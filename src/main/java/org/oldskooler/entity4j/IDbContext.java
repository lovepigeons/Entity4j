package org.oldskooler.entity4j;

import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.dialect.SqlDialectType;
import org.oldskooler.entity4j.mapping.MappingRegistry;
import org.oldskooler.entity4j.mapping.ModelBuilder;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.mapping.PrimaryKey;

import java.lang.reflect.Field;
import java.sql.*;
import java.sql.Date;
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
        this.dialect = detectDialect(connection);
    }

    private static SqlDialect detectDialect(Connection c) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        String url = (md.getURL() == null ? "" : md.getURL()).toLowerCase(Locale.ROOT);
        String product = (md.getDatabaseProductName() == null ? "" : md.getDatabaseProductName()).toLowerCase(Locale.ROOT);

        if (url.startsWith("jdbc:mysql:") || product.contains("mysql") || product.contains("mariadb"))
            return SqlDialectType.MYSQL.createDialect();
        if (url.startsWith("jdbc:postgresql:") || product.contains("postgres"))
            return SqlDialectType.POSTGRESQL.createDialect();
        if (url.startsWith("jdbc:sqlserver:") || product.contains("microsoft sql server"))
            return SqlDialectType.SQLSERVER.createDialect();
        if (url.startsWith("jdbc:sqlite:") || product.contains("sqlite"))
            return SqlDialectType.SQLITE.createDialect();

        throw new IllegalArgumentException("Could not automatically detect sql dialect, please explicitly declare it with: new DbContext(Connection connection, SqlDialectType dialectType)");
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
            Map<String, Object> values = extractValues(entity, m);

            // Build insert column list excluding auto PK properties
            Set<String> autoPkProps = getAutoPkProps(m);
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
            Optional<Map.Entry<String, PrimaryKey>> singleAuto = getSingleAutoPk(m);
            if (dialect.useInsertReturning() && singleAuto.isPresent()) {
                Field idField = m.propToField.get(singleAuto.get().getValue().property); // singleAuto.get().getValue().field();
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    bindParams(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Object id = rs.getObject(1);
                            setField(entity, idField, id);
                            return 1;
                        }
                        return 0;
                    }
                }
            }

            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bindParams(ps, params);
                int n = ps.executeUpdate();
                Optional<Map.Entry<String, PrimaryKey>> singleAutoGk = getSingleAutoPk(m);
                if (singleAutoGk.isPresent()) {
                    Field idField = m.propToField.get(singleAutoGk.get().getValue().property); // singleAutoGk.get().getValue().field();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            Object id = rs.getObject(1);
                            setField(entity, idField, id);
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
            Map<String, Object> values = extractValues(entity, m);

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
                bindParams(ps, params);
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
            Map<String, Object> values = extractValues(entity, m);

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
                bindParams(ps, params);
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
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapRow(rs, m));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("query failed", e);
        }
    }

    List<Map<String, Object>> executeQueryMap(String sql, List<Object> params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                ResultSetMetaData md = rs.getMetaData();
                final int cols = md.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>(cols);
                    for (int i = 1; i <= cols; i++) {
                        String label = md.getColumnLabel(i); // respects SQL aliases
                        Object val = rs.getObject(i);
                        row.put(label, val);
                    }
                    out.add(row);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("query failed", e);
        }
    }

    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object v = params.get(i);
            if (v instanceof LocalDate) {
                LocalDate ld = (LocalDate) v;
                ps.setDate(i + 1, Date.valueOf(ld));
            } else if (v instanceof LocalDateTime) {
                LocalDateTime ldt = (LocalDateTime) v;
                ps.setTimestamp(i + 1, Timestamp.valueOf(ldt));
            } else if (v instanceof Instant) {
                Instant inst = (Instant) v;
                ps.setTimestamp(i + 1, Timestamp.from(inst));
            } else {
                ps.setObject(i + 1, v);
            }
        }
    }

    private static <T> Map<String, Object> extractValues(T entity, TableMeta<T> m) {
        Map<String, Object> values = new LinkedHashMap<>();
        m.propToField.forEach((prop, f) -> values.put(prop, getField(entity, f)));
        return values;
    }

    private static Object getField(Object target, Field f) {
        try {
            f.setAccessible(true);
            return f.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, Field f, Object val) {
        try {
            f.setAccessible(true);
            f.set(target, convert(val, f.getType()));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object convert(Object val, Class<?> targetType) {
        if (val == null) return null;
        if (targetType.isInstance(val)) return val;
        if (targetType == Long.class || targetType == long.class) return ((Number) val).longValue();
        if (targetType == Integer.class || targetType == int.class) return ((Number) val).intValue();
        if (targetType == Double.class || targetType == double.class) return ((Number) val).doubleValue();
        if (targetType == Float.class || targetType == float.class) return ((Number) val).floatValue();
        if (targetType == Short.class || targetType == short.class) return ((Number) val).shortValue();
        if (targetType == Byte.class || targetType == byte.class) return ((Number) val).byteValue();
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (val instanceof Number) return ((Number) val).intValue() != 0;
            if (val instanceof String) return Boolean.parseBoolean((String) val);
        }
        if (targetType == String.class) return String.valueOf(val);
        if (targetType.getName().equals("java.util.UUID")) return java.util.UUID.fromString(String.valueOf(val));
        if (targetType == LocalDate.class && val instanceof java.sql.Date) return ((Date) val).toLocalDate();
        if (targetType == LocalDateTime.class && val instanceof java.sql.Timestamp)
            return ((Timestamp) val).toLocalDateTime();
        return val;
    }

    private <T> T mapRow(ResultSet rs, TableMeta<T> m) throws SQLException {
        try {
            T inst = m.type.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
                String prop = e.getKey();
                String col = e.getValue();
                Object dbVal;
                try {
                    dbVal = rs.getObject(col);
                } catch (SQLException ex) {
                    continue;
                }
                Field f = m.propToField.get(prop);
                setField(inst, f, dbVal);
            }
            return inst;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
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
            Set<String> autoPkProps = getAutoPkProps(m);
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
                String sql = buildMultiRowInsertSql(m, cols, chunk.size());

                // If dialect supports RETURNING and we have exactly one auto PK, keep it
                Optional<Map.Entry<String, PrimaryKey>> singleAuto = getSingleAutoPk(m);
                boolean wantsReturningIds = dialect.useInsertReturning() && singleAuto.isPresent();
                if (wantsReturningIds) {
                    Field idField = m.propToField.get(singleAuto.get().getValue().property);
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        List<Object> params = new ArrayList<>(paramsPerRow * chunk.size());
                        for (T e : chunk) collectInsertParams(e, m, cols, params);
                        bindParams(ps, params);
                        try (ResultSet rs = ps.executeQuery()) {
                            int n = 0;
                            for (T e : chunk) {
                                if (!rs.next()) break;
                                Object id = rs.getObject(1);
                                setField(e, idField, id);
                                n++;
                            }
                            total += n;
                        }
                    }
                } else {
                    // Use generated keys (only assign back if exactly one auto PK)
                    try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        List<Object> params = new ArrayList<>(paramsPerRow * chunk.size());
                        for (T e : chunk) collectInsertParams(e, m, cols, params);
                        bindParams(ps, params);
                        int n = ps.executeUpdate();
                        total += n;

                        Optional<Map.Entry<String, PrimaryKey>> singleAutoGk = getSingleAutoPk(m);
                        if (singleAutoGk.isPresent()) {
                            Field idField = m.propToField.get(singleAutoGk.get().getValue().property); // singleAutoGk.get().getValue().field();
                            try (ResultSet rs = ps.getGeneratedKeys()) {
                                for (T e : chunk) {
                                    if (!rs.next()) break;
                                    Object id = rs.getObject(1);
                                    setField(e, idField, id);
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
                        Map<String, Object> values = extractValues(e, m);

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

                        bindParams(ps, params);
                        ps.addBatch();
                        batched++;
                    }
                    int[] counts = ps.executeBatch();
                    total += sum(counts);
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
                        Object idVal = extractValues(e, m).get(prop);
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

            String group = "(" + String.join(" AND ", buildPkEqualsList(m, pkPropsList)) + ")";
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
                        Map<String, Object> vals = extractValues(e, m);
                        for (String prop : pkPropsList) {
                            Object v = vals.get(prop);
                            if (v == null) throw new IllegalArgumentException("Entity primary key '" + prop + "' is null");
                            params.add(v);
                        }
                    }
                    bindParams(ps, params);
                    total += ps.executeUpdate();
                }
            }
            return total;
        } catch (SQLException ex) {
            throw new RuntimeException("deleteAll failed", ex);
        }
    }

    /* ---------------------------
       Private helpers for batch
       --------------------------- */

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    private <T> String buildMultiRowInsertSql(TableMeta<T> m, List<String> cols, int rows) {
        String colList = String.join(", ", cols.stream().map(dialect::q).toArray(String[]::new));
        String rowPlaceholders = "(" + String.join(", ", Collections.nCopies(cols.size(), "?")) + ")";
        String values = String.join(", ", Collections.nCopies(rows, rowPlaceholders));
        String sql = "INSERT INTO " + dialect.q(m.table) + " (" + colList + ") VALUES " + values;

        // Only add RETURNING if exactly one auto PK
        if (dialect.useInsertReturning() && getSingleAutoPk(m).isPresent()) {
            sql = sql + " " + dialect.insertReturningSuffix(m);
        }
        return sql;
    }

    private <T> void collectInsertParams(T entity, TableMeta<T> m, List<String> cols, List<Object> out) {
        Map<String, Object> vals = extractValues(entity, m);
        // cols are actual column names; map back to property by reverse lookup
        for (String col : cols) {
            String prop = findPropByColumn(m, col);
            out.add(vals.get(prop));
        }
    }

    private <T> String findPropByColumn(TableMeta<T> m, String colName) {
        // colName is unquoted; m.propToColumn values are unquoted; direct match
        for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
            if (e.getValue().equals(colName)) return e.getKey();
        }
        throw new IllegalStateException("Column not found in meta: " + colName);
    }

    private <T> List<String> buildPkEqualsList(TableMeta<T> m, List<String> pkProps) {
        List<String> parts = new ArrayList<>(pkProps.size());
        for (String prop : pkProps) {
            parts.add(dialect.q(m.propToColumn.get(prop)) + " = ?");
        }
        return parts;
    }

    /**
     * PK helpers for new TableMeta.keys model
     */
    private static <T> Optional<Map.Entry<String, PrimaryKey>> getSingleAutoPk(TableMeta<T> m) {
        if (m.keys == null || m.keys.isEmpty()) return Optional.empty();
        Map.Entry<String, PrimaryKey> match = null;
        for (Map.Entry<String, PrimaryKey> e : m.keys.entrySet()) {
            if (e.getValue() != null && e.getValue().auto()) {
                if (match != null) return Optional.empty(); // more than one auto
                match = e;
            }
        }
        return Optional.ofNullable(match);
    }

    private static <T> Set<String> getAutoPkProps(TableMeta<T> m) {
        Set<String> s = new HashSet<>();
        if (m.keys == null) return s;
        for (Map.Entry<String, PrimaryKey> e : m.keys.entrySet()) {
            if (e.getValue() != null && e.getValue().auto()) s.add(e.getKey());
        }
        return s;
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
