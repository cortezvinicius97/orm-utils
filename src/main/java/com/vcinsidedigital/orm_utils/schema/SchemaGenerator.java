package com.vcinsidedigital.orm_utils.schema;

import com.vcinsidedigital.orm_utils.annotations.*;
import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;
import java.util.Date;

public class SchemaGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SchemaGenerator.class);
    private final Set<Class<?>> entities = new HashSet<>();
    private boolean autoDropColumns = true; // Controla se colunas devem ser removidas automaticamente

    public void addEntity(Class<?> entityClass) {
        if (!entityClass.isAnnotationPresent(Entity.class)) {
            throw new IllegalArgumentException("Class must be annotated with @Entity");
        }
        entities.add(entityClass);
    }

    /**
     * Define se colunas removidas das entidades devem ser automaticamente
     * deletadas do banco de dados.
     *
     * @param autoDropColumns true para remover automaticamente, false para apenas avisar
     * @return this SchemaGenerator para method chaining
     */
    public SchemaGenerator setAutoDropColumns(boolean autoDropColumns) {
        this.autoDropColumns = autoDropColumns;
        return this;
    }

    public void generateSchema() throws SQLException {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            DatabaseConfig.DatabaseType dbType = ConnectionPool.getInstance()
                    .getConfig().getType();

            logger.info("=== Starting Schema Generation/Update ===");
            logger.info("Auto-drop removed columns: {}", autoDropColumns);

            // Ordenar entidades por dependências
            List<Class<?>> orderedEntities = sortEntitiesByDependencies();

            // Criar/atualizar tabelas principais
            for (Class<?> entity : orderedEntities) {
                String tableName = getTableName(entity);

                if (tableExists(conn, tableName, dbType)) {
                    logger.info("Table '{}' exists, checking for updates...", tableName);
                    updateSchema(conn, entity, dbType);
                } else {
                    logger.info("Table '{}' does not exist, creating...", tableName);
                    String sql = generateCreateTableSQL(entity, dbType);
                    logger.debug("SQL: {}", sql);
                    conn.createStatement().execute(sql);
                    logger.info("✓ Table '{}' created successfully", tableName);
                }
            }

            // Criar tabelas de junção para ManyToMany
            for (Class<?> entity : orderedEntities) {
                createManyToManyTables(entity, dbType, conn);
            }

            logger.info("=== Schema Generation/Update Complete ===");
        }
    }

    /**
     * Ordena entidades por dependências usando ordenação topológica
     */
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

        logger.debug("Entities ordered by dependencies: {}",
                sorted.stream().map(Class::getSimpleName).toList());

        return sorted;
    }

    private void topologicalSort(Class<?> entity,
                                 Map<Class<?>, Set<Class<?>>> dependencies,
                                 Set<Class<?>> visited,
                                 Set<Class<?>> visiting,
                                 List<Class<?>> sorted) {
        if (visiting.contains(entity)) {
            logger.warn("Circular dependency detected involving: {}", entity.getSimpleName());
            return;
        }

        if (visited.contains(entity)) {
            return;
        }

        visiting.add(entity);

        for (Class<?> dependency : dependencies.get(entity)) {
            topologicalSort(dependency, dependencies, visited, visiting, sorted);
        }

        visiting.remove(entity);
        visited.add(entity);
        sorted.add(entity);
    }

    private void updateSchema(Connection conn, Class<?> entityClass,
                              DatabaseConfig.DatabaseType dbType) throws SQLException {
        String tableName = getTableName(entityClass);

        Map<String, ColumnInfo> existingColumns = getExistingColumns(conn, tableName, dbType);
        logger.debug("Existing columns in '{}': {}", tableName, existingColumns.keySet());

        Map<String, ColumnInfo> expectedColumns = getExpectedColumns(entityClass, dbType);
        logger.debug("Expected columns in '{}': {}", tableName, expectedColumns.keySet());

        List<String> alterStatements = generateAlterStatements(
                tableName, existingColumns, expectedColumns, dbType
        );

        if (!alterStatements.isEmpty()) {
            logger.info("Detected {} schema changes for table '{}'",
                    alterStatements.size(), tableName);

            for (String sql : alterStatements) {
                if (sql.startsWith("//")) {
                    logger.warn(sql.substring(3)); // Remove "//" e loga como warning
                    continue;
                }

                logger.info("Executing: {}", sql);
                try {
                    conn.createStatement().execute(sql);
                    logger.info("✓ Schema updated successfully");
                } catch (SQLException e) {
                    logger.error("✗ Failed to execute: {} - Error: {}", sql, e.getMessage());
                    throw e;
                }
            }
        } else {
            logger.info("No schema changes needed for table '{}'", tableName);
        }
    }

    private boolean tableExists(Connection conn, String tableName,
                                DatabaseConfig.DatabaseType dbType) throws SQLException {
        switch (dbType) {
            case MYSQL:
                String mysqlSql = "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(mysqlSql)) {
                    pstmt.setString(1, tableName);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        return rs.next() && rs.getInt(1) > 0;
                    }
                }

            case POSTGRESQL:
                String pgSql = "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(pgSql)) {
                    pstmt.setString(1, tableName);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        return rs.next() && rs.getInt(1) > 0;
                    }
                }
            case SQLITE:
            default:
                String sqliteSql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
                try (PreparedStatement pstmt = conn.prepareStatement(sqliteSql)) {
                    pstmt.setString(1, tableName);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        return rs.next();
                    }
                }
        }
    }

    private Map<String, ColumnInfo> getExistingColumns(Connection conn, String tableName,
                                                       DatabaseConfig.DatabaseType dbType)
            throws SQLException {
        Map<String, ColumnInfo> columns = new HashMap<>();

        switch (dbType) {
            case MYSQL:
                String mysqlSql = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ?";

                try (PreparedStatement pstmt = conn.prepareStatement(mysqlSql)) {
                    pstmt.setString(1, tableName);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            String columnName = rs.getString("COLUMN_NAME");
                            String columnType = rs.getString("COLUMN_TYPE");
                            boolean nullable = "YES".equals(rs.getString("IS_NULLABLE"));
                            String columnKey = rs.getString("COLUMN_KEY");
                            boolean isPrimaryKey = "PRI".equals(columnKey);
                            boolean unique = "UNI".equals(columnKey);

                            columns.put(columnName, new ColumnInfo(
                                    columnName, columnType, nullable, isPrimaryKey, unique
                            ));
                        }
                    }
                }
                break;

            case POSTGRESQL:
                String pgSql = "SELECT column_name, data_type, is_nullable, " +
                        "character_maximum_length, numeric_precision, numeric_scale " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = ?";

                try (PreparedStatement pstmt = conn.prepareStatement(pgSql)) {
                    pstmt.setString(1, tableName);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            String columnName = rs.getString("column_name");
                            String dataType = rs.getString("data_type");
                            Integer maxLength = (Integer) rs.getObject("character_maximum_length");

                            // Construir tipo completo
                            String fullType = dataType.toUpperCase();
                            if (maxLength != null && (fullType.contains("VARCHAR") || fullType.contains("CHAR"))) {
                                fullType = fullType + "(" + maxLength + ")";
                            }

                            boolean nullable = "YES".equals(rs.getString("is_nullable"));

                            // Verificar se é PK
                            boolean isPrimaryKey = isPrimaryKeyPostgres(conn, tableName, columnName);

                            columns.put(columnName, new ColumnInfo(
                                    columnName, fullType, nullable, isPrimaryKey, false
                            ));
                        }
                    }
                }
                break;
            case SQLITE:
            default:
                String sqliteSql = "PRAGMA table_info(" + tableName + ")";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sqliteSql)) {

                    while (rs.next()) {
                        String columnName = rs.getString("name");
                        String dataType = rs.getString("type");
                        boolean nullable = rs.getInt("notnull") == 0;
                        boolean isPrimaryKey = rs.getInt("pk") > 0;

                        columns.put(columnName, new ColumnInfo(
                                columnName, dataType, nullable, isPrimaryKey, false
                        ));
                    }
                }
                break;
        }

        return columns;
    }


    private boolean isPrimaryKeyPostgres(Connection conn, String tableName, String columnName)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "ON tc.constraint_name = kcu.constraint_name " +
                "WHERE tc.constraint_type = 'PRIMARY KEY' " +
                "AND tc.table_name = ? AND kcu.column_name = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            pstmt.setString(2, columnName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private Map<String, ColumnInfo> getExpectedColumns(Class<?> entityClass,
                                                       DatabaseConfig.DatabaseType dbType) {
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
                dataType = getColumnTypeForForeignKey(dbType);
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
                        dbType, isAutoIncrementPK);

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
                                                 Map<String, ColumnInfo> expected,
                                                 DatabaseConfig.DatabaseType dbType) {
        List<String> statements = new ArrayList<>();

        // 1. ADICIONAR novas colunas
        for (Map.Entry<String, ColumnInfo> entry : expected.entrySet()) {
            String columnName = entry.getKey();
            ColumnInfo expectedCol = entry.getValue();

            if (!existing.containsKey(columnName) && !expectedCol.isPrimaryKey) {
                String sql = generateAddColumnStatement(tableName, expectedCol, dbType);
                statements.add(sql);
                logger.debug("Will add column: {}", columnName);
            }
        }

        // 2. MODIFICAR colunas existentes (apenas MySQL)
        if (dbType == DatabaseConfig.DatabaseType.MYSQL) {
            for (Map.Entry<String, ColumnInfo> entry : expected.entrySet()) {
                String columnName = entry.getKey();
                ColumnInfo expectedCol = entry.getValue();

                if (existing.containsKey(columnName)) {
                    ColumnInfo existingCol = existing.get(columnName);

                    if (!expectedCol.isPrimaryKey && needsModification(existingCol, expectedCol)) {
                        String sql = generateModifyColumnStatement(tableName, expectedCol, dbType);
                        statements.add(sql);
                        logger.debug("Will modify column: {} from {} to {}",
                                columnName, existingCol, expectedCol);
                    }
                }
            }
        } else if (dbType == DatabaseConfig.DatabaseType.SQLITE) {
            for (Map.Entry<String, ColumnInfo> entry : expected.entrySet()) {
                String columnName = entry.getKey();
                ColumnInfo expectedCol = entry.getValue();

                if (existing.containsKey(columnName)) {
                    ColumnInfo existingCol = existing.get(columnName);

                    if (!expectedCol.isPrimaryKey && needsModification(existingCol, expectedCol)) {
                        logger.warn("⚠ Column '{}' needs modification but SQLite doesn't support ALTER COLUMN",
                                columnName);
                        logger.warn("   Current: {}", existingCol);
                        logger.warn("   Expected: {}", expectedCol);
                        logger.warn("   You'll need to create a migration to rebuild the table");
                    }
                }
            }
        }

        // 3. REMOVER colunas deletadas das entidades
        for (Map.Entry<String, ColumnInfo> entry : existing.entrySet()) {
            String columnName = entry.getKey();
            ColumnInfo existingCol = entry.getValue();

            if (!expected.containsKey(columnName)) {
                if (autoDropColumns) {
                    if (dbType == DatabaseConfig.DatabaseType.MYSQL) {
                        String sql = "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
                        statements.add(sql);
                        logger.info("⚠ Column '{}' removed from entity, will DROP from database", columnName);
                    } else {
                        logger.warn("⚠ Column '{}' removed from entity but SQLite doesn't support DROP COLUMN easily", columnName);
                        logger.warn("   You'll need to manually rebuild the table without this column");
                        statements.add("// WARNING: Column '" + columnName + "' should be removed but SQLite doesn't support DROP COLUMN");
                    }
                } else {
                    logger.warn("⚠ Column '{}' exists in database but not in entity class", columnName);
                    logger.warn("   This column will NOT be removed automatically (autoDropColumns=false)");
                    logger.warn("   To remove it, either set autoDropColumns(true) or run manually:");
                    logger.warn("   ALTER TABLE {} DROP COLUMN {}", tableName, columnName);
                }
            }
        }

        return statements;
    }

    private boolean needsModification(ColumnInfo existing, ColumnInfo expected) {
        String existingType = normalizeType(existing.dataType);
        String expectedType = normalizeType(expected.dataType);

        String existingBaseType = existingType.replaceAll("\\(.*\\)", "").trim();
        String expectedBaseType = expectedType.replaceAll("\\(.*\\)", "").trim();

        if (!existingBaseType.equals(expectedBaseType)) {
            logger.debug("Type base mismatch: '{}' vs '{}'", existingBaseType, expectedBaseType);
            return true;
        }

        if (existing.dataType.toUpperCase().startsWith("VARCHAR") ||
                existing.dataType.toUpperCase().startsWith("CHAR")) {
            if (!existing.dataType.equalsIgnoreCase(expected.dataType)) {
                logger.debug("Type length mismatch: '{}' vs '{}'",
                        existing.dataType, expected.dataType);
                return true;
            }
        }

        if (existing.nullable != expected.nullable) {
            logger.debug("Nullable mismatch: {} vs {}", existing.nullable, expected.nullable);
            return true;
        }

        if (existing.unique != expected.unique) {
            logger.debug("Unique mismatch: {} vs {}", existing.unique, expected.unique);
            return true;
        }

        return false;
    }

    private String generateAddColumnStatement(String tableName, ColumnInfo column,
                                              DatabaseConfig.DatabaseType dbType) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(tableName);
        sql.append(" ADD COLUMN ").append(column.name);
        sql.append(" ").append(column.dataType);

        if (!column.nullable) {
            sql.append(" NOT NULL");
        }

        if (column.unique) {
            sql.append(" UNIQUE");
        }

        return sql.toString();
    }

    private String generateModifyColumnStatement(String tableName, ColumnInfo column,
                                                 DatabaseConfig.DatabaseType dbType) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(tableName);

        switch (dbType) {
            case MYSQL:
                sql.append(" MODIFY COLUMN ");
                break;
            case POSTGRESQL:
                sql.append(" ALTER COLUMN ").append(column.name)
                        .append(" TYPE ");
                // PostgreSQL precisa de comandos separados para NOT NULL
                return sql.append(column.dataType).toString();
            case SQLITE:
                return "// SQLite doesn't support ALTER COLUMN";
        }

        sql.append(column.name).append(" ").append(column.dataType);

        if (!column.nullable && dbType != DatabaseConfig.DatabaseType.POSTGRESQL) {
            sql.append(" NOT NULL");
        }

        if (column.unique && dbType != DatabaseConfig.DatabaseType.POSTGRESQL) {
            sql.append(" UNIQUE");
        }

        return sql.toString();
    }

    private String normalizeType(String type) {
        return type.toUpperCase()
                .replaceAll("\\(\\d+(,\\d+)?\\)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String generateCreateTableSQL(Class<?> entityClass,
                                          DatabaseConfig.DatabaseType dbType) {
        Entity entityAnnotation = entityClass.getAnnotation(Entity.class);
        String tableName = entityAnnotation.table().isEmpty()
                ? toSnakeCase(entityClass.getSimpleName())
                : entityAnnotation.table();

        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(tableName).append(" (");

        List<String> columns = new ArrayList<>();
        List<String> foreignKeys = new ArrayList<>();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(OneToMany.class) ||
                    field.isAnnotationPresent(ManyToMany.class)) {
                continue;
            }

            String columnDef = generateColumnDefinition(field, dbType);
            if (columnDef != null) {
                columns.add(columnDef);
            }

            if (field.isAnnotationPresent(ManyToOne.class)) {
                String fk = generateForeignKey(field, tableName, dbType);
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

    private String generateColumnDefinition(Field field,
                                            DatabaseConfig.DatabaseType dbType) {
        if (field.isAnnotationPresent(ManyToOne.class)) {
            JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
            if (joinCol == null) {
                return null;
            }
            String colName = joinCol.name();
            String nullable = joinCol.nullable() ? "" : " NOT NULL";
            return colName + " " + getColumnTypeForForeignKey(dbType) + nullable;
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
                column != null ? column.length() : 255, dbType, isAutoIncrementPK);
        StringBuilder def = new StringBuilder(columnName).append(" ").append(type);

        if (field.isAnnotationPresent(Id.class)) {
            Id id = field.getAnnotation(Id.class);
            def.append(" PRIMARY KEY");

            if (id.autoIncrement() && isNumericType(field.getType())) {
                switch (dbType) {
                    case MYSQL:
                        def.append(" AUTO_INCREMENT");
                        break;
                    case POSTGRESQL:
                        // PostgreSQL usa SERIAL ou BIGSERIAL
                        // Reconstruir a definição
                        return columnName + " " +
                                (field.getType() == Long.class || field.getType() == long.class ?
                                        "BIGSERIAL" : "SERIAL") + " PRIMARY KEY";
                    case SQLITE:
                        def.append(" AUTOINCREMENT");
                        break;
                }
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

    private String getColumnType(Class<?> javaType, int length,
                                 DatabaseConfig.DatabaseType dbType,
                                 boolean isAutoIncrementPK) {
        if (javaType == java.time.LocalDateTime.class) {
            return switch (dbType) {
                case MYSQL -> "DATETIME";
                case POSTGRESQL -> "TIMESTAMP";
                case SQLITE -> "TEXT";
                default -> "TEXT";
            };
        } else if (javaType == java.time.LocalDate.class) {
            return switch (dbType) {
                case MYSQL, POSTGRESQL -> "DATE";
                case SQLITE -> "TEXT";
                default -> "TEXT";
            };
        } else if (javaType == java.time.LocalTime.class) {
            return switch (dbType) {
                case MYSQL, POSTGRESQL -> "TIME";
                case SQLITE -> "TEXT";
                default -> "TEXT";
            };
        } else if (javaType == Long.class || javaType == long.class) {
            if (dbType == DatabaseConfig.DatabaseType.SQLITE && isAutoIncrementPK) {
                return "INTEGER";
            }
            return switch (dbType) {
                case SQLITE -> "INTEGER";
                case MYSQL, POSTGRESQL -> "BIGINT";
                default -> "INTEGER";
            };
        } else if (javaType == Integer.class || javaType == int.class) {
            return switch (dbType) {
                case SQLITE -> "INTEGER";
                case MYSQL, POSTGRESQL -> "INT";
                default -> "INTEGER";
            };
        } else if (javaType == String.class) {
            return switch (dbType) {
                case MYSQL, POSTGRESQL -> "VARCHAR(" + length + ")";
                case SQLITE -> "TEXT";
                default -> "TEXT";
            };
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return switch (dbType) {
                case MYSQL -> "TINYINT(1)";
                case POSTGRESQL -> "BOOLEAN";
                case SQLITE -> "INTEGER";
                default -> "INTEGER";
            };
        } else if (javaType == Double.class || javaType == double.class) {
            return switch (dbType) {
                case MYSQL -> "DOUBLE";
                case POSTGRESQL -> "DOUBLE PRECISION";
                case SQLITE -> "REAL";
                default -> "REAL";
            };
        } else if (javaType == Float.class || javaType == float.class) {
            return switch (dbType) {
                case MYSQL, POSTGRESQL -> "FLOAT";
                case SQLITE -> "REAL";
                default -> "REAL";
            };
        } else if (javaType == java.util.Date.class ||
                javaType == java.sql.Date.class ||
                javaType == java.sql.Timestamp.class) {
            return switch (dbType) {
                case MYSQL -> "DATETIME";
                case POSTGRESQL -> "TIMESTAMP";
                case SQLITE -> "TEXT";
                default -> "TEXT";
            };
        }
        return "TEXT";
    }

    private String getColumnTypeForForeignKey(DatabaseConfig.DatabaseType dbType) {
        return switch (dbType) {
            case SQLITE -> "INTEGER";
            case MYSQL, POSTGRESQL -> "BIGINT";
            default -> "INTEGER";
        };
    }

    private String generateForeignKey(Field field, String tableName,
                                      DatabaseConfig.DatabaseType dbType) {
        JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
        if (joinCol == null) return null;

        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        Class<?> targetEntity = manyToOne.targetEntity();
        if (targetEntity == void.class) {
            targetEntity = field.getType();
        }

        String targetTable = getTableName(targetEntity);
        String fkName = "fk_" + tableName + "_" + joinCol.name();

        return String.format("CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s(%s)",
                fkName, joinCol.name(), targetTable, joinCol.referencedColumnName());
    }

    private void createManyToManyTables(Class<?> entityClass,
                                        DatabaseConfig.DatabaseType dbType,
                                        Connection conn) throws SQLException {
        for (Field field : entityClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(ManyToMany.class)) continue;

            ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
            String joinTable = manyToMany.joinTable();

            if (joinTable.isEmpty()) {
                String table1 = getTableName(entityClass);
                String table2 = getTableName(manyToMany.targetEntity());
                joinTable = table1 + "_" + table2;
            }

            String col1 = manyToMany.joinColumn().isEmpty()
                    ? toSnakeCase(entityClass.getSimpleName()) + "_id"
                    : manyToMany.joinColumn();

            String col2 = manyToMany.inverseJoinColumn().isEmpty()
                    ? toSnakeCase(manyToMany.targetEntity().getSimpleName()) + "_id"
                    : manyToMany.inverseJoinColumn();

            String columnType = dbType == DatabaseConfig.DatabaseType.SQLITE ? "INTEGER" : "BIGINT";

            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s (%s %s, %s %s, " +
                            "PRIMARY KEY (%s, %s))",
                    joinTable, col1, columnType, col2, columnType, col1, col2
            );

            logger.info("Creating join table: {}", joinTable);
            conn.createStatement().execute(sql);
        }
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