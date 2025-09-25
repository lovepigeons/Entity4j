package org.oldskooler.entity4j.operations;

import org.oldskooler.entity4j.IDbContext;
import org.oldskooler.entity4j.mapping.PrimaryKey;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.util.BatchSqlUtils;
import org.oldskooler.entity4j.util.JdbcParamBinder;
import org.oldskooler.entity4j.util.PrimaryKeyUtils;
import org.oldskooler.entity4j.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

/**
 * Handles batch CRUD operations for multiple entities
 */
public class DbBatchOperations {
    private final IDbContext context;
    private static final int MAX_PARAMS_PER_STATEMENT = 1800; // keeps under SQL Server's 2100 limit with some headroom

    public DbBatchOperations(IDbContext context) {
        this.context = context;
    }

    public <T> int insertAll(Collection<T> entities) throws SQLException, IllegalAccessException {
        if (entities == null || entities.isEmpty()) return 0;

        context.ensureModelBuiltInternal();
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entities.iterator().next().getClass();
        TableMeta<T> m = TableMeta.of(t, context.mappingRegistry());

        // Column order (excluding auto PK props)
        Set<String> autoPkProps = PrimaryKeyUtils.getAutoPkProps(m);
        List<String> cols = new ArrayList<>();
        for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
            if (autoPkProps.contains(e.getKey())) continue;
            cols.add(e.getValue());
        }

        // Prepare chunks to keep params under limits
        int paramsPerRow = cols.size();
        if (paramsPerRow == 0) throw new IllegalStateException("No columns to insert");
        int maxRowsPerStmt = Math.max(1, MAX_PARAMS_PER_STATEMENT / paramsPerRow);

        int total = 0;
        Iterator<T> it = entities.iterator();
        while (it.hasNext()) {
            List<T> chunk = new ArrayList<>(Math.min(maxRowsPerStmt, entities.size()));
            for (int i = 0; i < maxRowsPerStmt && it.hasNext(); i++) {
                chunk.add(it.next());
            }

            // Build single SQL: INSERT INTO t (c1,c2) VALUES (?,?),(?,?)...
            String sql = BatchSqlUtils.buildMultiRowInsertSql(context.dialect(), m, cols, chunk.size());

            // If dialect supports RETURNING and we have exactly one auto PK, keep it
            Optional<Map.Entry<String, PrimaryKey>> singleAuto = PrimaryKeyUtils.getSingleAutoPk(m);
            boolean wantsReturningIds = context.dialect().useInsertReturning() && singleAuto.isPresent();

            if (wantsReturningIds) {
                Field idField = m.propToField.get(singleAuto.get().getValue().property);
                try (PreparedStatement ps = context.conn().prepareStatement(sql)) {
                    List<Object> params = new ArrayList<>(paramsPerRow * chunk.size());
                    for (T e : chunk) {
                        BatchSqlUtils.collectInsertParams(e, m, cols, params);
                    }
                    JdbcParamBinder.bindParams(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        int n = 0;
                        for (T e : chunk) {
                            if (!rs.next()) break;
                            Object id = rs.getObject(1);
                            ReflectionUtils.setField(e, idField, id);
                            n++;
                        }
                        total += n;
                    }
                }
            } else {
                // Use generated keys (only assign back if exactly one auto PK)
                try (PreparedStatement ps = context.conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    List<Object> params = new ArrayList<>(paramsPerRow * chunk.size());
                    for (T e : chunk) {
                        BatchSqlUtils.collectInsertParams(e, m, cols, params);
                    }
                    JdbcParamBinder.bindParams(ps, params);
                    int n = ps.executeUpdate();
                    total += n;

                    Optional<Map.Entry<String, PrimaryKey>> singleAutoGk = PrimaryKeyUtils.getSingleAutoPk(m);
                    if (singleAutoGk.isPresent()) {
                        Field idField = m.propToField.get(singleAutoGk.get().getValue().property);
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            for (T e : chunk) {
                                if (!rs.next()) break;
                                Object id = rs.getObject(1);
                                ReflectionUtils.setField(e, idField, id);
                            }
                        }
                    }
                }
            }
        }
        return total;

    }

    public <T> int updateAll(Collection<T> entities) throws SQLException, IllegalAccessException {
        if (entities == null || entities.isEmpty()) return 0;

        context.ensureModelBuiltInternal();
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entities.iterator().next().getClass();
        TableMeta<T> m = TableMeta.of(t, context.mappingRegistry());
        if (m.keys == null || m.keys.isEmpty()) {
            throw new IllegalStateException("@Id required for batch update");
        }

        // Build SQL template
        // SET c1=?,c2=?,... WHERE pk1=? AND pk2=? ...
        Set<String> pkProps = m.keys.keySet();
        List<String> sets = new ArrayList<>();
        for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
            String prop = e.getKey();
            if (pkProps.contains(prop)) continue;
            sets.add(context.dialect().q(e.getValue()) + " = ?");
        }
        List<String> where = new ArrayList<>();
        for (String prop : pkProps) {
            where.add(context.dialect().q(m.propToColumn.get(prop)) + " = ?");
        }
        String sql = "UPDATE " + context.dialect().q(m.table) + " SET " + String.join(", ", sets) +
                " WHERE " + String.join(" AND ", where);

        int paramsPerRow = sets.size() + pkProps.size();
        int maxRowsPerStmt = Math.max(1, MAX_PARAMS_PER_STATEMENT / paramsPerRow);

        int total = 0;
        Iterator<T> it = entities.iterator();
        while (it.hasNext()) {
            try (PreparedStatement ps = context.conn().prepareStatement(sql)) {
                int batched = 0;
                while (batched < maxRowsPerStmt && it.hasNext()) {
                    T e = it.next();
                    Map<String, Object> values = ReflectionUtils.extractValues(e, m);

                    List<Object> params = new ArrayList<>(paramsPerRow);
                    for (Map.Entry<String, String> col : m.propToColumn.entrySet()) {
                        String prop = col.getKey();
                        if (pkProps.contains(prop)) continue;
                        params.add(values.get(prop));
                    }
                    for (String prop : pkProps) {
                        Object idVal = values.get(prop);
                        if (idVal == null) {
                            throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                        }
                        params.add(idVal);
                    }

                    JdbcParamBinder.bindParams(ps, params);
                    ps.addBatch();
                    batched++;
                }
                int[] counts = ps.executeBatch();
                total += BatchSqlUtils.sum(counts);
            }
        }
        return total;
    }

    public <T> int deleteAll(Collection<T> entities) throws SQLException, IllegalAccessException {
        if (entities == null || entities.isEmpty()) return 0;

        context.ensureModelBuiltInternal();
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entities.iterator().next().getClass();
        TableMeta<T> m = TableMeta.of(t, context.mappingRegistry());
        if (m.keys == null || m.keys.isEmpty()) {
            throw new IllegalStateException("@Id required for batch delete");
        }

        List<String> pkPropsList = new ArrayList<>(m.keys.keySet());

        // Single-column PK: fast path with IN (...)
        if (pkPropsList.size() == 1) {
            return deleteBySinglePrimaryKey(entities, m, pkPropsList.get(0));
        }

        // Composite PK: build OR of (k1=? AND k2=? ...) groups, chunked
        return deleteByCompositePrimaryKey(entities, m, pkPropsList);
    }

    private <T> int deleteBySinglePrimaryKey(Collection<T> entities, TableMeta<T> m, String prop) throws SQLException, IllegalAccessException {
        String col = m.propToColumn.get(prop);
        String baseSql = "DELETE FROM " + context.dialect().q(m.table) + " WHERE " + context.dialect().q(col) + " IN ";

        int maxIdsPerStmt = Math.max(1, MAX_PARAMS_PER_STATEMENT);
        int total = 0;
        Iterator<T> it = entities.iterator();

        while (it.hasNext()) {
            List<Object> ids = new ArrayList<>(Math.min(maxIdsPerStmt, entities.size()));
            while (ids.size() < maxIdsPerStmt && it.hasNext()) {
                T e = it.next();
                Object idVal = ReflectionUtils.extractValues(e, m).get(prop);
                if (idVal == null) {
                    throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                }
                ids.add(idVal);
            }

            String placeholders = "(" + String.join(", ", Collections.nCopies(ids.size(), "?")) + ")";
            String sql = baseSql + placeholders;

            try (PreparedStatement ps = context.conn().prepareStatement(sql)) {
                for (int i = 0; i < ids.size(); i++) {
                    ps.setObject(i + 1, ids.get(i));
                }
                total += ps.executeUpdate();
            }
        }
        return total;
    }

    private <T> int deleteByCompositePrimaryKey(Collection<T> entities, TableMeta<T> m, List<String> pkPropsList) throws SQLException, IllegalAccessException {
        int pkArity = pkPropsList.size();
        int paramsPerRow = pkArity;
        int maxGroupsPerStmt = Math.max(1, MAX_PARAMS_PER_STATEMENT / paramsPerRow);

        String group = "(" + String.join(" AND ", BatchSqlUtils.buildPkEqualsList(context.dialect(), m, pkPropsList)) + ")";
        String base = "DELETE FROM " + context.dialect().q(m.table) + " WHERE ";

        int total = 0;
        Iterator<T> it = entities.iterator();

        while (it.hasNext()) {
            List<T> chunk = new ArrayList<>(Math.min(maxGroupsPerStmt, entities.size()));
            for (int i = 0; i < maxGroupsPerStmt && it.hasNext(); i++) {
                chunk.add(it.next());
            }

            String sql = base + String.join(" OR ", Collections.nCopies(chunk.size(), group));
            try (PreparedStatement ps = context.conn().prepareStatement(sql)) {
                List<Object> params = new ArrayList<>(chunk.size() * pkArity);
                for (T e : chunk) {
                    Map<String, Object> vals = ReflectionUtils.extractValues(e, m);
                    for (String prop : pkPropsList) {
                        Object v = vals.get(prop);
                        if (v == null) {
                            throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                        }
                        params.add(v);
                    }
                }
                JdbcParamBinder.bindParams(ps, params);
                total += ps.executeUpdate();
            }
        }
        return total;
    }
}