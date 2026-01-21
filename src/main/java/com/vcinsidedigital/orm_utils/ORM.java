package com.vcinsidedigital.orm_utils;

import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import com.vcinsidedigital.orm_utils.core.EntityManager;
import com.vcinsidedigital.orm_utils.migration.MigrationGenerator;
import com.vcinsidedigital.orm_utils.migration.MigrationManager;
import com.vcinsidedigital.orm_utils.migration.SqlServerMigrationGenerator;
import com.vcinsidedigital.orm_utils.schema.SchemaGenerator;
import com.vcinsidedigital.orm_utils.schema.SqlServerSchemaGenerator;

public class ORM {
    private final EntityManager entityManager;
    private final SchemaGenerator schemaGenerator;
    private final SqlServerSchemaGenerator sqlServerSchemaGenerator;
    private final MigrationManager migrationManager;
    private final MigrationGenerator migrationGenerator;
    private final SqlServerMigrationGenerator sqlServerMigrationGenerator;
    private final DatabaseConfig config;
    private boolean initialized = false;

    public ORM(DatabaseConfig config) {
        ConnectionPool.initialize(config);
        this.config = config;
        this.entityManager = new EntityManager();
        this.migrationManager = new MigrationManager(config.getType());

        // Initialize the appropriate generators based on the database type
        if (config.getType() == DatabaseConfig.DatabaseType.SQLSERVER) {
            this.sqlServerSchemaGenerator = new SqlServerSchemaGenerator();
            this.sqlServerMigrationGenerator = new SqlServerMigrationGenerator();
            this.schemaGenerator = null;
            this.migrationGenerator = null;
        } else {
            this.schemaGenerator = new SchemaGenerator();
            this.migrationGenerator = new MigrationGenerator(config.getType());
            this.sqlServerSchemaGenerator = null;
            this.sqlServerMigrationGenerator = null;
        }
    }

    /**
     * Creates a database by executing a custom SQL script.
     * Supports MySQL, PostgreSQL and SQL Server (SQLite does not require as it creates automatically).
     *
     * @param config database configuration
     * @throws Exception if there is an error during creation or if database type is not supported
     */
    public static void createDatabase(DatabaseConfig config) throws Exception {
        if (config.getType() == DatabaseConfig.DatabaseType.SQLITE) {
            throw new UnsupportedOperationException(
                    "SQLite does not require manual database creation. The file is created automatically."
            );
        }

        if (config.getType() != DatabaseConfig.DatabaseType.MYSQL &&
                config.getType() != DatabaseConfig.DatabaseType.POSTGRESQL &&
                config.getType() != DatabaseConfig.DatabaseType.SQLSERVER) {
            throw new IllegalArgumentException(
                    "Unsupported database type: " + config.getType()
            );
        }

        ConnectionPool.createDatabase(config);
    }

    public static void dropDatabase(DatabaseConfig config) throws Exception {
        if (config.getType() == DatabaseConfig.DatabaseType.SQLITE) {
            throw new UnsupportedOperationException(
                    "SQLite does not support DROP DATABASE. Delete the database file manually."
            );
        }

        if (config.getType() != DatabaseConfig.DatabaseType.MYSQL &&
                config.getType() != DatabaseConfig.DatabaseType.POSTGRESQL &&
                config.getType() != DatabaseConfig.DatabaseType.SQLSERVER) {
            throw new IllegalArgumentException(
                    "Unsupported database type: " + config.getType()
            );
        }

        ConnectionPool.dropDatabase(config);
    }

    public ORM registerEntity(Class<?> entityClass) {
        if (config.getType() == DatabaseConfig.DatabaseType.SQLSERVER) {
            sqlServerSchemaGenerator.addEntity(entityClass);
            sqlServerMigrationGenerator.addEntity(entityClass);
        } else {
            schemaGenerator.addEntity(entityClass);
            migrationGenerator.addEntity(entityClass);
        }
        return this;
    }

    public ORM initialize() throws Exception {
        if (!initialized) {
            if (config.getType() == DatabaseConfig.DatabaseType.SQLSERVER) {
                sqlServerSchemaGenerator.generateSchema();
            } else {
                schemaGenerator.generateSchema();
            }
            migrationManager.migrate();
            initialized = true;
        }
        return this;
    }

    /**
     * Generates migration files automatically based on the registered entities.
     * Creates the 'migrations' directory if it does not exist and generates
     * VersionXXXX.java files containing SQL commands to create or update the schema.
     *
     * @return this ORM instance for method chaining
     * @throws Exception if an error occurs during generation
     */
    public ORM createMigrations() throws Exception {
        if (config.getType() == DatabaseConfig.DatabaseType.SQLSERVER) {
            sqlServerMigrationGenerator.generateMigrations();
        } else {
            migrationGenerator.generateMigrations();
        }
        return this;
    }

    /**
     * Defines whether columns removed from entities should be automatically
     * dropped from the database (SQL Server only).
     *
     * @param autoDropColumns true to drop automatically, false to only warn
     * @return this ORM instance for method chaining
     */
    public ORM setAutoDropColumns(boolean autoDropColumns) {
        if (config.getType() == DatabaseConfig.DatabaseType.SQLSERVER) {
            sqlServerSchemaGenerator.setAutoDropColumns(autoDropColumns);
        } else {
            schemaGenerator.setAutoDropColumns(autoDropColumns);
        }
        return this;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public MigrationManager getMigrationManager() {
        return migrationManager;
    }

    public void shutdown() {
        ConnectionPool.getInstance().close();
    }
}