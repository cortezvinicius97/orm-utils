package com.vcinsidedigital.orm_utils.migration;

import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MigrationManager {
    private static final Logger logger = LoggerFactory.getLogger(MigrationManager.class);
    private final List<Migration> migrations = new ArrayList<>();

    public void addMigration(Migration migration) {
        migrations.add(migration);
        migrations.sort(Comparator.comparing(Migration::getVersion));
    }

    public void migrate() throws Exception {
        ensureMigrationTable();

        Set<String> appliedVersions = getAppliedVersions();

        for (Migration migration : migrations) {
            if (!appliedVersions.contains(migration.getVersion())) {
                logger.info("Applying migration: {}", migration.getVersion());
                applyMigration(migration);
                logger.info("Migration {} applied successfully", migration.getVersion());
            }
        }
    }

    public void rollback(int steps) throws Exception {
        Set<String> appliedVersions = getAppliedVersions();
        List<Migration> toRollback = migrations.stream()
                .filter(m -> appliedVersions.contains(m.getVersion()))
                .sorted(Comparator.comparing(Migration::getVersion).reversed())
                .limit(steps)
                .toList();

        for (Migration migration : toRollback) {
            logger.info("Rolling back migration: {}", migration.getVersion());
            rollbackMigration(migration);
            logger.info("Migration {} rolled back successfully", migration.getVersion());
        }
    }

    private void ensureMigrationTable() throws SQLException {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version VARCHAR(255) PRIMARY KEY,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            conn.createStatement().execute(sql);
        }
    }

    private Set<String> getAppliedVersions() throws SQLException {
        Set<String> versions = new HashSet<>();
        try (Connection conn = ConnectionPool.getInstance().getConnection();
             ResultSet rs = conn.createStatement()
                     .executeQuery("SELECT version FROM schema_migrations")) {
            while (rs.next()) {
                versions.add(rs.getString("version"));
            }
        }
        return versions;
    }

    private void applyMigration(Migration migration) throws Exception {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                MigrationContext context = new MigrationContext(conn);
                migration.up(context);

                String sql = "INSERT INTO schema_migrations (version) VALUES (?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, migration.getVersion());
                    pstmt.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void rollbackMigration(Migration migration) throws Exception {
        try (Connection conn = ConnectionPool.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                MigrationContext context = new MigrationContext(conn);
                migration.down(context);

                String sql = "DELETE FROM schema_migrations WHERE version = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, migration.getVersion());
                    pstmt.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
