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

        // Inicializar os generators apropriados baseado no tipo de banco
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
     * Gera arquivos de migration automaticamente baseado nas entidades registradas.
     * Cria o diretório 'migrations' se não existir e gera arquivos VersionXXXX.java
     * com os comandos SQL para criar/atualizar o schema.
     *
     * @return this ORM instance para method chaining
     * @throws Exception se houver erro na geração
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
     * Define se colunas removidas das entidades devem ser automaticamente
     * deletadas do banco de dados (apenas para SQL Server).
     *
     * @param autoDropColumns true para remover automaticamente, false para apenas avisar
     * @return this ORM instance para method chaining
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