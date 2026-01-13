package com.vcinsidedigital.orm_utils;

import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import com.vcinsidedigital.orm_utils.core.EntityManager;
import com.vcinsidedigital.orm_utils.migration.MigrationManager;
import com.vcinsidedigital.orm_utils.schema.SchemaGenerator;

public class ORM {
    private final EntityManager entityManager;
    private final SchemaGenerator schemaGenerator;
    private final MigrationManager migrationManager;
    private boolean initialized = false;

    public ORM(DatabaseConfig config) {
        ConnectionPool.initialize(config);
        this.entityManager = new EntityManager();
        this.schemaGenerator = new SchemaGenerator();
        this.migrationManager = new MigrationManager();
    }

    public ORM registerEntity(Class<?> entityClass) {
        schemaGenerator.addEntity(entityClass);
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
