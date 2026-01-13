package com.vcinsidedigital.orm_utils.schema;


import com.vcinsidedigital.orm_utils.annotations.*;
import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public class SchemaGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SchemaGenerator.class);
    private final Set<Class<?>> entities = new HashSet<>();

    public void addEntity(Class<?> entityClass) {
        if (!entityClass.isAnnotationPresent(Entity.class)) {
            throw new IllegalArgumentException("Class must be annotated with @Entity");
        }
        entities.add(entityClass);
    }

    public void generateSchema() throws SQLException {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            DatabaseConfig.DatabaseType dbType = ConnectionPool.getInstance()
                    .getConfig().getType();

            // Primeiro, criar tabelas principais
            for (Class<?> entity : entities) {
                String sql = generateCreateTableSQL(entity, dbType);
                logger.info("Creating table for entity: {}", entity.getSimpleName());
                logger.debug("SQL: {}", sql);
                conn.createStatement().execute(sql);
            }

            // Depois, criar tabelas de junção para ManyToMany
            for (Class<?> entity : entities) {
                createManyToManyTables(entity, dbType, conn);
            }
        }
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
                continue; // Skip collection fields
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
                return null; // Skip if no JoinColumn
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

        // Check if this is a primary key with auto-increment
        boolean isAutoIncrementPK = field.isAnnotationPresent(Id.class) &&
                field.getAnnotation(Id.class).autoIncrement();

        // FIX: Passar o tipo do campo para detectar LocalDateTime, LocalDate, etc.
        String type = getColumnType(field.getType(),
                column != null ? column.length() : 255, dbType, isAutoIncrementPK);
        StringBuilder def = new StringBuilder(columnName).append(" ").append(type);

        if (field.isAnnotationPresent(Id.class)) {
            Id id = field.getAnnotation(Id.class);
            def.append(" PRIMARY KEY");

            // Só adiciona AUTO_INCREMENT se for numérico E se autoIncrement for true
            if (id.autoIncrement() && isNumericType(field.getType())) {
                if (dbType == DatabaseConfig.DatabaseType.MYSQL) {
                    def.append(" AUTO_INCREMENT");
                } else {
                    // SQLite usa AUTOINCREMENT
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

    private String getColumnType(Class<?> javaType, int length,
                                 DatabaseConfig.DatabaseType dbType,
                                 boolean isAutoIncrementPK) {
        // FIX: Adicionar suporte para tipos de data/hora do java.time
        if (javaType == java.time.LocalDateTime.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DATETIME" : "TEXT";
        } else if (javaType == java.time.LocalDate.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DATE" : "TEXT";
        } else if (javaType == java.time.LocalTime.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ? "TIME" : "TEXT";
        } else if (javaType == Long.class || javaType == long.class) {
            // SQLite precisa de INTEGER para AUTOINCREMENT, não BIGINT
            if (dbType == DatabaseConfig.DatabaseType.SQLITE && isAutoIncrementPK) {
                return "INTEGER";
            }
            return dbType == DatabaseConfig.DatabaseType.SQLITE ? "INTEGER" : "BIGINT";
        } else if (javaType == Integer.class || javaType == int.class) {
            return "INTEGER";
        } else if (javaType == String.class) {
            return "VARCHAR(" + length + ")";
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ? "BOOLEAN" : "INTEGER";
        } else if (javaType == Double.class || javaType == double.class) {
            return dbType == DatabaseConfig.DatabaseType.SQLITE ? "REAL" : "DOUBLE";
        } else if (javaType == Float.class || javaType == float.class) {
            return dbType == DatabaseConfig.DatabaseType.SQLITE ? "REAL" : "FLOAT";
        } else if (javaType == Date.class ||
                javaType == java.sql.Date.class ||
                javaType == java.sql.Timestamp.class) {
            return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DATETIME" : "TEXT";
        }
        return "TEXT";
    }

    // Método para foreign keys
    private String getColumnTypeForForeignKey(DatabaseConfig.DatabaseType dbType) {
        return dbType == DatabaseConfig.DatabaseType.SQLITE ? "INTEGER" : "BIGINT";
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
}