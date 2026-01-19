package com.vcinsidedigital.orm_utils;

import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import com.vcinsidedigital.orm_utils.core.EntityManager;
import com.vcinsidedigital.orm_utils.migration.MigrationManager;
import com.vcinsidedigital.orm_utils.schema.SchemaGenerator;

import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import com.vcinsidedigital.orm_utils.core.EntityManager;
import com.vcinsidedigital.orm_utils.migration.MigrationGenerator;
import com.vcinsidedigital.orm_utils.migration.MigrationManager;
import com.vcinsidedigital.orm_utils.schema.SchemaGenerator;

public class ORM {
    private final EntityManager entityManager;
    private final SchemaGenerator schemaGenerator;
    private final MigrationManager migrationManager;
    private final MigrationGenerator migrationGenerator;
    private final DatabaseConfig config;
    private boolean initialized = false;

    public ORM(DatabaseConfig config) {
        ConnectionPool.initialize(config);
        this.config = config;
        this.entityManager = new EntityManager();
        this.schemaGenerator = new SchemaGenerator();
        this.migrationManager = new MigrationManager();
        this.migrationGenerator = new MigrationGenerator(config.getType());
    }

    public ORM registerEntity(Class<?> entityClass) {
        schemaGenerator.addEntity(entityClass);
        migrationGenerator.addEntity(entityClass);
        return this;
    }

    public ORM initialize() throws Exception {
        if (!initialized) {
            schemaGenerator.generateSchema();
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
        migrationGenerator.generateMigrations();
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
