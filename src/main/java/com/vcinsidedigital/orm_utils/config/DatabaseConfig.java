package com.vcinsidedigital.orm_utils.config;

public class DatabaseConfig {
    private DatabaseType type;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String filePath; // Para SQLite

    private DatabaseConfig() {}

    public static Builder builder() {
        return new Builder();
    }

    public String getJdbcUrl() {
        return switch (type) {
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s", host, port, database);
            case SQLITE -> String.format("jdbc:sqlite:%s", filePath);
        };
    }

    public String getDriverClassName() {
        return switch (type) {
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case SQLITE -> "org.sqlite.JDBC";
        };
    }

    // Getters
    public DatabaseType getType() { return type; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFilePath() { return filePath; }

    public static class Builder {
        private final DatabaseConfig config = new DatabaseConfig();

        public Builder mysql(String host, int port, String database) {
            config.type = DatabaseType.MYSQL;
            config.host = host;
            config.port = port;
            config.database = database;
            return this;
        }

        public Builder sqlite(String filePath) {
            config.type = DatabaseType.SQLITE;
            config.filePath = filePath;
            return this;
        }

        public Builder credentials(String username, String password) {
            config.username = username;
            config.password = password;
            return this;
        }

        public DatabaseConfig build() {
            if (config.type == null) {
                throw new IllegalStateException("Database type not specified");
            }
            return config;
        }
    }

    public enum DatabaseType {
        MYSQL, SQLITE
    }
}
