package com.vcinsidedigital.orm_utils.config;

public class DatabaseConfig {
    private DatabaseType type;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String filePath; // Para SQLite
    private String schema; // Para PostgreSQL/SQL Server
    private String instance; // Para SQL Server named instances

    private DatabaseConfig() {}

    public static Builder builder() {
        return new Builder();
    }

    public String getJdbcUrl() {
        return switch (type) {
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                    host, port, database);
            case SQLITE -> String.format("jdbc:sqlite:%s", filePath);
            case POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s",
                    host, port, database);
            case SQLSERVER -> {
                StringBuilder url = new StringBuilder("jdbc:sqlserver://");
                url.append(host);
                if (instance != null && !instance.isEmpty()) {
                    url.append("\\").append(instance);
                }
                url.append(":").append(port);
                url.append(";databaseName=").append(database);
                url.append(";encrypt=false;trustServerCertificate=true");
                yield url.toString();
            }
        };
    }

    public String getDriverClassName() {
        return switch (type) {
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case SQLITE -> "org.sqlite.JDBC";
            case POSTGRESQL -> "org.postgresql.Driver";
            case SQLSERVER -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
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
    public String getSchema() { return schema; }
    public String getInstance() { return instance; }

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

        public Builder postgresql(String host, int port, String database) {
            config.type = DatabaseType.POSTGRESQL;
            config.host = host;
            config.port = port;
            config.database = database;
            return this;
        }

        /**
         * Configura conexão com SQL Server
         * @param host Hostname ou IP do servidor
         * @param port Porta (padrão: 1433)
         * @param database Nome do banco de dados
         * @return Builder instance
         */
        public Builder sqlserver(String host, int port, String database) {
            config.type = DatabaseType.SQLSERVER;
            config.host = host;
            config.port = port;
            config.database = database;
            return this;
        }

        /**
         * Configura SQL Server com porta padrão (1433)
         */
        public Builder sqlserver(String host, String database) {
            return sqlserver(host, 1433, database);
        }

        /**
         * Define uma named instance do SQL Server (ex: SQLEXPRESS)
         */
        public Builder instance(String instance) {
            config.instance = instance;
            return this;
        }

        public Builder credentials(String username, String password) {
            config.username = username;
            config.password = password;
            return this;
        }

        public Builder schema(String schema) {
            config.schema = schema;
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
        MYSQL,
        SQLITE,
        POSTGRESQL,
        SQLSERVER
    }
}