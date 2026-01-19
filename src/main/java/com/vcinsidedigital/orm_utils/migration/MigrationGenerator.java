package com.vcinsidedigital.orm_utils.migration;

import com.vcinsidedigital.orm_utils.annotations.*;
import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MigrationGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MigrationGenerator.class);
    private static final String MIGRATIONS_DIR = "src/main/java/migrations";
    private static final String PACKAGE_NAME = "migrations";

    private final Set<Class<?>> entities = new HashSet<>();
    private final DatabaseConfig.DatabaseType dbType;

    public MigrationGenerator(DatabaseConfig.DatabaseType dbType) {
        this.dbType = dbType;
    }

    public void addEntity(Class<?> entityClass) {
        entities.add(entityClass);
    }

    public void generateMigrations() throws Exception {
        logger.info("=== Starting Migration Generation ===");

        createMigrationsDirectory();

        String version = generateVersionNumber();
        String className = "Version" + version;

        logger.info("Generating migration: {}", className);

        List<String> upStatements = new ArrayList<>();
        List<String> downStatements = new ArrayList<>();

        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            // Ordenar entidades por dependências
            List<Class<?>> orderedEntities = sortEntitiesByDependencies();

            // Processar entidades na ordem correta
            for (Class<?> entity : orderedEntities) {
                String tableName = getTableName(entity);

                if (tableExists(conn, tableName)) {
                    generateAlterStatements(conn, entity, upStatements, downStatements);
                } else {
                    generateCreateStatements(entity, upStatements, downStatements);
                }
            }

            // Gerar tabelas de junção para ManyToMany (sempre por último)
            for (Class<?> entity : orderedEntities) {
                generateManyToManyStatements(conn, entity, upStatements, downStatements);
            }
        }

        if (upStatements.isEmpty()) {
            logger.info("No schema changes detected. No migration generated.");
            return;
        }

        String migrationContent = generateMigrationFile(className, version, upStatements, downStatements);
        saveMigrationFile(className, migrationContent);

        logger.info("=== Migration {} Generated Successfully ===", className);
        logger.info("Location: {}/{}.java", MIGRATIONS_DIR, className);
        logger.info("Total operations: {} UP, {} DOWN", upStatements.size(), downStatements.size());
    }

    /**
     * Ordena entidades por dependências: entidades sem foreign keys primeiro,
     * depois as que dependem delas, e assim por diante.
     */
    private List<Class<?>> sortEntitiesByDependencies() {
        Map<Class<?>, Set<Class<?>>> dependencies = new HashMap<>();

        // Mapear dependências de cada entidade
        for (Class<?> entity : entities) {
            Set<Class<?>> deps = new HashSet<>();

            for (Field field : entity.getDeclaredFields()) {
                if (field.isAnnotationPresent(ManyToOne.class)) {
                    ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
                    Class<?> targetEntity = manyToOne.targetEntity();
                    if (targetEntity == void.class) {
                        targetEntity = field.getType();
                    }

                    // Só adiciona como dependência se for uma entidade registrada
                    if (entities.contains(targetEntity)) {
                        deps.add(targetEntity);
                    }
                }
            }

            dependencies.put(entity, deps);
        }

        // Ordenação topológica
        List<Class<?>> sorted = new ArrayList<>();
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> visiting = new HashSet<>();

        for (Class<?> entity : entities) {
            if (!visited.contains(entity)) {
                topologicalSort(entity, dependencies, visited, visiting, sorted);
            }
        }

        logger.info("Entities ordered by dependencies: {}",
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

        // Visitar dependências primeiro
        for (Class<?> dependency : dependencies.get(entity)) {
            topologicalSort(dependency, dependencies, visited, visiting, sorted);
        }

        visiting.remove(entity);
        visited.add(entity);
        sorted.add(entity);
    }

    private void createMigrationsDirectory() throws IOException {
        Path migrationsPath = Paths.get(MIGRATIONS_DIR);
        if (!Files.exists(migrationsPath)) {
            Files.createDirectories(migrationsPath);
            logger.info("Created migrations directory: {}", MIGRATIONS_DIR);
        }
    }

    private String generateVersionNumber() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return LocalDateTime.now().format(formatter);
    }

    private void generateCreateStatements(Class<?> entity,
                                          List<String> upStatements,
                                          List<String> downStatements) {
        String createSQL = generateCreateTableSQL(entity);
        upStatements.add(createSQL);

        String tableName = getTableName(entity);
        String dropSQL = "DROP TABLE IF EXISTS " + tableName;
        downStatements.add(0, dropSQL);
    }

    private void generateAlterStatements(Connection conn, Class<?> entity,
                                         List<String> upStatements,
                                         List<String> downStatements) throws SQLException {
        String tableName = getTableName(entity);

        Map<String, ColumnInfo> existingColumns = getExistingColumns(conn, tableName);
        Map<String, ColumnInfo> expectedColumns = getExpectedColumns(entity);

        // Novas colunas
        for (Map.Entry<String, ColumnInfo> entry : expectedColumns.entrySet()) {
            String columnName = entry.getKey();
            ColumnInfo expectedCol = entry.getValue();

            if (!existingColumns.containsKey(columnName) && !expectedCol.isPrimaryKey) {
                String addSQL = generateAddColumnSQL(tableName, expectedCol);
                upStatements.add(addSQL);

                String dropSQL = generateDropColumnSQL(tableName, columnName);
                downStatements.add(0, dropSQL);
            }
        }

        // Modificações (apenas MySQL)
        if (dbType == DatabaseConfig.DatabaseType.MYSQL) {
            for (Map.Entry<String, ColumnInfo> entry : expectedColumns.entrySet()) {
                String columnName = entry.getKey();
                ColumnInfo expectedCol = entry.getValue();

                if (existingColumns.containsKey(columnName)) {
                    ColumnInfo existingCol = existingColumns.get(columnName);

                    if (!expectedCol.isPrimaryKey && needsModification(existingCol, expectedCol)) {
                        String modifySQL = generateModifyColumnSQL(tableName, expectedCol);
                        upStatements.add(modifySQL);

                        String revertSQL = generateModifyColumnSQL(tableName, existingCol);
                        downStatements.add(0, revertSQL);
                    }
                }
            }
        }

        // Colunas removidas - REMOVER AUTOMATICAMENTE
        for (Map.Entry<String, ColumnInfo> entry : existingColumns.entrySet()) {
            String columnName = entry.getKey();

            if (!expectedColumns.containsKey(columnName)) {
                logger.info("Column '{}' was removed from entity, will drop from table '{}'",
                        columnName, tableName);

                if (dbType == DatabaseConfig.DatabaseType.MYSQL) {
                    String dropSQL = "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
                    upStatements.add(dropSQL);

                    // Para o DOWN, recriar a coluna com o tipo original
                    String addBackSQL = generateAddColumnSQL(tableName, entry.getValue());
                    downStatements.add(0, addBackSQL);
                } else {
                    // SQLite: avisar que não suporta DROP COLUMN facilmente
                    upStatements.add("// WARNING: Column '" + columnName + "' should be removed but SQLite doesn't support DROP COLUMN");
                    upStatements.add("// You need to manually recreate the table without this column");
                }
            }
        }
    }

    private void generateManyToManyStatements(Connection conn, Class<?> entity,
                                              List<String> upStatements,
                                              List<String> downStatements) throws SQLException {
        for (Field field : entity.getDeclaredFields()) {
            if (!field.isAnnotationPresent(ManyToMany.class)) continue;

            ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
            String joinTable = manyToMany.joinTable();

            if (joinTable.isEmpty()) {
                String table1 = getTableName(entity);
                String table2 = getTableName(manyToMany.targetEntity());
                joinTable = table1 + "_" + table2;
            }

            if (!tableExists(conn, joinTable)) {
                String createSQL = generateManyToManyTableSQL(entity, field);
                upStatements.add(createSQL);

                String dropSQL = "DROP TABLE IF EXISTS " + joinTable;
                downStatements.add(0, dropSQL);
            }
        }
    }

    private String generateCreateTableSQL(Class<?> entityClass) {
        Entity entityAnnotation = entityClass.getAnnotation(Entity.class);
        String tableName = entityAnnotation.table().isEmpty()
                ? toSnakeCase(entityClass.getSimpleName())
                : entityAnnotation.table();

        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(tableName).append(" (\n");

        List<String> columns = new ArrayList<>();
        List<String> foreignKeys = new ArrayList<>();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(OneToMany.class) ||
                    field.isAnnotationPresent(ManyToMany.class)) {
                continue;
            }

            String columnDef = generateColumnDefinition(field);
            if (columnDef != null) {
                columns.add("    " + columnDef);
            }

            if (field.isAnnotationPresent(ManyToOne.class)) {
                String fk = generateForeignKey(field, tableName);
                if (fk != null) {
                    foreignKeys.add("    " + fk);
                }
            }
        }

        sql.append(String.join(",\n", columns));

        if (!foreignKeys.isEmpty()) {
            sql.append(",\n").append(String.join(",\n", foreignKeys));
        }

        sql.append("\n)");

        return sql.toString();
    }

    private String generateManyToManyTableSQL(Class<?> entityClass, Field field) {
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

        return String.format(
                "CREATE TABLE IF NOT EXISTS %s (\n" +
                        "    %s %s,\n" +
                        "    %s %s,\n" +
                        "    PRIMARY KEY (%s, %s)\n" +
                        ")",
                joinTable, col1, columnType, col2, columnType, col1, col2
        );
    }

    private String generateAddColumnSQL(String tableName, ColumnInfo column) {
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

    private String generateDropColumnSQL(String tableName, String columnName) {
        if (dbType == DatabaseConfig.DatabaseType.SQLITE) {
            return "// SQLite doesn't support DROP COLUMN easily - manual migration needed";
        }
        return "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
    }

    private String generateModifyColumnSQL(String tableName, ColumnInfo column) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(tableName);
        sql.append(" MODIFY COLUMN ").append(column.name);
        sql.append(" ").append(column.dataType);

        if (!column.nullable) {
            sql.append(" NOT NULL");
        }

        if (column.unique) {
            sql.append(" UNIQUE");
        }

        return sql.toString();
    }

    private String generateMigrationFile(String className, String version,
                                         List<String> upStatements,
                                         List<String> downStatements) {
        StringBuilder sb = new StringBuilder();

        sb.append("package ").append(PACKAGE_NAME).append(";\n\n");
        sb.append("import com.vcinsidedigital.orm_utils.migration.Migration;\n");
        sb.append("import com.vcinsidedigital.orm_utils.migration.MigrationContext;\n\n");

        sb.append("/**\n");
        sb.append(" * Auto-generated migration\n");
        sb.append(" * Generated at: ").append(LocalDateTime.now()).append("\n");
        sb.append(" */\n");
        sb.append("public class ").append(className).append(" extends Migration {\n\n");

        sb.append("    public ").append(className).append("() {\n");
        sb.append("        super(\"").append(version).append("\");\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public void up(MigrationContext context) throws Exception {\n");
        for (String statement : upStatements) {
            if (statement.startsWith("//")) {
                sb.append("        ").append(statement).append("\n");
            } else {
                sb.append("        context.execute(\n");
                sb.append("            \"").append(escapeSQL(statement)).append("\"\n");
                sb.append("        );\n");
            }
        }
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public void down(MigrationContext context) throws Exception {\n");
        for (String statement : downStatements) {
            if (statement.startsWith("//")) {
                sb.append("        ").append(statement).append("\n");
            } else {
                sb.append("        context.execute(\n");
                sb.append("            \"").append(escapeSQL(statement)).append("\"\n");
                sb.append("        );\n");
            }
        }
        sb.append("    }\n");

        sb.append("}\n");

        return sb.toString();
    }

    private void saveMigrationFile(String className, String content) throws IOException {
        File file = new File(MIGRATIONS_DIR, className + ".java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        logger.info("Migration file saved: {}", file.getAbsolutePath());
    }

    private String escapeSQL(String sql) {
        return sql.replace("\"", "\\\"")
                .replace("\n", "\\n\" +\n                \"");
    }

    // Métodos auxiliares

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        if (dbType == DatabaseConfig.DatabaseType.MYSQL) {
            String sql = "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, tableName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } else {
            String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, tableName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    private Map<String, ColumnInfo> getExistingColumns(Connection conn, String tableName)
            throws SQLException {
        Map<String, ColumnInfo> columns = new HashMap<>();

        if (dbType == DatabaseConfig.DatabaseType.MYSQL) {
            String sql = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY " +
                    "FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        } else {
            String sql = "PRAGMA table_info(" + tableName + ")";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

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
                dataType = getColumnTypeForForeignKey();
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

    private boolean needsModification(ColumnInfo existing, ColumnInfo expected) {
        String existingBaseType = existing.dataType.replaceAll("\\(.*\\)", "").trim().toUpperCase();
        String expectedBaseType = expected.dataType.replaceAll("\\(.*\\)", "").trim().toUpperCase();

        if (!existingBaseType.equals(expectedBaseType)) {
            return true;
        }

        if (existing.dataType.toUpperCase().startsWith("VARCHAR") ||
                existing.dataType.toUpperCase().startsWith("CHAR")) {
            if (!existing.dataType.equalsIgnoreCase(expected.dataType)) {
                return true;
            }
        }

        if (existing.nullable != expected.nullable) {
            return true;
        }

        if (existing.unique != expected.unique) {
            return true;
        }

        return false;
    }

    private String generateColumnDefinition(Field field) {
        if (field.isAnnotationPresent(ManyToOne.class)) {
            JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
            if (joinCol == null) return null;

            String colName = joinCol.name();
            String nullable = joinCol.nullable() ? "" : " NOT NULL";
            return colName + " " + getColumnTypeForForeignKey() + nullable;
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
                column != null ? column.length() : 255, isAutoIncrementPK);
        StringBuilder def = new StringBuilder(columnName).append(" ").append(type);

        if (field.isAnnotationPresent(Id.class)) {
            Id id = field.getAnnotation(Id.class);
            def.append(" PRIMARY KEY");

            if (id.autoIncrement() && isNumericType(field.getType())) {
                if (dbType == DatabaseConfig.DatabaseType.MYSQL) {
                    def.append(" AUTO_INCREMENT");
                } else {
                    def.append(" AUTOINCREMENT");
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

    private String generateForeignKey(Field field, String tableName) {
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

    private String getColumnType(Class<?> javaType, int length, boolean isAutoIncrementPK) {
        if (javaType == java.time.LocalDateTime.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DATETIME" : "TEXT";
        } else if (javaType == java.time.LocalDate.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DATE" : "TEXT";
        } else if (javaType == java.time.LocalTime.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ? "TIME" : "TEXT";
        } else if (javaType == Long.class || javaType == long.class) {
            if (dbType == DatabaseConfig.DatabaseType.SQLITE && isAutoIncrementPK) {
                return "INTEGER";
            }
            return dbType == DatabaseConfig.DatabaseType.SQLITE ? "INTEGER" : "BIGINT";
        } else if (javaType == Integer.class || javaType == int.class) {
            return "INTEGER";
        } else if (javaType == String.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ?
                    "VARCHAR(" + length + ")" : "TEXT";
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ? "TINYINT(1)" : "INTEGER";
        } else if (javaType == Double.class || javaType == double.class) {
            return dbType == DatabaseConfig.DatabaseType.SQLITE ? "REAL" : "DOUBLE";
        } else if (javaType == Float.class || javaType == float.class) {
            return dbType == DatabaseConfig.DatabaseType.SQLITE ? "REAL" : "FLOAT";
        }
        return "TEXT";
    }

    private String getColumnTypeForForeignKey() {
        return dbType == DatabaseConfig.DatabaseType.SQLITE ? "INTEGER" : "BIGINT";
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
    }
}