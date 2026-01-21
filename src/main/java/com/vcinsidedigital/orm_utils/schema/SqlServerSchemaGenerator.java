package com.vcinsidedigital.orm_utils.schema;

import com.vcinsidedigital.orm_utils.annotations.*;
import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

/**
 * SchemaGenerator específico para SQL Server
 * Trata das particularidades do T-SQL
 */
public class SqlServerSchemaGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SqlServerSchemaGenerator.class);
    private final Set<Class<?>> entities = new HashSet<>();
    private boolean autoDropColumns = true;

    public void addEntity(Class<?> entityClass) {
        if (!entityClass.isAnnotationPresent(Entity.class)) {
            throw new IllegalArgumentException("Class must be annotated with @Entity");
        }
        entities.add(entityClass);
    }

    public SqlServerSchemaGenerator setAutoDropColumns(boolean autoDropColumns) {
        this.autoDropColumns = autoDropColumns;
        return this;
    }

    public void generateSchema() throws SQLException {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            logger.info("=== Starting SQL Server Schema Generation ===");
            logger.info("Auto-drop removed columns: {}", autoDropColumns);

            List<Class<?>> orderedEntities = sortEntitiesByDependencies();

            for (Class<?> entity : orderedEntities) {
                String tableName = getTableName(entity);

                if (tableExists(conn, tableName)) {
                    logger.info("Table '{}' exists, checking for updates...", tableName);
                    updateSchema(conn, entity);
                } else {
                    logger.info("Table '{}' does not exist, creating...", tableName);
                    String sql = generateCreateTableSQL(entity);
                    logger.debug("SQL: {}", sql);
                    conn.createStatement().execute(sql);
                    logger.info("✓ Table '{}' created successfully", tableName);
                }
            }

            for (Class<?> entity : orderedEntities) {
                createManyToManyTables(entity, conn);
            }

            logger.info("=== SQL Server Schema Generation Complete ===");
        }
    }

    private List<Class<?>> sortEntitiesByDependencies() {
        Map<Class<?>, Set<Class<?>>> dependencies = new HashMap<>();

        for (Class<?> entity : entities) {
            Set<Class<?>> deps = new HashSet<>();
            for (Field field : entity.getDeclaredFields()) {
                if (field.isAnnotationPresent(ManyToOne.class)) {
                    ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
                    Class<?> targetEntity = manyToOne.targetEntity();
                    if (targetEntity == void.class) {
                        targetEntity = field.getType();
                    }
                    if (entities.contains(targetEntity)) {
                        deps.add(targetEntity);
                    }
                }
            }
            dependencies.put(entity, deps);
        }

        List<Class<?>> sorted = new ArrayList<>();
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> visiting = new HashSet<>();

        for (Class<?> entity : entities) {
            if (!visited.contains(entity)) {
                topologicalSort(entity, dependencies, visited, visiting, sorted);
            }
        }

        return sorted;
    }

    private void topologicalSort(Class<?> entity, Map<Class<?>, Set<Class<?>>> deps,
                                 Set<Class<?>> visited, Set<Class<?>> visiting,
                                 List<Class<?>> sorted) {
        if (visiting.contains(entity)) {
            logger.warn("Circular dependency: {}", entity.getSimpleName());
            return;
        }
        if (visited.contains(entity)) return;

        visiting.add(entity);
        for (Class<?> dep : deps.get(entity)) {
            topologicalSort(dep, deps, visited, visiting, sorted);
        }
        visiting.remove(entity);
        visited.add(entity);
        sorted.add(entity);
    }

    private void updateSchema(Connection conn, Class<?> entityClass) throws SQLException {
        String tableName = getTableName(entityClass);
        Map<String, ColumnInfo> existing = getExistingColumns(conn, tableName);
        Map<String, ColumnInfo> expected = getExpectedColumns(entityClass);

        List<String> alterStatements = generateAlterStatements(tableName, existing, expected);

        if (!alterStatements.isEmpty()) {
            logger.info("Detected {} schema changes for '{}'", alterStatements.size(), tableName);
            for (String sql : alterStatements) {
                if (sql.startsWith("//")) {
                    logger.warn(sql.substring(3));
                    continue;
                }
                logger.info("Executing: {}", sql);
                try {
                    conn.createStatement().execute(sql);
                    logger.info("✓ Schema updated");
                } catch (SQLException e) {
                    logger.error("✗ Failed: {} - {}", sql, e.getMessage());
                    throw e;
                }
            }
        } else {
            logger.info("No schema changes for '{}'", tableName);
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_NAME = ? AND TABLE_SCHEMA = 'dbo'";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private Map<String, ColumnInfo> getExistingColumns(Connection conn, String tableName)
            throws SQLException {
        Map<String, ColumnInfo> columns = new HashMap<>();

        String sql = "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.CHARACTER_MAXIMUM_LENGTH, " +
                "c.NUMERIC_PRECISION, c.NUMERIC_SCALE, c.IS_NULLABLE, " +
                "CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 1 ELSE 0 END AS IS_PRIMARY_KEY " +
                "FROM INFORMATION_SCHEMA.COLUMNS c " +
                "LEFT JOIN ( " +
                "  SELECT ku.COLUMN_NAME " +
                "  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                "  JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku " +
                "    ON tc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME " +
                "  WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY' " +
                "    AND tc.TABLE_NAME = ? " +
                ") pk ON c.COLUMN_NAME = pk.COLUMN_NAME " +
                "WHERE c.TABLE_NAME = ? AND c.TABLE_SCHEMA = 'dbo'";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            pstmt.setString(2, tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE").toUpperCase();
                    Integer maxLength = (Integer) rs.getObject("CHARACTER_MAXIMUM_LENGTH");
                    boolean nullable = "YES".equals(rs.getString("IS_NULLABLE"));
                    boolean isPrimaryKey = rs.getInt("IS_PRIMARY_KEY") == 1;

                    String fullType = dataType;
                    if (maxLength != null && (dataType.contains("VARCHAR") || dataType.contains("CHAR"))) {
                        fullType = dataType + "(" + (maxLength == -1 ? "MAX" : maxLength) + ")";
                    }

                    columns.put(columnName, new ColumnInfo(
                            columnName, fullType, nullable, isPrimaryKey, false
                    ));
                }
            }
        }

        return columns;
    }

    private Map<String, ColumnInfo> getExpectedColumns(Class<?> entityClass) {
        Map<String, ColumnInfo> columns = new HashMap<>();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(OneToMany.class) ||
                    field.isAnnotationPresent(ManyToMany.class)) {
                continue;
            }

            String columnName;
            String dataType;
            boolean nullable = true;
            boolean unique = false;
            boolean isPrimaryKey = false;

            if (field.isAnnotationPresent(ManyToOne.class)) {
                JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
                if (joinCol == null) continue;

                columnName = joinCol.name();
                dataType = "BIGINT";
                nullable = joinCol.nullable();
            } else {
                Column column = field.getAnnotation(Column.class);
                columnName = (column != null && !column.name().isEmpty())
                        ? column.name()
                        : toSnakeCase(field.getName());

                isPrimaryKey = field.isAnnotationPresent(Id.class);
                boolean isAutoIncrementPK = isPrimaryKey &&
                        field.getAnnotation(Id.class).autoIncrement();

                dataType = getColumnType(field.getType(),
                        column != null ? column.length() : 255,
                        isAutoIncrementPK);

                if (column != null) {
                    nullable = column.nullable() && !isPrimaryKey;
                    unique = column.unique();
                }
            }

            columns.put(columnName, new ColumnInfo(
                    columnName, dataType, nullable, isPrimaryKey, unique
            ));
        }

        return columns;
    }

    private List<String> generateAlterStatements(String tableName,
                                                 Map<String, ColumnInfo> existing,
                                                 Map<String, ColumnInfo> expected) {
        List<String> statements = new ArrayList<>();

        // Adicionar novas colunas
        for (Map.Entry<String, ColumnInfo> entry : expected.entrySet()) {
            String columnName = entry.getKey();
            ColumnInfo expectedCol = entry.getValue();

            if (!existing.containsKey(columnName) && !expectedCol.isPrimaryKey) {
                String sql = generateAddColumnStatement(tableName, expectedCol);
                statements.add(sql);
            }
        }

        // Modificar colunas existentes
        for (Map.Entry<String, ColumnInfo> entry : expected.entrySet()) {
            String columnName = entry.getKey();
            ColumnInfo expectedCol = entry.getValue();

            if (existing.containsKey(columnName)) {
                ColumnInfo existingCol = existing.get(columnName);
                if (!expectedCol.isPrimaryKey && needsModification(existingCol, expectedCol)) {
                    String sql = generateAlterColumnStatement(tableName, expectedCol);
                    statements.add(sql);
                }
            }
        }

        // Remover colunas
        for (String columnName : existing.keySet()) {
            if (!expected.containsKey(columnName)) {
                if (autoDropColumns) {
                    // SQL Server precisa dropar constraints primeiro
                    statements.add(generateDropColumnStatement(tableName, columnName));
                } else {
                    logger.warn("⚠ Column '{}' exists in DB but not in entity", columnName);
                }
            }
        }

        return statements;
    }

    private boolean needsModification(ColumnInfo existing, ColumnInfo expected) {
        String existingType = existing.dataType.replaceAll("\\(.*\\)", "").trim();
        String expectedType = expected.dataType.replaceAll("\\(.*\\)", "").trim();

        if (!existingType.equals(expectedType)) return true;
        if (!existing.dataType.equalsIgnoreCase(expected.dataType)) return true;
        if (existing.nullable != expected.nullable) return true;
        return existing.unique != expected.unique;
    }

    private String generateAddColumnStatement(String tableName, ColumnInfo column) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(tableName);
        sql.append(" ADD ").append(column.name);
        sql.append(" ").append(column.dataType);

        if (!column.nullable) {
            sql.append(" NOT NULL");
        }

        if (column.unique) {
            sql.append(" UNIQUE");
        }

        return sql.toString();
    }

    private String generateAlterColumnStatement(String tableName, ColumnInfo column) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(tableName);
        sql.append(" ALTER COLUMN ").append(column.name);
        sql.append(" ").append(column.dataType);

        if (!column.nullable) {
            sql.append(" NOT NULL");
        }

        return sql.toString();
    }

    private String generateDropColumnStatement(String tableName, String columnName) {
        // SQL Server requer dropar constraints antes de dropar a coluna
        return String.format(
                "DECLARE @ConstraintName nvarchar(200); " +
                        "SELECT @ConstraintName = Name FROM SYS.DEFAULT_CONSTRAINTS " +
                        "WHERE PARENT_OBJECT_ID = OBJECT_ID('%s') " +
                        "AND PARENT_COLUMN_ID = (SELECT column_id FROM sys.columns " +
                        "WHERE NAME = '%s' AND object_id = OBJECT_ID('%s')); " +
                        "IF @ConstraintName IS NOT NULL " +
                        "EXEC('ALTER TABLE %s DROP CONSTRAINT ' + @ConstraintName); " +
                        "ALTER TABLE %s DROP COLUMN %s",
                tableName, columnName, tableName, tableName, tableName, columnName
        );
    }

    private String generateCreateTableSQL(Class<?> entityClass) {
        Entity entityAnnotation = entityClass.getAnnotation(Entity.class);
        String tableName = entityAnnotation.table().isEmpty()
                ? toSnakeCase(entityClass.getSimpleName())
                : entityAnnotation.table();

        StringBuilder sql = new StringBuilder("CREATE TABLE ")
                .append(tableName).append(" (");

        List<String> columns = new ArrayList<>();
        List<String> foreignKeys = new ArrayList<>();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(OneToMany.class) ||
                    field.isAnnotationPresent(ManyToMany.class)) {
                continue;
            }

            String columnDef = generateColumnDefinition(field);
            if (columnDef != null) {
                columns.add(columnDef);
            }

            if (field.isAnnotationPresent(ManyToOne.class)) {
                String fk = generateForeignKey(field, tableName);
                if (fk != null) {
                    foreignKeys.add(fk);
                }
            }
        }

        sql.append(String.join(", ", columns));

        if (!foreignKeys.isEmpty()) {
            sql.append(", ").append(String.join(", ", foreignKeys));
        }

        sql.append(")");

        return sql.toString();
    }

    private String generateColumnDefinition(Field field) {
        if (field.isAnnotationPresent(ManyToOne.class)) {
            JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
            if (joinCol == null) return null;

            String colName = joinCol.name();
            String nullable = joinCol.nullable() ? "" : " NOT NULL";
            return colName + " BIGINT" + nullable;
        }

        Column column = field.getAnnotation(Column.class);
        String columnName = (column != null && !column.name().isEmpty())
                ? column.name()
                : toSnakeCase(field.getName());

        if (column != null && !column.columnDefinition().isEmpty()) {
            return columnName + " " + column.columnDefinition();
        }

        boolean isAutoIncrementPK = field.isAnnotationPresent(Id.class) &&
                field.getAnnotation(Id.class).autoIncrement();

        String type = getColumnType(field.getType(),
                column != null ? column.length() : 255,
                isAutoIncrementPK);

        StringBuilder def = new StringBuilder(columnName).append(" ").append(type);

        if (field.isAnnotationPresent(Id.class)) {
            def.append(" PRIMARY KEY");
            if (isAutoIncrementPK && isNumericType(field.getType())) {
                def.append(" IDENTITY(1,1)");
            }
        }

        if (column != null) {
            if (!column.nullable() && !field.isAnnotationPresent(Id.class)) {
                def.append(" NOT NULL");
            }
            if (column.unique()) {
                def.append(" UNIQUE");
            }
        }

        return def.toString();
    }

    private String generateForeignKey(Field field, String tableName) {
        JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
        if (joinCol == null) return null;

        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        Class<?> targetEntity = manyToOne.targetEntity();
        if (targetEntity == void.class) {
            targetEntity = field.getType();
        }

        String targetTable = getTableName(targetEntity);
        String fkName = "FK_" + tableName + "_" + joinCol.name();

        return String.format("CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s(%s)",
                fkName, joinCol.name(), targetTable, joinCol.referencedColumnName());
    }

    private void createManyToManyTables(Class<?> entityClass, Connection conn)
            throws SQLException {
        for (Field field : entityClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(ManyToMany.class)) continue;

            ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
            String joinTable = manyToMany.joinTable();

            if (joinTable.isEmpty()) {
                String table1 = getTableName(entityClass);
                String table2 = getTableName(manyToMany.targetEntity());
                joinTable = table1 + "_" + table2;
            }

            if (!tableExists(conn, joinTable)) {
                String col1 = manyToMany.joinColumn().isEmpty()
                        ? toSnakeCase(entityClass.getSimpleName()) + "_id"
                        : manyToMany.joinColumn();

                String col2 = manyToMany.inverseJoinColumn().isEmpty()
                        ? toSnakeCase(manyToMany.targetEntity().getSimpleName()) + "_id"
                        : manyToMany.inverseJoinColumn();

                String sql = String.format(
                        "CREATE TABLE %s (%s BIGINT, %s BIGINT, PRIMARY KEY (%s, %s))",
                        joinTable, col1, col2, col1, col2
                );

                logger.info("Creating join table: {}", joinTable);
                conn.createStatement().execute(sql);
            }
        }
    }

    private String getColumnType(Class<?> javaType, int length, boolean isAutoIncrementPK) {
        if (javaType == java.time.LocalDateTime.class || javaType == Timestamp.class) {
            return "DATETIME2";
        } else if (javaType == java.time.LocalDate.class || javaType == java.sql.Date.class) {
            return "DATE";
        } else if (javaType == java.time.LocalTime.class || javaType == Time.class) {
            return "TIME";
        } else if (javaType == Long.class || javaType == long.class) {
            return "BIGINT";
        } else if (javaType == Integer.class || javaType == int.class) {
            return "INT";
        } else if (javaType == String.class) {
            return length > 4000 ? "NVARCHAR(MAX)" : "NVARCHAR(" + length + ")";
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return "BIT";
        } else if (javaType == Double.class || javaType == double.class) {
            return "FLOAT";
        } else if (javaType == Float.class || javaType == float.class) {
            return "REAL";
        } else if (javaType == java.util.Date.class) {
            return "DATETIME2";
        }
        return "NVARCHAR(255)";
    }

    private String getTableName(Class<?> entityClass) {
        Entity entity = entityClass.getAnnotation(Entity.class);
        return entity.table().isEmpty()
                ? toSnakeCase(entityClass.getSimpleName())
                : entity.table();
    }

    private String toSnakeCase(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private boolean isNumericType(Class<?> type) {
        return type == Long.class || type == long.class ||
                type == Integer.class || type == int.class ||
                type == Short.class || type == short.class ||
                type == Byte.class || type == byte.class;
    }

    private static class ColumnInfo {
        final String name;
        final String dataType;
        final boolean nullable;
        final boolean isPrimaryKey;
        final boolean unique;

        ColumnInfo(String name, String dataType, boolean nullable,
                   boolean isPrimaryKey, boolean unique) {
            this.name = name;
            this.dataType = dataType;
            this.nullable = nullable;
            this.isPrimaryKey = isPrimaryKey;
            this.unique = unique;
        }

        @Override
        public String toString() {
            List<String> attrs = new ArrayList<>();
            attrs.add(dataType);
            if (!nullable) attrs.add("NOT NULL");
            if (isPrimaryKey) attrs.add("PK");
            if (unique) attrs.add("UNIQUE");
            return String.join(" ", attrs);
        }
    }
}