package com.vcinsidedigital.orm_utils.annotations;

import com.vcinsidedigital.orm_utils.config.DatabaseConfig;

public enum ColumnType {
    AUTO,
    VARCHAR, TEXT, LONGTEXT, MEDIUMTEXT, TINYTEXT,
    TINYINT, SMALLINT, INT, BIGINT,
    FLOAT, DOUBLE, DECIMAL,
    BOOLEAN,
    DATE, DATETIME, DATETIME2, TIMESTAMP, TIME, YEAR,
    BLOB, LONGBLOB, MEDIUMBLOB, TINYBLOB,
    JSON, ENUM;

    public String getSqlType(DatabaseConfig.DatabaseType dbType, int length, int precision, int scale) {
        switch (this) {
            case AUTO:
                return null;

            // Tipos de texto
            case VARCHAR:
                return switch (dbType) {
                    case MYSQL, POSTGRESQL -> "VARCHAR(" + length + ")";
                    case SQLSERVER -> length > 4000 ? "NVARCHAR(MAX)" : "NVARCHAR(" + length + ")";
                    case SQLITE -> "TEXT";
                };

            case TEXT:
                return switch (dbType) {
                    case MYSQL, POSTGRESQL -> "TEXT";
                    case SQLSERVER -> "NVARCHAR(MAX)";
                    case SQLITE -> "TEXT";
                };

            case LONGTEXT:
                return switch (dbType) {
                    case MYSQL -> "LONGTEXT";
                    case POSTGRESQL -> "TEXT";
                    case SQLSERVER -> "NVARCHAR(MAX)";
                    case SQLITE -> "TEXT";
                };

            case MEDIUMTEXT:
                return switch (dbType) {
                    case MYSQL -> "MEDIUMTEXT";
                    case POSTGRESQL -> "TEXT";
                    case SQLSERVER -> "NVARCHAR(MAX)";
                    case SQLITE -> "TEXT";
                };

            case TINYTEXT:
                return switch (dbType) {
                    case MYSQL -> "TINYTEXT";
                    case POSTGRESQL -> "VARCHAR(255)";
                    case SQLSERVER -> "NVARCHAR(255)";
                    case SQLITE -> "TEXT";
                };

            // Tipos numéricos inteiros
            case TINYINT:
                return switch (dbType) {
                    case MYSQL -> "TINYINT";
                    case POSTGRESQL -> "SMALLINT";
                    case SQLSERVER -> "TINYINT";
                    case SQLITE -> "INTEGER";
                };

            case SMALLINT:
                return "SMALLINT";

            case INT:
                return switch (dbType) {
                    case MYSQL, POSTGRESQL, SQLSERVER -> "INT";
                    case SQLITE -> "INTEGER";
                };

            case BIGINT:
                return switch (dbType) {
                    case MYSQL, POSTGRESQL, SQLSERVER -> "BIGINT";
                    case SQLITE -> "INTEGER";
                };

            // Tipos numéricos decimais
            case FLOAT:
                return switch (dbType) {
                    case MYSQL, POSTGRESQL -> "FLOAT";
                    case SQLSERVER -> "REAL";
                    case SQLITE -> "REAL";
                };

            case DOUBLE:
                return switch (dbType) {
                    case MYSQL -> "DOUBLE";
                    case POSTGRESQL -> "DOUBLE PRECISION";
                    case SQLSERVER -> "FLOAT";
                    case SQLITE -> "REAL";
                };

            case DECIMAL:
                if (precision > 0 && scale > 0) {
                    return switch (dbType) {
                        case MYSQL, POSTGRESQL, SQLSERVER ->
                                "DECIMAL(" + precision + "," + scale + ")";
                        case SQLITE -> "REAL";
                    };
                }
                return switch (dbType) {
                    case MYSQL, POSTGRESQL, SQLSERVER -> "DECIMAL(10,2)";
                    case SQLITE -> "REAL";
                };

            // Tipos booleanos
            case BOOLEAN:
                return switch (dbType) {
                    case MYSQL -> "TINYINT(1)";
                    case POSTGRESQL -> "BOOLEAN";
                    case SQLSERVER -> "BIT";
                    case SQLITE -> "INTEGER";
                };

            // Tipos de data/hora
            case DATE:
                return switch (dbType) {
                    case MYSQL, POSTGRESQL, SQLSERVER -> "DATE";
                    case SQLITE -> "TEXT";
                };

            case DATETIME:
                return switch (dbType) {
                    case MYSQL -> "DATETIME";
                    case POSTGRESQL -> "TIMESTAMP";
                    case SQLSERVER -> "DATETIME";
                    case SQLITE -> "TEXT";
                };

            case DATETIME2:
                return switch (dbType) {
                    case MYSQL -> "DATETIME";
                    case POSTGRESQL -> "TIMESTAMP";
                    case SQLSERVER -> "DATETIME2";
                    case SQLITE -> "TEXT";
                };

            case TIMESTAMP:
                return switch (dbType) {
                    case MYSQL -> "DATETIME";
                    case POSTGRESQL -> "TIMESTAMP";
                    case SQLSERVER -> "DATETIME2";
                    case SQLITE -> "TEXT";
                };

            case TIME:
                return switch (dbType) {
                    case MYSQL, POSTGRESQL, SQLSERVER -> "TIME";
                    case SQLITE -> "TEXT";
                };

            case YEAR:
                return switch (dbType) {
                    case MYSQL -> "YEAR";
                    case POSTGRESQL, SQLSERVER -> "INT";
                    case SQLITE -> "INTEGER";
                };

            // Tipos binários
            case BLOB:
                return switch (dbType) {
                    case MYSQL, SQLITE -> "BLOB";
                    case POSTGRESQL -> "BYTEA";
                    case SQLSERVER -> "VARBINARY(MAX)";
                };

            case LONGBLOB:
                return switch (dbType) {
                    case MYSQL -> "LONGBLOB";
                    case POSTGRESQL -> "BYTEA";
                    case SQLSERVER -> "VARBINARY(MAX)";
                    case SQLITE -> "BLOB";
                };

            case MEDIUMBLOB:
                return switch (dbType) {
                    case MYSQL -> "MEDIUMBLOB";
                    case POSTGRESQL -> "BYTEA";
                    case SQLSERVER -> "VARBINARY(MAX)";
                    case SQLITE -> "BLOB";
                };

            case TINYBLOB:
                return switch (dbType) {
                    case MYSQL -> "TINYBLOB";
                    case POSTGRESQL -> "BYTEA";
                    case SQLSERVER -> "VARBINARY(255)";
                    case SQLITE -> "BLOB";
                };

            // Tipos especiais
            case JSON:
                return switch (dbType) {
                    case MYSQL, POSTGRESQL -> "JSON";
                    case SQLSERVER -> "NVARCHAR(MAX)";
                    case SQLITE -> "TEXT";
                };

            case ENUM:
                return switch (dbType) {
                    case MYSQL -> "ENUM";
                    case POSTGRESQL, SQLSERVER, SQLITE -> "TEXT";
                };

            default:
                return switch (dbType) {
                    case SQLSERVER -> "NVARCHAR(255)";
                    default -> "TEXT";
                };
        }
    }
}