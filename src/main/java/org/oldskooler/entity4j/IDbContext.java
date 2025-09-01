package org.oldskooler.entity4j;

import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.dialect.SqlDialectType;
import org.oldskooler.entity4j.mapping.MappingRegistry;
import org.oldskooler.entity4j.mapping.ModelBuilder;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.mapping.PrimaryKey;
import org.oldskooler.entity4j.util.*;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public abstract class IDbContext implements AutoCloseable {
    private final Connection connection;
    private final SqlDialect dialect;

    private final MappingRegistry mappingRegistry = new MappingRegistry();
    private boolean modelBuilt = false;

    private static final int MAX_PARAMS_PER_STATEMENT = 1800; // keeps under SQL Server's 2100 limit with some headroom

    /**
     * Explicit dialect via enum
     */
    public IDbContext(Connection connection, SqlDialectType dialectType) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.dialect = dialectType.createDialect();
    }

    /**
     * Explicit dialect instance
     */
    public IDbContext(Connection connection, SqlDialect dialect) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    /**
     * Auto-detect from JDBC metadata
     */
    public IDbContext(Connection connection) throws SQLException {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.dialect = DialectDetector.detectDialect(connection);
    }

    public abstract void onModelCreating(ModelBuilder model);

    private void ensureModelBuilt() {
        if (!modelBuilt) {
            onModelCreating(new ModelBuilder(mappingRegistry));
            modelBuilt = true;
        }
    }

    public <T> Query<T> from(Class<T> type) {
        return new Query<>(this, TableMeta.of(type, mappingRegistry));
    }

    /* ---------------------------
       Public SQL builder APIs
       --------------------------- */

    public <T> String createTableSql(Class<T> type) {
        return createTableSql(type, true);
    }

    public <T> String createTableSql(Class<T> type, boolean ifNotExists) {
        return ddlFor(type, ifNotExists);
    }

    public <T> String dropTableSql(Class<T> type) {
        return dropTableSql(type, true);
    }

    public <T> String dropTableSql(Class<T> type, boolean ifExists) {
        ensureModelBuilt();
        TableMeta<T> m = TableMeta.of(type, mappingRegistry);
        return dialect.dropTableDdl(m, ifExists && dialect.supportsDropIfExists());
    }

    public <T> int createTable(Class<T> type) {
        ensureModelBuilt();
        String sql = createTableSql(type, true);
        try (Statement st = connection.createStatement()) {
            return st.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("createTable failed for " + type.getName() + ": " + sql, e);
        }
    }

    public int createTables(Class<?>... types) {
        int total = 0;
        for (Class<?> t : types) total += createTable(t);
        return total;
    }

    public <T> int dropTableIfExists(Class<T> type) {
        ensureModelBuilt();
        String sql = dropTableSql(type, true);
        try (Statement st = connection.createStatement()) {
            return st.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("dropTableIfExists failed for " + type.getName() + ": " + sql, e);
        }
    }

    /**
     * Build CREATE TABLE statement via dialect.
     */
    private <T> String ddlFor(Class<T> type, boolean ifNotExists) {
        ensureModelBuilt();
        TableMeta<T> m = TableMeta.of(type, mappingRegistry);
        boolean useIfNotExists = ifNotExists && dialect.supportsCreateIfNotExists();
        return dialect.createTableDdl(m, useIfNotExists);
    }

    /* ---------------------------
       CRUD
       --------------------------- */

    public <T> int insert(T entity) {
        ensureModelBuilt();
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) entity.getClass();
        TableMeta<T> m = TableMeta.of(type, mappingRegistry);
        try {
            Map<String, Object> values = ReflectionUtils.extractValues(entity, m);

            // Build insert column list excluding auto PK properties
            Set<String> autoPkProps = PrimaryKeyUtils.getAutoPkProps(m);
            List<String> cols = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
                String prop = e.getKey();
                if (autoPkProps.contains(prop)) continue;
                cols.add(e.getValue()); // unquoted; dialect will quote
                params.add(values.get(prop));
            }

            String sql = dialect.buildInsertSql(m, cols);

            // If the dialect uses "RETURNING id" and we have exactly one auto PK
            Optional<Map.Entry<String, PrimaryKey>> singleAuto = PrimaryKeyUtils.getSingleAutoPk(m);
            if (dialect.useInsertReturning() && singleAuto.isPresent()) {
                Field idField = m.propToField.get(singleAuto.get().getValue().property); // singleAuto.get().getValue().field();
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
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

            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                JdbcParamBinder.bindParams(ps, params);
                int n = ps.executeUpdate();
                Optional<Map.Entry<String, PrimaryKey>> singleAutoGk = PrimaryKeyUtils.getSingleAutoPk(m);
                if (singleAutoGk.isPresent()) {
                    Field idField = m.propToField.get(singleAutoGk.get().getValue().property); // singleAutoGk.get().getValue().field();
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

    public <T> int update(T entity) {
        ensureModelBuilt();
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entity.getClass();
        TableMeta<T> m = TableMeta.of(t, mappingRegistry);
        if (m.keys == null || m.keys.isEmpty()) throw new IllegalStateException("@Id required for update");

        try {
            Map<String, Object> values = ReflectionUtils.extractValues(entity, m);

            // SETs exclude all PK props
            Set<String> pkProps = m.keys.keySet();
            List<String> sets = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
                String prop = e.getKey();
                if (pkProps.contains(prop)) continue;
                sets.add(dialect.q(e.getValue()) + " = ?");
                params.add(values.get(prop));
            }

            // WHERE from PK columns
            List<String> where = new ArrayList<>();
            for (String prop : pkProps) {
                String col = m.propToColumn.get(prop);
                where.add(dialect.q(col) + " = ?");
            }

            // Append PK values
            for (String prop : pkProps) {
                Object v = values.get(prop);
                if (v == null) throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                params.add(v);
            }

            String sql = "UPDATE " + dialect.q(m.table) + " SET " + String.join(", ", sets)
                    + " WHERE " + String.join(" AND ", where);

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                JdbcParamBinder.bindParams(ps, params);
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("update failed", e);
        }
    }

    public <T> int delete(T entity) {
        ensureModelBuilt();
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entity.getClass();
        TableMeta<T> m = TableMeta.of(t, mappingRegistry);
        if (m.keys == null || m.keys.isEmpty()) throw new IllegalStateException("@Id required for delete");
        try {
            Map<String, Object> values = ReflectionUtils.extractValues(entity, m);

            List<String> where = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (String prop : m.keys.keySet()) {
                String col = m.propToColumn.get(prop);
                Object v = values.get(prop);
                if (v == null) throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                where.add(dialect.q(col) + " = ?");
                params.add(v);
            }

            String sql = "DELETE FROM " + dialect.q(m.table) + " WHERE " + String.join(" AND ", where);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                JdbcParamBinder.bindParams(ps, params);
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("delete failed", e);
        }
    }

    @Override
    public void close() throws RuntimeException {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /* ---- helpers ---- */
    <T> List<T> executeQuery(TableMeta<T> m, String sql, List<Object> params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            JdbcParamBinder.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return RowMapper.mapAll(rs, m);
            }
        } catch (SQLException e) {
            throw new RuntimeException("query failed", e);
        }
    }

    List<Map<String, Object>> executeQueryMap(String sql, List<Object> params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            JdbcParamBinder.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return RowMapper.toMapList(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("query failed", e);
        }
    }

    /* ---------------------------
       Batch CRUD
       --------------------------- */

    public <T> int insertAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entities.iterator().next().getClass();
        TableMeta<T> m = TableMeta.of(t, mappingRegistry);

        try {
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
                for (int i = 0; i < maxRowsPerStmt && it.hasNext(); i++) chunk.add(it.next());

                // Build single SQL: INSERT INTO t (c1,c2) VALUES (?,?),(?,?)...
                String sql = BatchSqlUtils.buildMultiRowInsertSql(dialect, m, cols, chunk.size());

                // If dialect supports RETURNING and we have exactly one auto PK, keep it
                Optional<Map.Entry<String, PrimaryKey>> singleAuto = PrimaryKeyUtils.getSingleAutoPk(m);
                boolean wantsReturningIds = dialect.useInsertReturning() && singleAuto.isPresent();
                if (wantsReturningIds) {
                    Field idField = m.propToField.get(singleAuto.get().getValue().property);
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        List<Object> params = new ArrayList<>(paramsPerRow * chunk.size());
                        for (T e : chunk) BatchSqlUtils.collectInsertParams(e, m, cols, params);
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
                    try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        List<Object> params = new ArrayList<>(paramsPerRow * chunk.size());
                        for (T e : chunk) BatchSqlUtils.collectInsertParams(e, m, cols, params);
                        JdbcParamBinder.bindParams(ps, params);
                        int n = ps.executeUpdate();
                        total += n;

                        Optional<Map.Entry<String, PrimaryKey>> singleAutoGk = PrimaryKeyUtils.getSingleAutoPk(m);
                        if (singleAutoGk.isPresent()) {
                            Field idField = m.propToField.get(singleAutoGk.get().getValue().property); // singleAutoGk.get().getValue().field();
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
        } catch (SQLException ex) {
            throw new RuntimeException("insertAll failed", ex);
        }
    }

    public <T> int updateAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entities.iterator().next().getClass();
        TableMeta<T> m = TableMeta.of(t, mappingRegistry);
        if (m.keys == null || m.keys.isEmpty()) throw new IllegalStateException("@Id required for batch update");

        try {
            // Build SQL template
            // SET c1=?,c2=?,... WHERE pk1=? AND pk2=? ...
            Set<String> pkProps = m.keys.keySet();
            List<String> sets = new ArrayList<>();
            for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
                String prop = e.getKey();
                if (pkProps.contains(prop)) continue;
                sets.add(dialect.q(e.getValue()) + " = ?");
            }
            List<String> where = new ArrayList<>();
            for (String prop : pkProps) {
                where.add(dialect.q(m.propToColumn.get(prop)) + " = ?");
            }
            String sql = "UPDATE " + dialect.q(m.table) + " SET " + String.join(", ", sets) +
                    " WHERE " + String.join(" AND ", where);

            int paramsPerRow = sets.size() + pkProps.size();
            int maxRowsPerStmt = Math.max(1, MAX_PARAMS_PER_STATEMENT / paramsPerRow);

            int total = 0;
            Iterator<T> it = entities.iterator();
            while (it.hasNext()) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
                            if (idVal == null) throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
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
        } catch (SQLException ex) {
            throw new RuntimeException("updateAll failed", ex);
        }
    }

    public <T> int deleteAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) entities.iterator().next().getClass();
        TableMeta<T> m = TableMeta.of(t, mappingRegistry);
        if (m.keys == null || m.keys.isEmpty()) throw new IllegalStateException("@Id required for batch delete");

        try {
            List<String> pkPropsList = new ArrayList<>(m.keys.keySet());

            // Single-column PK: fast path with IN (...)
            if (pkPropsList.size() == 1) {
                String prop = pkPropsList.get(0);
                String col = m.propToColumn.get(prop);
                String baseSql = "DELETE FROM " + dialect.q(m.table) + " WHERE " + dialect.q(col) + " IN ";

                int maxIdsPerStmt = Math.max(1, MAX_PARAMS_PER_STATEMENT);
                int total = 0;
                Iterator<T> it = entities.iterator();
                while (it.hasNext()) {
                    List<Object> ids = new ArrayList<>(Math.min(maxIdsPerStmt, entities.size()));
                    while (ids.size() < maxIdsPerStmt && it.hasNext()) {
                        T e = it.next();
                        Object idVal = ReflectionUtils.extractValues(e, m).get(prop);
                        if (idVal == null) throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                        ids.add(idVal);
                    }

                    String placeholders = "(" + String.join(", ", Collections.nCopies(ids.size(), "?")) + ")";
                    String sql = baseSql + placeholders;

                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        for (int i = 0; i < ids.size(); i++) ps.setObject(i + 1, ids.get(i));
                        total += ps.executeUpdate();
                    }
                }
                return total;
            }

            // Composite PK: build OR of (k1=? AND k2=? ...) groups, chunked
            int pkArity = pkPropsList.size();
            int paramsPerRow = pkArity;
            int maxGroupsPerStmt = Math.max(1, MAX_PARAMS_PER_STATEMENT / paramsPerRow);

            String group = "(" + String.join(" AND ", BatchSqlUtils.buildPkEqualsList(dialect, m, pkPropsList)) + ")";
            String base = "DELETE FROM " + dialect.q(m.table) + " WHERE ";

            int total = 0;
            Iterator<T> it = entities.iterator();
            while (it.hasNext()) {
                List<T> chunk = new ArrayList<>(Math.min(maxGroupsPerStmt, entities.size()));
                for (int i = 0; i < maxGroupsPerStmt && it.hasNext(); i++) chunk.add(it.next());

                String sql = base + String.join(" OR ", Collections.nCopies(chunk.size(), group));
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    List<Object> params = new ArrayList<>(chunk.size() * pkArity);
                    for (T e : chunk) {
                        Map<String, Object> vals = ReflectionUtils.extractValues(e, m);
                        for (String prop : pkPropsList) {
                            Object v = vals.get(prop);
                            if (v == null) throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                            params.add(v);
                        }
                    }
                    JdbcParamBinder.bindParams(ps, params);
                    total += ps.executeUpdate();
                }
            }
            return total;
        } catch (SQLException ex) {
            throw new RuntimeException("deleteAll failed", ex);
        }
    }

    /**
     * Dialect-quoted identifier utility for Query et al.
     */
    String q(String ident) {
        return dialect.q(ident);
    } /* package-private access for Query */

    Connection conn() {
        return connection;
    }

    SqlDialect dialect() {
        return dialect;
    }

    MappingRegistry mappingRegistry() {
        return mappingRegistry;
    }
}