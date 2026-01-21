package com.vcinsidedigital.orm_utils.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionPool {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPool.class);
    private static ConnectionPool instance;

    private final DatabaseConfig config;
    private final BlockingQueue<PooledConnection> availableConnections;
    private final AtomicInteger activeConnections;

    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final long maxLifetime;

    private volatile boolean isShutdown = false;

    private ConnectionPool(DatabaseConfig config) {
        this.config = config;
        this.maxPoolSize = 10;
        this.minIdle = 2;
        this.connectionTimeout = 30000; // 30 seconds
        this.idleTimeout = 600000; // 10 minutes
        this.maxLifetime = 1800000; // 30 minutes

        this.availableConnections = new ArrayBlockingQueue<>(maxPoolSize);
        this.activeConnections = new AtomicInteger(0);

        try {
            // Load JDBC driver
            Class.forName(config.getDriverClassName());

            // Create minimum idle connections
            for (int i = 0; i < minIdle; i++) {
                availableConnections.offer(createNewConnection());
            }

            logger.info("ConnectionPool initialized with {} connections", minIdle);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ConnectionPool", e);
        }
    }

    public static void initialize(DatabaseConfig config) {
        if (instance != null) {
            instance.close();
        }
        instance = new ConnectionPool(config);
    }

    public static ConnectionPool getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConnectionPool not initialized");
        }
        return instance;
    }

    public static void createDatabase(DatabaseConfig config) throws SQLException {
        String url;
        String databaseName;
        String sqlScript;

        // Extract database name and build URL without the database
        switch (config.getType()) {
            case MYSQL:
                url = config.getJdbcUrl();
                int lastSlash = url.lastIndexOf('/');
                databaseName = url.substring(lastSlash + 1);
                sqlScript = "CREATE DATABASE IF NOT EXISTS " + config.getDatabase() +
                        " CHARACTER SET "+config.getEncoding()+ " COLLATE " +config.getEncoding()+"_unicode_ci";
                // URL format: jdbc:mysql://host:port/database


                // Remove query parameters if they exist
                int queryStart = databaseName.indexOf('?');
                if (queryStart > 0) {
                    databaseName = databaseName.substring(0, queryStart);
                }
                // URL without the database
                url = url.substring(0, lastSlash);
                break;

            case POSTGRESQL:

                // URL format: jdbc:postgresql://host:port/database
                url = config.getJdbcUrl();
                lastSlash = url.lastIndexOf('/');
                databaseName = url.substring(lastSlash + 1);
                queryStart = databaseName.indexOf('?');
                if (queryStart > 0) {
                    databaseName = databaseName.substring(0, queryStart);
                }
                // PostgreSQL needs to connect to an existing database (postgres)
                url = url.substring(0, lastSlash) + "/postgres";

                // PostgreSQL - check database existence before creating
                sqlScript = "SELECT 1 FROM pg_database WHERE datname = '" + config.getDatabase() + "'";
                break;

            case SQLSERVER:
                // URL format: jdbc:sqlserver://host:port;databaseName=database
                url = config.getJdbcUrl();
                int dbNameStart = url.indexOf("databaseName=");
                if (dbNameStart > 0) {
                    int dbNameEnd = url.indexOf(';', dbNameStart);
                    if (dbNameEnd > 0) {
                        databaseName = url.substring(dbNameStart + 13, dbNameEnd);
                        // Remove the databaseName parameter from URL
                        url = url.substring(0, dbNameStart) + url.substring(dbNameEnd + 1);
                    } else {
                        databaseName = url.substring(dbNameStart + 13);
                        url = url.substring(0, dbNameStart - 1);
                    }
                } else {
                    throw new SQLException("Database name not found in SQL Server URL");
                }

                sqlScript = "IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = '" +config.getDatabase()+"') "  +
                        "BEGIN " +
                        "CREATE DATABASE " +config.getDatabase()+" COLLATE " + config.getEncoding()+" "+
                        "END";
                break;

            default:
                throw new UnsupportedOperationException(
                        "Database type not supported: " + config.getType()
                );
        }

        logger.info("Creating database '{}' using {}", databaseName, config.getType());

        Connection conn = null;
        java.sql.Statement stmt = null;


        try {
            // Load driver
            Class.forName(config.getDriverClassName());

            // Connect without specifying the database
            if (config.getUsername() != null && config.getPassword() != null) {
                conn = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
            } else {
                conn = DriverManager.getConnection(url);
            }

            stmt = conn.createStatement();

            // For PostgreSQL, check if the database already exists before creating
            if (config.getType() == DatabaseConfig.DatabaseType.POSTGRESQL) {
                java.sql.ResultSet rs = stmt.executeQuery(sqlScript);

                if (rs.next()) {
                    logger.info("Database '{}' already exists, skipping creation", databaseName);
                } else {
                    String createScript = "CREATE DATABASE " + config.getDatabase() +
                            " WITH ENCODING '" + config.getEncoding() + "'";
                    logger.debug("Executing SQL: {}", createScript);
                    stmt.execute(createScript);
                    logger.info("Database '{}' created successfully", databaseName);
                }
                rs.close();
            } else {
                // For other databases, execute normally
                String[] commands = sqlScript.split(";");
                for (String command : commands) {
                    command = command.trim();
                    if (!command.isEmpty()) {
                        logger.debug("Executing SQL: {}", command);
                        stmt.execute(command);
                    }
                }
                logger.info("Database '{}' created successfully", databaseName);
            }

        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC Driver not found: " + config.getDriverClassName(), e);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    logger.error("Error closing statement", e);
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    public static void dropDatabase(DatabaseConfig config) throws SQLException {
        String url;
        String databaseName;
        String sqlScript;

        // Extract database name and build URL without the database
        switch (config.getType()) {
            case MYSQL:
                url = config.getJdbcUrl();
                int lastSlash = url.lastIndexOf('/');
                databaseName = url.substring(lastSlash + 1);

                // Remove query parameters if they exist
                int queryStart = databaseName.indexOf('?');
                if (queryStart > 0) {
                    databaseName = databaseName.substring(0, queryStart);
                }

                // URL without the database
                url = url.substring(0, lastSlash);
                sqlScript = "DROP DATABASE IF EXISTS " + config.getDatabase();
                break;

            case POSTGRESQL:
                // URL format: jdbc:postgresql://host:port/database
                url = config.getJdbcUrl();
                lastSlash = url.lastIndexOf('/');
                databaseName = url.substring(lastSlash + 1);
                queryStart = databaseName.indexOf('?');
                if (queryStart > 0) {
                    databaseName = databaseName.substring(0, queryStart);
                }

                // PostgreSQL needs to connect to an existing database (postgres)
                url = url.substring(0, lastSlash) + "/postgres";

                // PostgreSQL requires terminating active connections before dropping
                sqlScript = "SELECT pg_terminate_backend(pg_stat_activity.pid) " +
                        "FROM pg_stat_activity " +
                        "WHERE pg_stat_activity.datname = '" + config.getDatabase() + "' " +
                        "AND pid <> pg_backend_pid();" +
                        "DROP DATABASE IF EXISTS " + config.getDatabase();
                break;

            case SQLSERVER:
                // URL format: jdbc:sqlserver://host:port;databaseName=database
                url = config.getJdbcUrl();
                int dbNameStart = url.indexOf("databaseName=");
                if (dbNameStart > 0) {
                    int dbNameEnd = url.indexOf(';', dbNameStart);
                    if (dbNameEnd > 0) {
                        databaseName = url.substring(dbNameStart + 13, dbNameEnd);
                        // Remove the databaseName parameter from URL
                        url = url.substring(0, dbNameStart) + url.substring(dbNameEnd + 1);
                    } else {
                        databaseName = url.substring(dbNameStart + 13);
                        url = url.substring(0, dbNameStart - 1);
                    }
                } else {
                    throw new SQLException("Database name not found in SQL Server URL");
                }

                // SQL Server - special marker to execute commands separately
                sqlScript = "SQLSERVER_DROP";
                break;

            default:
                throw new UnsupportedOperationException(
                        "Database type not supported: " + config.getType()
                );
        }

        logger.info("Dropping database '{}' using {}", databaseName, config.getType());

        Connection conn = null;
        java.sql.Statement stmt = null;

        try {
            // Load driver
            Class.forName(config.getDriverClassName());

            // Connect without specifying the database
            if (config.getUsername() != null && config.getPassword() != null) {
                conn = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
            } else {
                conn = DriverManager.getConnection(url);
            }

            stmt = conn.createStatement();

            // SQL Server requires executing commands separately
            if (config.getType() == DatabaseConfig.DatabaseType.SQLSERVER) {
                // Check if the database exists
                java.sql.ResultSet rs = stmt.executeQuery(
                        "SELECT name FROM sys.databases WHERE name = '" + config.getDatabase() + "'"
                );

                if (rs.next()) {
                    rs.close();

                    // Set database to SINGLE_USER mode
                    String setSingleUser = "ALTER DATABASE [" + config.getDatabase() +
                            "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE";
                    logger.debug("Executing SQL: {}", setSingleUser);
                    stmt.execute(setSingleUser);

                    // Drop the database
                    String dropDb = "DROP DATABASE [" + config.getDatabase() + "]";
                    logger.debug("Executing SQL: {}", dropDb);
                    stmt.execute(dropDb);

                    logger.info("Database '{}' dropped successfully", databaseName);
                } else {
                    rs.close();
                    logger.info("Database '{}' does not exist, skipping drop", databaseName);
                }
            } else {
                // For other databases, execute normally
                String[] commands = sqlScript.split(";");
                for (String command : commands) {
                    command = command.trim();
                    if (!command.isEmpty()) {
                        logger.debug("Executing SQL: {}", command);
                        stmt.execute(command);
                    }
                }
                logger.info("Database '{}' dropped successfully", databaseName);
            }

        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC Driver not found: " + config.getDriverClassName(), e);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    logger.error("Error closing statement", e);
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (isShutdown) {
            throw new SQLException("ConnectionPool is shutdown");
        }

        try {
            PooledConnection pooledConn = availableConnections.poll(
                    connectionTimeout, TimeUnit.MILLISECONDS
            );

            if (pooledConn == null) {
                // Try to create a new connection if pool limit is not reached
                if (activeConnections.get() < maxPoolSize) {
                    pooledConn = createNewConnection();
                } else {
                    throw new SQLException("Connection timeout - pool exhausted");
                }
            }

            // Validate connection
            if (!isConnectionValid(pooledConn)) {
                logger.warn("Invalid connection detected, creating new one");
                closeConnection(pooledConn);
                pooledConn = createNewConnection();
            }

            pooledConn.setLastUsed(System.currentTimeMillis());
            return new ConnectionWrapper(pooledConn, this);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection", e);
        }
    }

    protected void returnConnection(PooledConnection connection) {
        if (isShutdown) {
            closeConnection(connection);
            return;
        }

        long now = System.currentTimeMillis();
        long age = now - connection.getCreatedAt();
        long idleTime = now - connection.getLastUsed();

        // Close if connection exceeded max lifetime or idle timeout
        if (age > maxLifetime || idleTime > idleTimeout) {
            logger.debug("Closing aged/idle connection");
            closeConnection(connection);

            // Maintain minimum pool size
            if (activeConnections.get() < minIdle) {
                try {
                    availableConnections.offer(createNewConnection());
                } catch (SQLException e) {
                    logger.error("Failed to create replacement connection", e);
                }
            }
        } else {
            // Return connection to pool queue
            if (!availableConnections.offer(connection)) {
                logger.warn("Failed to return connection to pool, closing it");
                closeConnection(connection);
            }
        }
    }

    private PooledConnection createNewConnection() throws SQLException {
        String url = config.getJdbcUrl();
        Connection conn;

        if (config.getUsername() != null && config.getPassword() != null) {
            conn = DriverManager.getConnection(
                    url, config.getUsername(), config.getPassword()
            );
        } else {
            conn = DriverManager.getConnection(url);
        }

        activeConnections.incrementAndGet();
        logger.debug("Created new connection. Active: {}", activeConnections.get());

        return new PooledConnection(conn);
    }

    private boolean isConnectionValid(PooledConnection pooledConn) {
        try {
            Connection conn = pooledConn.getConnection();
            return conn != null && !conn.isClosed() && conn.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    private void closeConnection(PooledConnection pooledConn) {
        try {
            if (pooledConn != null && pooledConn.getConnection() != null) {
                pooledConn.getConnection().close();
                activeConnections.decrementAndGet();
                logger.debug("Closed connection. Active: {}", activeConnections.get());
            }
        } catch (SQLException e) {
            logger.error("Error closing connection", e);
        }
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public void close() {
        if (isShutdown) {
            return;
        }

        isShutdown = true;
        logger.info("Closing ConnectionPool");

        // Close all available connections
        PooledConnection conn;
        while ((conn = availableConnections.poll()) != null) {
            closeConnection(conn);
        }

        logger.info("ConnectionPool closed. Remaining active connections: {}",
                activeConnections.get());
    }

    // Internal class to store connection metadata
    protected static class PooledConnection {
        private final Connection connection;
        private final long createdAt;
        private volatile long lastUsed;

        public PooledConnection(Connection connection) {
            this.connection = connection;
            this.createdAt = System.currentTimeMillis();
            this.lastUsed = createdAt;
        }

        public Connection getConnection() {
            return connection;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getLastUsed() {
            return lastUsed;
        }

        public void setLastUsed(long lastUsed) {
            this.lastUsed = lastUsed;
        }
    }

    // Wrapper to intercept close() and return connection to pool
    private static class ConnectionWrapper implements Connection {
        private final PooledConnection pooledConnection;
        private final ConnectionPool pool;
        private boolean closed = false;

        public ConnectionWrapper(PooledConnection pooledConnection, ConnectionPool pool) {
            this.pooledConnection = pooledConnection;
            this.pool = pool;
        }

        @Override
        public void close() throws SQLException {
            if (!closed) {
                closed = true;
                pool.returnConnection(pooledConnection);
            }
        }

        // Delegate all other methods to the underlying connection
        @Override
        public java.sql.Statement createStatement() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().createStatement();
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().prepareStatement(sql);
        }

        @Override
        public java.sql.CallableStatement prepareCall(String sql) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().prepareCall(sql);
        }

        @Override
        public String nativeSQL(String sql) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().nativeSQL(sql);
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().setAutoCommit(autoCommit);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getAutoCommit();
        }

        @Override
        public void commit() throws SQLException {
            checkClosed();
            pooledConnection.getConnection().commit();
        }

        @Override
        public void rollback() throws SQLException {
            checkClosed();
            pooledConnection.getConnection().rollback();
        }

        @Override
        public boolean isClosed() throws SQLException {
            return closed || pooledConnection.getConnection().isClosed();
        }

        @Override
        public java.sql.DatabaseMetaData getMetaData() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getMetaData();
        }

        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().setReadOnly(readOnly);
        }

        @Override
        public boolean isReadOnly() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().isReadOnly();
        }

        @Override
        public void setCatalog(String catalog) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().setCatalog(catalog);
        }

        @Override
        public String getCatalog() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getCatalog();
        }

        @Override
        public void setTransactionIsolation(int level) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().setTransactionIsolation(level);
        }

        @Override
        public int getTransactionIsolation() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getTransactionIsolation();
        }

        @Override
        public java.sql.SQLWarning getWarnings() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getWarnings();
        }

        @Override
        public void clearWarnings() throws SQLException {
            checkClosed();
            pooledConnection.getConnection().clearWarnings();
        }

        @Override
        public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency)
                throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().createStatement(
                    resultSetType, resultSetConcurrency);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType,
                                                           int resultSetConcurrency) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().prepareStatement(
                    sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType,
                                                      int resultSetConcurrency) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().prepareCall(
                    sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public java.util.Map<String, Class<?>> getTypeMap() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getTypeMap();
        }

        @Override
        public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().setTypeMap(map);
        }

        @Override
        public void setHoldability(int holdability) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().setHoldability(holdability);
        }

        @Override
        public int getHoldability() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getHoldability();
        }

        @Override
        public java.sql.Savepoint setSavepoint() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().setSavepoint();
        }

        @Override
        public java.sql.Savepoint setSavepoint(String name) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().setSavepoint(name);
        }

        @Override
        public void rollback(java.sql.Savepoint savepoint) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().rollback(savepoint);
        }

        @Override
        public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().releaseSavepoint(savepoint);
        }

        @Override
        public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency,
                                                  int resultSetHoldability) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().createStatement(
                    resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType,
                                                           int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().prepareStatement(
                    sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType,
                                                      int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().prepareCall(
                    sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
                throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().prepareStatement(sql, autoGeneratedKeys);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes)
                throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().prepareStatement(sql, columnIndexes);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames)
                throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().prepareStatement(sql, columnNames);
        }

        @Override
        public java.sql.Clob createClob() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().createClob();
        }

        @Override
        public java.sql.Blob createBlob() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().createBlob();
        }

        @Override
        public java.sql.NClob createNClob() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().createNClob();
        }

        @Override
        public java.sql.SQLXML createSQLXML() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().createSQLXML();
        }

        @Override
        public boolean isValid(int timeout) throws SQLException {
            if (closed) return false;
            return pooledConnection.getConnection().isValid(timeout);
        }

        @Override
        public void setClientInfo(String name, String value) throws java.sql.SQLClientInfoException {
            try {
                checkClosed();
                pooledConnection.getConnection().setClientInfo(name, value);
            } catch (SQLException e) {
                throw new java.sql.SQLClientInfoException();
            }
        }

        @Override
        public void setClientInfo(java.util.Properties properties)
                throws java.sql.SQLClientInfoException {
            try {
                checkClosed();
                pooledConnection.getConnection().setClientInfo(properties);
            } catch (SQLException e) {
                throw new java.sql.SQLClientInfoException();
            }
        }

        @Override
        public String getClientInfo(String name) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getClientInfo(name);
        }

        @Override
        public java.util.Properties getClientInfo() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getClientInfo();
        }

        @Override
        public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().createArrayOf(typeName, elements);
        }

        @Override
        public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().createStruct(typeName, attributes);
        }

        @Override
        public void setSchema(String schema) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().setSchema(schema);
        }

        @Override
        public String getSchema() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getSchema();
        }

        @Override
        public void abort(java.util.concurrent.Executor executor) throws SQLException {
            checkClosed();
            pooledConnection.getConnection().abort(executor);
        }

        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds)
                throws SQLException {
            checkClosed();
            pooledConnection.getConnection().setNetworkTimeout(executor, milliseconds);
        }

        @Override
        public int getNetworkTimeout() throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().getNetworkTimeout();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            checkClosed();
            return pooledConnection.getConnection().isWrapperFor(iface);
        }

        private void checkClosed() throws SQLException {
            if (closed) {
                throw new SQLException("Connection is closed");
            }
        }
    }
}