package com.vcinsidedigital.orm_utils.core;

import com.vcinsidedigital.orm_utils.annotations.*;
import com.vcinsidedigital.orm_utils.config.ConnectionPool;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

public class EntityManager {
    private static final Logger logger = LoggerFactory.getLogger(EntityManager.class);

    public <T> T persist(T entity) throws Exception {
        Class<?> entityClass = entity.getClass();
        String tableName = getTableName(entityClass);

        // Preencher campos @CreatedDate automaticamente
        fillCreatedDateFields(entity);

        List<String> columnNames = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Verificar se o ID é auto-increment
        Field idField = getIdField(entityClass);
        boolean isAutoIncrement = false;
        if (idField != null) {
            Id id = idField.getAnnotation(Id.class);
            isAutoIncrement = id.autoIncrement() && isNumericType(idField.getType());
        }

        // Se o ID não é auto-increment, incluir no INSERT
        if (idField != null && !isAutoIncrement) {
            idField.setAccessible(true);
            Object idValue = idField.get(entity);
            if (idValue == null) {
                throw new IllegalArgumentException(
                        "ID field must be set manually when autoIncrement is false: " +
                                idField.getName()
                );
            }
            columnNames.add(getColumnName(idField));
            values.add(idValue);
        }

        List<Field> fields = getNonIdFields(entityClass);
        for (Field field : fields) {
            if (field.isAnnotationPresent(OneToMany.class) ||
                    field.isAnnotationPresent(ManyToMany.class)) {
                continue;
            }

            field.setAccessible(true);
            Object value = field.get(entity);

            if (field.isAnnotationPresent(ManyToOne.class)) {
                if (value != null) {
                    JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
                    if (joinCol != null) {
                        columnNames.add(joinCol.name());
                        values.add(getIdValue(value));
                    }
                }
            } else {
                columnNames.add(getColumnName(field));
                values.add(convertToSqlValue(value));
            }
        }

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName,
                String.join(", ", columnNames),
                String.join(", ", Collections.nCopies(columnNames.size(), "?"))
        );

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {

            for (int i = 0; i < values.size(); i++) {
                pstmt.setObject(i + 1, values.get(i));
            }

            pstmt.executeUpdate();

            // Set generated ID apenas se for auto-increment
            if (idField != null && isAutoIncrement) {
                Long generatedId = getGeneratedId(pstmt, conn);
                if (generatedId != null) {
                    idField.setAccessible(true);
                    idField.set(entity, generatedId);
                }
            }

            logger.info("Persisted entity: {} with id: {}",
                    entityClass.getSimpleName(), getIdValue(entity));
        }

        return entity;
    }

    // Método auxiliar para obter ID gerado - compatível com SQLite e MySQL
    private Long getGeneratedId(PreparedStatement stmt, Connection conn) throws SQLException {
        DatabaseConfig.DatabaseType dbType = ConnectionPool.getInstance()
                .getConfig().getType();

        if (dbType == DatabaseConfig.DatabaseType.SQLITE) {
            // SQLite: usar last_insert_rowid()
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return null;
        } else {
            // MySQL: usar getGeneratedKeys()
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return null;
        }
    }

    public <T> T find(Class<T> entityClass, Object id) throws Exception {
        String tableName = getTableName(entityClass);
        Field idField = getIdField(entityClass);

        if (idField == null) {
            throw new IllegalArgumentException("Entity must have @Id field");
        }

        String idColumn = getColumnName(idField);
        String sql = String.format("SELECT * FROM %s WHERE %s = ?",
                tableName, idColumn);

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToEntity(rs, entityClass);
                }
            }
        }

        return null;
    }

    public <T> List<T> findAll(Class<T> entityClass) throws Exception {
        String tableName = getTableName(entityClass);
        String sql = String.format("SELECT * FROM %s", tableName);

        List<T> results = new ArrayList<>();

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                results.add(mapResultSetToEntity(rs, entityClass));
            }
        }

        return results;
    }



    /**
     * Busca entidades filtrando por um campo específico
     * @param entityClass Classe da entidade
     * @param fieldName Nome do campo Java (ex: "name", "email")
     * @param value Valor a ser buscado
     * @return Lista de entidades que correspondem ao filtro
     */
    public <T> List<T> findBy(Class<T> entityClass, String fieldName, Object value) throws Exception {
        String tableName = getTableName(entityClass);

        // Encontrar o campo na classe
        Field field = findFieldByName(entityClass, fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in " + entityClass.getSimpleName());
        }

        String columnName = getColumnName(field);
        String sql = String.format("SELECT * FROM %s WHERE %s = ?", tableName, columnName);

        List<T> results = new ArrayList<>();

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, convertToSqlValue(value));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs, entityClass));
                }
            }
        }

        logger.info("Found {} entities of type {} where {} = {}",
                results.size(), entityClass.getSimpleName(), fieldName, value);

        return results;
    }

    /**
     * Busca entidades com múltiplos filtros (AND)
     * @param entityClass Classe da entidade
     * @param filters Map com nome do campo e valor
     * @return Lista de entidades que correspondem a TODOS os filtros
     */
    public <T> List<T> findBy(Class<T> entityClass, Map<String, Object> filters) throws Exception {
        if (filters == null || filters.isEmpty()) {
            return findAll(entityClass);
        }

        String tableName = getTableName(entityClass);
        List<String> whereConditions = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Field field = findFieldByName(entityClass, entry.getKey());
            if (field == null) {
                throw new IllegalArgumentException("Field '" + entry.getKey() + "' not found");
            }

            String columnName = getColumnName(field);
            whereConditions.add(columnName + " = ?");
            values.add(convertToSqlValue(entry.getValue()));
        }

        String sql = String.format("SELECT * FROM %s WHERE %s",
                tableName,
                String.join(" AND ", whereConditions));

        List<T> results = new ArrayList<>();

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < values.size(); i++) {
                pstmt.setObject(i + 1, values.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs, entityClass));
                }
            }
        }

        logger.info("Found {} entities of type {} with {} filters",
                results.size(), entityClass.getSimpleName(), filters.size());

        return results;
    }

    /**
     * Busca uma única entidade por campo (retorna a primeira encontrada)
     * @param entityClass Classe da entidade
     * @param fieldName Nome do campo Java
     * @param value Valor a ser buscado
     * @return Entidade encontrada ou null
     */
    public <T> T findOneBy(Class<T> entityClass, String fieldName, Object value) throws Exception {
        List<T> results = findBy(entityClass, fieldName, value);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Busca uma única entidade com múltiplos filtros (retorna a primeira encontrada)
     * @param entityClass Classe da entidade
     * @param filters Map com nome do campo e valor
     * @return Entidade encontrada ou null
     */
    public <T> T findOneBy(Class<T> entityClass, Map<String, Object> filters) throws Exception {
        List<T> results = findBy(entityClass, filters);
        return results.isEmpty() ? null : results.get(0);
    }



    /**
     * Busca com LIKE (para textos)
     * @param entityClass Classe da entidade
     * @param fieldName Nome do campo
     * @param pattern Padrão de busca (ex: "%nome%", "joao%", "%@gmail.com")
     * @return Lista de entidades encontradas
     */
    public <T> List<T> findByLike(Class<T> entityClass, String fieldName, String pattern) throws Exception {
        String tableName = getTableName(entityClass);

        Field field = findFieldByName(entityClass, fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found");
        }

        String columnName = getColumnName(field);
        String sql = String.format("SELECT * FROM %s WHERE %s LIKE ?", tableName, columnName);

        List<T> results = new ArrayList<>();

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, pattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs, entityClass));
                }
            }
        }

        return results;
    }

    /**
     * Método auxiliar para encontrar um campo pelo nome
     */
    private Field findFieldByName(Class<?> entityClass, String fieldName) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    public <T> void update(T entity) throws Exception {
        Class<?> entityClass = entity.getClass();
        String tableName = getTableName(entityClass);

        // Preencher campos @UpdatedDate automaticamente
        fillUpdatedDateFields(entity);

        Field idField = getIdField(entityClass);
        if (idField == null) {
            throw new IllegalArgumentException("Entity must have @Id field");
        }

        idField.setAccessible(true);
        Object idValue = idField.get(entity);

        List<Field> fields = getNonIdFields(entityClass);
        List<String> setStatements = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Field field : fields) {
            if (field.isAnnotationPresent(OneToMany.class) ||
                    field.isAnnotationPresent(ManyToMany.class)) {
                continue;
            }

            field.setAccessible(true);
            Object value = field.get(entity);

            if (field.isAnnotationPresent(ManyToOne.class)) {
                if (value != null) {
                    JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
                    if (joinCol != null) {
                        setStatements.add(joinCol.name() + " = ?");
                        values.add(getIdValue(value));
                    }
                }
            } else {
                setStatements.add(getColumnName(field) + " = ?");
                values.add(convertToSqlValue(value));
            }
        }

        values.add(idValue);

        String sql = String.format("UPDATE %s SET %s WHERE %s = ?",
                tableName,
                String.join(", ", setStatements),
                getColumnName(idField)
        );

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < values.size(); i++) {
                pstmt.setObject(i + 1, values.get(i));
            }

            pstmt.executeUpdate();
            logger.info("Updated entity: {} with id: {}",
                    entityClass.getSimpleName(), idValue);
        }
    }

    public <T> void delete(T entity) throws Exception {
        Class<?> entityClass = entity.getClass();
        String tableName = getTableName(entityClass);

        Field idField = getIdField(entityClass);
        if (idField == null) {
            throw new IllegalArgumentException("Entity must have @Id field");
        }

        idField.setAccessible(true);
        Object idValue = idField.get(entity);

        String sql = String.format("DELETE FROM %s WHERE %s = ?",
                tableName, getColumnName(idField));

        try (Connection conn = ConnectionPool.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, idValue);
            pstmt.executeUpdate();

            logger.info("Deleted entity: {} with id: {}",
                    entityClass.getSimpleName(), idValue);
        }
    }

    private <T> T mapResultSetToEntity(ResultSet rs, Class<T> entityClass)
            throws Exception {
        T instance = entityClass.getDeclaredConstructor().newInstance();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(OneToMany.class) ||
                    field.isAnnotationPresent(ManyToMany.class)) {
                continue; // Handle separately if needed
            }

            field.setAccessible(true);

            if (field.isAnnotationPresent(ManyToOne.class)) {
                JoinColumn joinCol = field.getAnnotation(JoinColumn.class);
                if (joinCol != null) {
                    Object fkValue = rs.getObject(joinCol.name());
                    if (fkValue != null && !rs.wasNull()) {
                        // Lazy loading - just store the ID for now
                        // Full implementation would load the related entity
                        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
                        if (manyToOne.fetch() == FetchType.EAGER) {
                            Class<?> targetClass = field.getType();
                            Object related = find(targetClass, fkValue);
                            field.set(instance, related);
                        }
                    }
                }
            } else {
                String columnName = getColumnName(field);
                Object value = rs.getObject(columnName);

                if (value != null && !rs.wasNull()) {
                    field.set(instance, convertValue(value, field.getType()));
                }
            }
        }

        return instance;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;

        // Converter para Boolean
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
        }

        // Converter para Long
        if (targetType == Long.class && value instanceof Integer) {
            return ((Integer) value).longValue();
        }

        // Converter Timestamp para LocalDateTime
        if (targetType == java.time.LocalDateTime.class) {
            if (value instanceof Timestamp) {
                return ((Timestamp) value).toLocalDateTime();
            } else if (value instanceof String) {
                // SQLite retorna como String
                return java.time.LocalDateTime.parse((String) value);
            }
        }

        // Converter Date para LocalDate
        if (targetType == java.time.LocalDate.class) {
            if (value instanceof java.sql.Date) {
                return ((java.sql.Date) value).toLocalDate();
            } else if (value instanceof String) {
                return java.time.LocalDate.parse((String) value);
            }
        }

        // Converter Time para LocalTime
        if (targetType == java.time.LocalTime.class) {
            if (value instanceof Time) {
                return ((Time) value).toLocalTime();
            } else if (value instanceof String) {
                return java.time.LocalTime.parse((String) value);
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

    private String getColumnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        return (column != null && !column.name().isEmpty())
                ? column.name()
                : toSnakeCase(field.getName());
    }

    private Field getIdField(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field;
            }
        }
        return null;
    }

    private List<Field> getNonIdFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<>();
        for (Field field : entityClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Id.class)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private Object getIdValue(Object entity) throws Exception {
        Field idField = getIdField(entity.getClass());
        if (idField == null) return null;
        idField.setAccessible(true);
        return idField.get(entity);
    }

    private String toSnakeCase(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    // Método auxiliar para verificar se o tipo é numérico
    private boolean isNumericType(Class<?> type) {
        return type == Long.class || type == long.class ||
                type == Integer.class || type == int.class ||
                type == Short.class || type == short.class ||
                type == Byte.class || type == byte.class;
    }

    // Preenche campos anotados com @CreatedDate
    private void fillCreatedDateFields(Object entity) throws Exception {
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(CreatedDate.class)) {
                field.setAccessible(true);
                Object currentValue = field.get(entity);

                // Só preenche se estiver null
                if (currentValue == null) {
                    field.set(entity, getCurrentDateForType(field.getType()));
                }
            }
        }
    }

    // Preenche campos anotados com @UpdatedDate
    private void fillUpdatedDateFields(Object entity) throws Exception {
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(UpdatedDate.class)) {
                field.setAccessible(true);
                field.set(entity, getCurrentDateForType(field.getType()));
            }
        }
    }

    // Retorna a data atual no tipo apropriado
    private Object getCurrentDateForType(Class<?> type) {
        if (type == java.time.LocalDateTime.class) {
            return java.time.LocalDateTime.now();
        } else if (type == java.time.LocalDate.class) {
            return java.time.LocalDate.now();
        } else if (type == java.time.LocalTime.class) {
            return java.time.LocalTime.now();
        } else if (type == Timestamp.class) {
            return new Timestamp(System.currentTimeMillis());
        } else if (type == java.sql.Date.class) {
            return new java.sql.Date(System.currentTimeMillis());
        } else if (type == java.util.Date.class) {
            return new java.util.Date();
        } else if (type == Long.class || type == long.class) {
            return System.currentTimeMillis();
        }
        return null;
    }

    // Converte valores Java para SQL (especialmente LocalDateTime)
    private Object convertToSqlValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof java.time.LocalDateTime) {
            // Converter LocalDateTime para Timestamp para MySQL
            return Timestamp.valueOf((java.time.LocalDateTime) value);
        } else if (value instanceof java.time.LocalDate) {
            return java.sql.Date.valueOf((java.time.LocalDate) value);
        } else if (value instanceof java.time.LocalTime) {
            return Time.valueOf((java.time.LocalTime) value);
        }

        return value;
    }
}