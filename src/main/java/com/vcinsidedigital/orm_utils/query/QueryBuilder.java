package com.vcinsidedigital.orm_utils.query;

import com.vcinsidedigital.orm_utils.annotations.*;
import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public class QueryBuilder<T> {
    private static final Logger logger = LoggerFactory.getLogger(QueryBuilder.class);

    private final Class<T> entityClass;
    private final StringBuilder query;
    private final List<Object> parameters;
    private final List<String> selectedColumns;
    private final List<String> whereConditions;
    private final List<String> orderByColumns;
    private final List<JoinClause> joins;

    private String tableName;
    private Integer limitValue;
    private Integer offsetValue;
    private boolean distinct = false;
    private String groupByColumn;
    private String havingClause;

    public QueryBuilder(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.query = new StringBuilder();
        this.parameters = new ArrayList<>();
        this.selectedColumns = new ArrayList<>();
        this.whereConditions = new ArrayList<>();
        this.orderByColumns = new ArrayList<>();
        this.joins = new ArrayList<>();
        this.tableName = getTableName(entityClass);
    }

    // ========== SELECT ==========

    public QueryBuilder<T> select(String... columns) {
        if (columns.length == 0) {
            selectedColumns.add("*");
        } else {
            selectedColumns.addAll(Arrays.asList(columns));
        }
        return this;
    }

    public QueryBuilder<T> distinct() {
        this.distinct = true;
        return this;
    }

    // ========== WHERE ==========

    public QueryBuilder<T> where(String column, Object value) {
        whereConditions.add(column + " = ?");
        parameters.add(value);
        return this;
    }

    public QueryBuilder<T> where(String column, String operator, Object value) {
        whereConditions.add(column + " " + operator + " ?");
        parameters.add(value);
        return this;
    }

    public QueryBuilder<T> whereNot(String column, Object value) {
        whereConditions.add(column + " != ?");
        parameters.add(value);
        return this;
    }

    public QueryBuilder<T> whereNull(String column) {
        whereConditions.add(column + " IS NULL");
        return this;
    }

    public QueryBuilder<T> whereNotNull(String column) {
        whereConditions.add(column + " IS NOT NULL");
        return this;
    }

    public QueryBuilder<T> whereIn(String column, Object... values) {
        String placeholders = String.join(", ", Collections.nCopies(values.length, "?"));
        whereConditions.add(column + " IN (" + placeholders + ")");
        parameters.addAll(Arrays.asList(values));
        return this;
    }

    public QueryBuilder<T> whereNotIn(String column, Object... values) {
        String placeholders = String.join(", ", Collections.nCopies(values.length, "?"));
        whereConditions.add(column + " NOT IN (" + placeholders + ")");
        parameters.addAll(Arrays.asList(values));
        return this;
    }

    public QueryBuilder<T> whereBetween(String column, Object start, Object end) {
        whereConditions.add(column + " BETWEEN ? AND ?");
        parameters.add(start);
        parameters.add(end);
        return this;
    }

    public QueryBuilder<T> like(String column, String pattern) {
        whereConditions.add(column + " LIKE ?");
        parameters.add(pattern);
        return this;
    }

    public QueryBuilder<T> notLike(String column, String pattern) {
        whereConditions.add(column + " NOT LIKE ?");
        parameters.add(pattern);
        return this;
    }

    // ========== AND / OR ==========

    public QueryBuilder<T> and(String column, Object value) {
        return where(column, value);
    }

    public QueryBuilder<T> and(String column, String operator, Object value) {
        return where(column, operator, value);
    }

    public QueryBuilder<T> or(String column, Object value) {
        if (!whereConditions.isEmpty()) {
            int lastIndex = whereConditions.size() - 1;
            String lastCondition = whereConditions.get(lastIndex);
            whereConditions.set(lastIndex, lastCondition + " OR " + column + " = ?");
            parameters.add(value);
        } else {
            where(column, value);
        }
        return this;
    }

    public QueryBuilder<T> or(String column, String operator, Object value) {
        if (!whereConditions.isEmpty()) {
            int lastIndex = whereConditions.size() - 1;
            String lastCondition = whereConditions.get(lastIndex);
            whereConditions.set(lastIndex, lastCondition + " OR " + column + " " + operator + " ?");
            parameters.add(value);
        } else {
            where(column, operator, value);
        }
        return this;
    }

    // ========== JOIN ==========

    public QueryBuilder<T> join(String table, String condition) {
        joins.add(new JoinClause("INNER JOIN", table, condition));
        return this;
    }

    public QueryBuilder<T> leftJoin(String table, String condition) {
        joins.add(new JoinClause("LEFT JOIN", table, condition));
        return this;
    }

    public QueryBuilder<T> rightJoin(String table, String condition) {
        joins.add(new JoinClause("RIGHT JOIN", table, condition));
        return this;
    }

    public QueryBuilder<T> innerJoin(String table, String condition) {
        joins.add(new JoinClause("INNER JOIN", table, condition));
        return this;
    }

    // ========== ORDER BY ==========

    public QueryBuilder<T> orderBy(String column) {
        orderByColumns.add(column + " ASC");
        return this;
    }

    public QueryBuilder<T> orderBy(String column, String direction) {
        orderByColumns.add(column + " " + direction.toUpperCase());
        return this;
    }

    public QueryBuilder<T> orderByAsc(String column) {
        return orderBy(column, "ASC");
    }

    public QueryBuilder<T> orderByDesc(String column) {
        return orderBy(column, "DESC");
    }

    // ========== GROUP BY / HAVING ==========

    public QueryBuilder<T> groupBy(String column) {
        this.groupByColumn = column;
        return this;
    }

    public QueryBuilder<T> having(String condition) {
        this.havingClause = condition;
        return this;
    }

    // ========== LIMIT / OFFSET ==========

    public QueryBuilder<T> limit(int limit) {
        this.limitValue = limit;
        return this;
    }

    public QueryBuilder<T> offset(int offset) {
        this.offsetValue = offset;
        return this;
    }

    public QueryBuilder<T> skip(int skip) {
        return offset(skip);
    }

    public QueryBuilder<T> take(int take) {
        return limit(take);
    }

    // ========== EXECUTION ==========

    public List<T> get() throws Exception {
        String sql = buildSelectQuery();
        logger.debug("Executing query: {}", sql);
        logger.debug("Parameters: {}", parameters);

        List<T> results = new ArrayList<>();

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setParameters(pstmt);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        }

        return results;
    }

    public T first() throws Exception {
        limit(1);
        List<T> results = get();
        return results.isEmpty() ? null : results.get(0);
    }

    public T firstOrFail() throws Exception {
        T result = first();
        if (result == null) {
            throw new NoSuchElementException("No entity found for query: " + buildSelectQuery());
        }
        return result;
    }

    public long count() throws Exception {
        String sql = buildCountQuery();
        logger.debug("Executing count query: {}", sql);

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setParameters(pstmt);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;
    }

    public boolean exists() throws Exception {
        return count() > 0;
    }

    // ========== UPDATE ==========

    public int update(Map<String, Object> values) throws Exception {
        String sql = buildUpdateQuery(values);
        logger.debug("Executing update: {}", sql);

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int paramIndex = 1;

            // Set values
            for (Object value : values.values()) {
                pstmt.setObject(paramIndex++, value);
            }

            // Set where parameters
            for (Object param : parameters) {
                pstmt.setObject(paramIndex++, param);
            }

            return pstmt.executeUpdate();
        }
    }

    // ========== DELETE ==========

    public int delete() throws Exception {
        String sql = buildDeleteQuery();
        logger.debug("Executing delete: {}", sql);

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setParameters(pstmt);
            return pstmt.executeUpdate();
        }
    }

    // ========== QUERY BUILDING ==========

    private String buildSelectQuery() {
        StringBuilder sql = new StringBuilder("SELECT ");

        if (distinct) {
            sql.append("DISTINCT ");
        }

        if (selectedColumns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", selectedColumns));
        }

        sql.append(" FROM ").append(tableName);

        // JOINs
        for (JoinClause join : joins) {
            sql.append(" ").append(join.type)
                    .append(" ").append(join.table)
                    .append(" ON ").append(join.condition);
        }

        // WHERE
        if (!whereConditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereConditions));
        }

        // GROUP BY
        if (groupByColumn != null) {
            sql.append(" GROUP BY ").append(groupByColumn);
        }

        // HAVING
        if (havingClause != null) {
            sql.append(" HAVING ").append(havingClause);
        }

        // ORDER BY
        if (!orderByColumns.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", orderByColumns));
        }

        // LIMIT
        if (limitValue != null) {
            sql.append(" LIMIT ").append(limitValue);
        }

        // OFFSET
        if (offsetValue != null) {
            sql.append(" OFFSET ").append(offsetValue);
        }

        return sql.toString();
    }

    private String buildCountQuery() {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ");
        sql.append(tableName);

        if (!whereConditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereConditions));
        }

        return sql.toString();
    }

    private String buildUpdateQuery(Map<String, Object> values) {
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(tableName).append(" SET ");

        List<String> setStatements = new ArrayList<>();
        for (String column : values.keySet()) {
            setStatements.add(column + " = ?");
        }
        sql.append(String.join(", ", setStatements));

        if (!whereConditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereConditions));
        }

        return sql.toString();
    }

    private String buildDeleteQuery() {
        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(tableName);

        if (!whereConditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereConditions));
        }

        return sql.toString();
    }

    // ========== HELPERS ==========

    private void setParameters(PreparedStatement pstmt) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            pstmt.setObject(i + 1, parameters.get(i));
        }
    }

    private T mapResultSetToEntity(ResultSet rs) throws Exception {
        T instance = entityClass.getDeclaredConstructor().newInstance();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(OneToMany.class) ||
                    field.isAnnotationPresent(ManyToMany.class)) {
                continue;
            }

            field.setAccessible(true);

            String columnName;
            if (field.isAnnotationPresent(ManyToOne.class)) {
                JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
                if (joinCol == null) continue;
                columnName = joinCol.name();
            } else {
                Column column = field.getAnnotation(Column.class);
                columnName = (column != null && !column.name().isEmpty())
                        ? column.name()
                        : toSnakeCase(field.getName());
            }

            try {
                Object value = rs.getObject(columnName);
                if (value != null && !rs.wasNull()) {
                    field.set(instance, convertValue(value, field.getType()));
                }
            } catch (SQLException e) {
                // Column might not exist in result set (e.g., when using specific SELECT columns)
                logger.debug("Column '{}' not found in result set", columnName);
            }
        }

        return instance;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;

        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
        }

        if (targetType == Long.class && value instanceof Integer) {
            return ((Integer) value).longValue();
        }

        if (targetType == LocalDateTime.class) {
            if (value instanceof Timestamp) {
                return ((Timestamp) value).toLocalDateTime();
            } else if (value instanceof String) {
                return LocalDateTime.parse((String) value);
            }
        }

        if (targetType == LocalDate.class) {
            if (value instanceof java.sql.Date) {
                return ((java.sql.Date) value).toLocalDate();
            } else if (value instanceof String) {
                return LocalDate.parse((String) value);
            }
        }

        if (targetType == LocalTime.class) {
            if (value instanceof Time) {
                return ((Time) value).toLocalTime();
            } else if (value instanceof String) {
                return LocalTime.parse((String) value);
            }
        }

        return value;
    }

    private String getTableName(Class<?> entityClass) {
        Entity entity = entityClass.getAnnotation(Entity.class);
        if (entity == null) {
            throw new IllegalArgumentException("Class must be annotated with @Entity");
        }
        return entity.table().isEmpty()
                ? toSnakeCase(entityClass.getSimpleName())
                : entity.table();
    }

    private String toSnakeCase(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    // ========== INNER CLASSES ==========

    private static class JoinClause {
        final String type;
        final String table;
        final String condition;

        JoinClause(String type, String table, String condition) {
            this.type = type;
            this.table = table;
            this.condition = condition;
        }
    }

    // ========== DEBUG ==========

    public String toSql() {
        return buildSelectQuery();
    }

    public List<Object> getParameters() {
        return new ArrayList<>(parameters);
    }
}