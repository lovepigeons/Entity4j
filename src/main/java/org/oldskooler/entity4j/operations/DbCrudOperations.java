package org.oldskooler.entity4j.operations;

import org.oldskooler.entity4j.IDbContext;
import org.oldskooler.entity4j.mapping.PrimaryKey;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.util.JdbcParamBinder;
import org.oldskooler.entity4j.util.PrimaryKeyUtils;
import org.oldskooler.entity4j.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

/**
 * Handles single-entity CRUD operations (Create, Read, Update, Delete)
 */
public class DbCrudOperations {
    private final IDbContext context;

    public DbCrudOperations(IDbContext context) {
        this.context = context;
    }

    public <T> int insert(T entity) {
        context.ensureModelBuiltInternal();
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) entity.getClass();
        TableMeta<T> m = TableMeta.of(type, context.mappingRegistry());

        try {
            Map<String, Object> values = ReflectionUtils.extractValues(entity, m);

            // Build insert column list excluding auto PK properties
            Set<String> autoPkProps = PrimaryKeyUtils.getAutoPkProps(m);
            List<String> cols = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
                String prop = e.getKey();
                if (autoPkProps.contains(prop)) continue;

                // Skip if value is the default for its Java type
                if (isDefaultJavaValue(m.propToField.get(prop), values.get(prop))) {
                    continue;
                }

                cols.add(e.getValue()); // unquoted; dialect will quote
                params.add(values.get(prop));
            }

            String sql = context.dialect().buildInsertSql(m, cols);

            // If the dialect uses "RETURNING id" and we have exactly one auto PK
            Optional<Map.Entry<String, PrimaryKey>> singleAuto = PrimaryKeyUtils.getSingleAutoPk(m);
            if (context.dialect().useInsertReturning() && singleAuto.isPresent()) {
                Field idField = m.propToField.get(singleAuto.get().getValue().property);
                try (PreparedStatement ps = context.conn().prepareStatement(sql)) {
                    JdbcParamBinder.bindParams(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Object id = rs.getObject(1);
                            ReflectionUtils.setField(entity, idField, id);
                            return 1;
                        }
                        return 0;
                    }
                }
            }

            try (PreparedStatement ps = context.conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                JdbcParamBinder.bindParams(ps, params);
                int n = ps.executeUpdate();
                Optional<Map.Entry<String, PrimaryKey>> singleAutoGk = PrimaryKeyUtils.getSingleAutoPk(m);
                if (singleAutoGk.isPresent()) {
                    Field idField = m.propToField.get(singleAutoGk.get().getValue().property);
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            Object id = rs.getObject(1);
                            ReflectionUtils.setField(entity, idField, id);
                        }
                    }
                }
                return n;
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert failed", e);
        }
    }

    private boolean isDefaultJavaValue(Field field, Object value) {
        Class<?> type = field.getType();

        if (value == null) {
            // For primitives, null means we treat it as default
            return true;
        }

        if (type == boolean.class || type == Boolean.class) {
            return Boolean.FALSE.equals(value);
        }
        if (type == byte.class || type == Byte.class) {
            return ((Byte) value) == 0;
        }
        if (type == short.class || type == Short.class) {
            return ((Short) value) == 0;
        }
        if (type == int.class || type == Integer.class) {
            return ((Integer) value) == 0;
        }
        if (type == long.class || type == Long.class) {
            return ((Long) value) == 0L;
        }
        if (type == float.class || type == Float.class) {
            return ((Float) value) == 0f;
        }
        if (type == double.class || type == Double.class) {
            return ((Double) value) == 0d;
        }
        if (type == char.class || type == Character.class) {
            return ((Character) value) == '\u0000'; // default char
        }

        // For String and all other reference types, only null is default.
        return false;
    }

    public <T> int update(T entity) {
        context.ensureModelBuiltInternal();
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entity.getClass();
        TableMeta<T> m = TableMeta.of(t, context.mappingRegistry());
        if (m.keys == null || m.keys.isEmpty()) {
            throw new IllegalStateException("@Id required for update");
        }

        try {
            Map<String, Object> values = ReflectionUtils.extractValues(entity, m);

            // SETs exclude all PK props
            Set<String> pkProps = m.keys.keySet();
            List<String> sets = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
                String prop = e.getKey();
                if (pkProps.contains(prop)) continue;
                sets.add(context.dialect().q(e.getValue()) + " = ?");
                params.add(values.get(prop));
            }

            // WHERE from PK columns
            List<String> where = new ArrayList<>();
            for (String prop : pkProps) {
                String col = m.propToColumn.get(prop);
                where.add(context.dialect().q(col) + " = ?");
            }

            // Append PK values
            for (String prop : pkProps) {
                Object v = values.get(prop);
                if (v == null) {
                    throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                }
                params.add(v);
            }

            String sql = "UPDATE " + context.dialect().q(m.table) + " SET " + String.join(", ", sets)
                    + " WHERE " + String.join(" AND ", where);

            try (PreparedStatement ps = context.conn().prepareStatement(sql)) {
                JdbcParamBinder.bindParams(ps, params);
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("update failed", e);
        }
    }

    public <T> int delete(T entity) {
        context.ensureModelBuiltInternal();
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entity.getClass();
        TableMeta<T> m = TableMeta.of(t, context.mappingRegistry());
        if (m.keys == null || m.keys.isEmpty()) {
            throw new IllegalStateException("@Id required for delete");
        }

        try {
            Map<String, Object> values = ReflectionUtils.extractValues(entity, m);

            List<String> where = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (String prop : m.keys.keySet()) {
                String col = m.propToColumn.get(prop);
                Object v = values.get(prop);
                if (v == null) {
                    throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                }
                where.add(context.dialect().q(col) + " = ?");
                params.add(v);
            }

            String sql = "DELETE FROM " + context.dialect().q(m.table) + " WHERE " + String.join(" AND ", where);
            try (PreparedStatement ps = context.conn().prepareStatement(sql)) {
                JdbcParamBinder.bindParams(ps, params);
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("delete failed", e);
        }
    }
}