package com.vcinsidedigital.orm_utils.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationContext {
    private final Connection connection;

    public MigrationContext(Connection connection) {
        this.connection = connection;
    }

    public void execute(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public Connection getConnection() {
        return connection;
    }
}
