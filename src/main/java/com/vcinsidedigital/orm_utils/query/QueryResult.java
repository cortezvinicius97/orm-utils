package com.vcinsidedigital.orm_utils.query;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * Representa o resultado de uma query SQL customizada
 */
public class QueryResult {
    private final List<Map<String, Object>> rows;
    private final List<String> columnNames;

    public QueryResult(ResultSet rs) throws SQLException {
        this.rows = new ArrayList<>();
        this.columnNames = new ArrayList<>();

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        // Obter nomes das colunas
        for (int i = 1; i <= columnCount; i++) {
            columnNames.add(metaData.getColumnLabel(i));
        }

        // Processar linhas
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i);
                Object value = rs.getObject(i);
                row.put(columnName, value);
            }
            rows.add(row);
        }
    }

    // Construtor interno para filter()
    private QueryResult(List<Map<String, Object>> rows, List<String> columnNames) {
        this.rows = rows;
        this.columnNames = columnNames;
    }

    /**
     * Retorna todas as linhas do resultado
     */
    public List<Map<String, Object>> getRows() {
        return rows;
    }

    /**
     * Retorna a primeira linha ou null se vazia
     */
    public Map<String, Object> first() {
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Retorna o número de linhas
     */
    public int size() {
        return rows.size();
    }

    /**
     * Verifica se está vazio
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * Retorna os nomes das colunas
     */
    public List<String> getColumnNames() {
        return new ArrayList<>(columnNames);
    }

    /**
     * Obtém um valor específico da primeira linha
     */
    public Object getValue(String columnName) {
        Map<String, Object> firstRow = first();
        return firstRow != null ? firstRow.get(columnName) : null;
    }

    /**
     * Obtém um valor com tipo específico da primeira linha
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(String columnName, Class<T> type) {
        Object value = getValue(columnName);
        if (value == null) return null;

        // Conversão de tipo se necessário
        if (type == Long.class && value instanceof Integer) {
            return (T) Long.valueOf(((Integer) value).longValue());
        }
        if (type == Integer.class && value instanceof Long) {
            return (T) Integer.valueOf(((Long) value).intValue());
        }
        if (type == String.class && !(value instanceof String)) {
            return (T) value.toString();
        }

        return (T) value;
    }

    /**
     * Retorna uma coluna específica de todas as linhas
     */
    public List<Object> getColumn(String columnName) {
        List<Object> column = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            column.add(row.get(columnName));
        }
        return column;
    }

    /**
     * Retorna uma coluna específica com tipo
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getColumn(String columnName, Class<T> type) {
        List<T> column = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(columnName);
            if (value != null) {
                column.add((T) value);
            }
        }
        return column;
    }

    /**
     * Itera sobre as linhas
     */
    public void forEach(java.util.function.Consumer<Map<String, Object>> action) {
        rows.forEach(action);
    }

    /**
     * Mapeia as linhas para outro tipo
     */
    public <R> List<R> map(java.util.function.Function<Map<String, Object>, R> mapper) {
        List<R> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(mapper.apply(row));
        }
        return result;
    }

    /**
     * Filtra as linhas
     */
    public QueryResult filter(java.util.function.Predicate<Map<String, Object>> predicate) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (predicate.test(row)) {
                filtered.add(row);
            }
        }

        return new QueryResult(filtered, columnNames);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("QueryResult[").append(rows.size()).append(" rows]\n");

        if (!rows.isEmpty()) {
            // Cabeçalho
            sb.append(String.join(" | ", columnNames)).append("\n");
            sb.append("-".repeat(50)).append("\n");

            // Linhas (máximo 10)
            int limit = Math.min(10, rows.size());
            for (int i = 0; i < limit; i++) {
                Map<String, Object> row = rows.get(i);
                List<String> values = new ArrayList<>();
                for (String col : columnNames) {
                    values.add(String.valueOf(row.get(col)));
                }
                sb.append(String.join(" | ", values)).append("\n");
            }

            if (rows.size() > 10) {
                sb.append("... (").append(rows.size() - 10).append(" more rows)\n");
            }
        }

        return sb.toString();
    }
}