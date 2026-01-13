package com.vcinsidedigital.orm_utils.annotations;

import com.vcinsidedigital.orm_utils.config.DatabaseConfig;

public enum ColumnType {
    // Tipos automáticos (inferidos do tipo Java)
    AUTO,

    // Tipos de texto
    VARCHAR,        // VARCHAR(n) - MySQL/SQLite
    TEXT,          // TEXT - ambos
    LONGTEXT,      // LONGTEXT (MySQL) / TEXT (SQLite)
    MEDIUMTEXT,    // MEDIUMTEXT (MySQL) / TEXT (SQLite)
    TINYTEXT,      // TINYTEXT (MySQL) / TEXT (SQLite)

    // Tipos numéricos inteiros
    TINYINT,       // TINYINT (MySQL) / INTEGER (SQLite)
    SMALLINT,      // SMALLINT - ambos
    INT,           // INT (MySQL) / INTEGER (SQLite)
    BIGINT,        // BIGINT (MySQL) / INTEGER (SQLite)

    // Tipos numéricos decimais
    FLOAT,         // FLOAT (MySQL) / REAL (SQLite)
    DOUBLE,        // DOUBLE (MySQL) / REAL (SQLite)
    DECIMAL,       // DECIMAL(p,s) (MySQL) / REAL (SQLite)

    // Tipos booleanos
    BOOLEAN,       // TINYINT(1) (MySQL) / INTEGER (SQLite)

    // Tipos de data/hora
    DATE,          // DATE (MySQL) / TEXT (SQLite)
    DATETIME,      // DATETIME (MySQL) / TEXT (SQLite)
    TIMESTAMP,     // TIMESTAMP (MySQL) / TEXT (SQLite)
    TIME,          // TIME (MySQL) / TEXT (SQLite)
    YEAR,          // YEAR (MySQL) / INTEGER (SQLite)

    // Tipos binários
    BLOB,          // BLOB - ambos
    LONGBLOB,      // LONGBLOB (MySQL) / BLOB (SQLite)
    MEDIUMBLOB,    // MEDIUMBLOB (MySQL) / BLOB (SQLite)
    TINYBLOB,      // TINYBLOB (MySQL) / BLOB (SQLite)

    // Tipos especiais
    JSON,          // JSON (MySQL) / TEXT (SQLite)
    ENUM;          // ENUM (MySQL) / TEXT (SQLite)

    /**
     * Retorna o tipo SQL correspondente para o banco de dados especificado
     */
    public String getSqlType(DatabaseConfig.DatabaseType dbType, int length, int precision, int scale) {
        switch (this) {
            case AUTO:
                return null; // Será inferido do tipo Java

            // Tipos de texto
            case VARCHAR:
                return "VARCHAR(" + length + ")";
            case TEXT:
                return "TEXT";
            case LONGTEXT:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "LONGTEXT" : "TEXT";
            case MEDIUMTEXT:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "MEDIUMTEXT" : "TEXT";
            case TINYTEXT:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "TINYTEXT" : "TEXT";

            // Tipos numéricos inteiros
            case TINYINT:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "TINYINT" : "INTEGER";
            case SMALLINT:
                return "SMALLINT";
            case INT:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "INT" : "INTEGER";
            case BIGINT:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "BIGINT" : "INTEGER";

            // Tipos numéricos decimais
            case FLOAT:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "FLOAT" : "REAL";
            case DOUBLE:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DOUBLE" : "REAL";
            case DECIMAL:
                if (precision > 0 && scale > 0) {
                    return dbType == DatabaseConfig.DatabaseType.MYSQL
                            ? "DECIMAL(" + precision + "," + scale + ")"
                            : "REAL";
                }
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DECIMAL(10,2)" : "REAL";

            // Tipos booleanos
            case BOOLEAN:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "TINYINT(1)" : "INTEGER";

            // Tipos de data/hora
            case DATE:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DATE" : "TEXT";
            case DATETIME:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DATETIME" : "TEXT";
            case TIMESTAMP:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "DATETIME" : "TEXT";
            case TIME:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "TIME" : "TEXT";
            case YEAR:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "YEAR" : "INTEGER";

            // Tipos binários
            case BLOB:
                return "BLOB";
            case LONGBLOB:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "LONGBLOB" : "BLOB";
            case MEDIUMBLOB:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "MEDIUMBLOB" : "BLOB";
            case TINYBLOB:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "TINYBLOB" : "BLOB";

            // Tipos especiais
            case JSON:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "JSON" : "TEXT";
            case ENUM:
                return dbType == DatabaseConfig.DatabaseType.MYSQL ? "ENUM" : "TEXT";

            default:
                return "TEXT";
        }
    }
}