package org.oldskooler.entity4j.dialect.types;

import org.oldskooler.entity4j.annotations.Column;
import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.mapping.ColumnMeta;
import org.oldskooler.entity4j.mapping.PrimaryKey;
import org.oldskooler.entity4j.mapping.TableMeta;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class PostgresDialect implements SqlDialect {

    @Override
    public String q(String ident) { return "\"" + ident.replace("\"","\"\"") + "\""; }

    @Override
    public boolean supportsCreateIfNotExists() { return true; }

    @Override
    public boolean useInsertReturning() { return true; } // leverage RETURNING for auto keys

    /* =========================
       DDL
       ========================= */

    @Override
    public <T> String createTableDdl(TableMeta<T> m, boolean ifNotExists) {
        List<String> defs = new ArrayList<>();

        // property -> isAuto
        Map<String, Boolean> isAutoProp = new HashMap<>();
        if (m.keys != null && !m.keys.isEmpty()) {
            for (PrimaryKey pk : m.keys.values()) {
                isAutoProp.put(pk.property, pk.auto());
            }
        }

        // Column defs
        for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
            String prop = e.getKey();
            String col  = e.getValue();

            Field f = m.propToField.get(prop);

            boolean nullable = true;
            Column colAnn = f.getAnnotation(Column.class);
            if (colAnn == null) {
                if (m.columns.containsKey(col)) {
                    ColumnMeta meta = m.columns.get(col);
                    nullable = meta.nullable;
                }
            } else {
                nullable = colAnn.nullable();
            }

            String baseType = resolveSqlType(m, f, col);
            boolean auto = Boolean.TRUE.equals(isAutoProp.get(prop));
            String type = auto ? postgresIdentityType(f, baseType) : baseType;

            StringBuilder d = new StringBuilder(q(col)).append(' ').append(type);
            if (!nullable) d.append(" NOT NULL");
            defs.add(d.toString());
        }

        // PRIMARY KEY (supports 0/1/many)
        if (m.keys != null && !m.keys.isEmpty()) {
            List<String> pkCols = new ArrayList<>(m.keys.size());
            for (PrimaryKey pk : m.keys.values()) {
                String prop = pk.property;              // canonical property key
                String col  = m.propToColumn.get(prop);
                if (col == null) {
                    throw new IllegalStateException("Primary key property has no column mapping: " + prop);
                }
                pkCols.add(q(col));
            }
            defs.add("PRIMARY KEY (" + String.join(", ", pkCols) + ")");
        }

        return "CREATE TABLE" + (ifNotExists && supportsCreateIfNotExists() ? " IF NOT EXISTS" : "") + " " + q(m.table) +
                " (\n  " + String.join(",\n  ", defs) + "\n)";
    }

    @Override
    public <T> String dropTableDdl(TableMeta<T> m, boolean ifExists) {
        return "DROP TABLE" + (ifExists ? " IF EXISTS" : "") + " " + q(m.table);
    }

    /**
     * Postgres doesn’t need (or use) a trailing "auto increment" clause on a column;
     * we encode identity directly in the type. Return empty.
     */
    @Override
    public String autoIncrementClause() { return ""; }

    /* =========================
       Type resolution
       ========================= */

    @Override
    public <T> String resolveSqlType(TableMeta<T> m, Field f, String col) {
        Column colAnn = f.getAnnotation(Column.class);

        String user = null;
        int precision = 0;
        int scale = 0;
        int length = -1;

        if (colAnn == null) {
            if (m.columns.containsKey(col)) {
                ColumnMeta meta = m.columns.get(col);
                user = SqlDialect.userTypeOrNull(meta.effectiveType());
                precision = meta.precision;
                scale = meta.scale;
                length = meta.length;
            }
        } else {
            user = SqlDialect.userTypeOrNull(colAnn.type());
            precision = colAnn.precision();
            scale = Math.max(0, colAnn.scale());
            length = colAnn.length();
        }

        if (user != null) {
            if ((user.contains("CHAR") || user.equals("VARCHAR")) && length > 0) return user + "(" + length + ")";
            if ((user.equals("DECIMAL") || user.equals("NUMERIC")) && precision > 0)
                return "NUMERIC(" + precision + "," + scale + ")";
            return user;
        }
        if (precision > 0) return "NUMERIC(" + precision + "," + scale + ")";
        if (length > 0 && f.getType() == String.class) return "VARCHAR(" + length + ")";

        Class<?> t = f.getType();
        if (t == Long.class || t == long.class) return "BIGINT";
        if (t == Integer.class || t == int.class) return "INTEGER";
        if (t == Short.class || t == short.class) return "SMALLINT";
        if (t == Byte.class || t == byte.class) return "SMALLINT";
        if (t == Double.class || t == double.class) return "DOUBLE PRECISION";
        if (t == Float.class || t == float.class) return "REAL";
        if (t == Boolean.class || t == boolean.class) return "BOOLEAN";
        if (t == java.math.BigDecimal.class) return "NUMERIC(38,10)";
        if (t.getName().equals("java.util.UUID")) return "UUID";
        if (t == java.time.LocalDate.class || t == java.sql.Date.class) return "DATE";
        if (t == java.time.LocalDateTime.class || t == java.sql.Timestamp.class) return "TIMESTAMP(6)";
        if (t == java.time.Instant.class) return "TIMESTAMP(6) WITH TIME ZONE";
        return "VARCHAR(255)";
    }

    /* =========================
       INSERT builders
       ========================= */

    @Override
    public <T> String buildInsertSql(TableMeta<T> m, List<String> cols) {
        String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
        String base = "INSERT INTO " + q(m.table) + " (" +
                cols.stream().map(this::q).collect(Collectors.joining(", ")) +
                ") VALUES (" + placeholders + ")";

        // For single-row inserts, IDbContext relies on this method to include RETURNING
        // when useInsertReturning() is true and there are auto keys.
        String returning = insertReturningSuffix(m);
        return returning.isEmpty() ? base : base + returning;
    }

    @Override
    public String insertReturningSuffix(TableMeta<?> m) {
        // Return all AUTO primary key columns, in iteration order of m.keys.values()
        if (m.keys == null || m.keys.isEmpty()) return "";

        List<String> autoCols = new ArrayList<>();
        for (PrimaryKey pk : m.keys.values()) {
            if (!pk.auto()) continue;
            String prop = pk.property;
            String col = m.propToColumn.get(prop);
            if (col == null) {
                throw new IllegalStateException("Primary key property has no column mapping: " + prop);
            }
            autoCols.add(q(col));
        }
        return autoCols.isEmpty() ? "" : " RETURNING " + String.join(", ", autoCols);
    }

    /* =========================
       Helpers
       ========================= */

    /**
     * Choose an appropriate IDENTITY type for Postgres.
     * Uses "GENERATED BY DEFAULT AS IDENTITY" so explicit inserts are still allowed.
     */
    private String postgresIdentityType(Field f, String baseType) {
        Class<?> t = f.getType();
        String bt = baseType.toUpperCase(Locale.ROOT);

        // BIGINT identity
        if (t == long.class || t == Long.class || bt.contains("BIGINT")) {
            return "BIGINT GENERATED BY DEFAULT AS IDENTITY";
        }
        // INTEGER identity (avoid accidental SMALLINT promotion)
        if (t == int.class || t == Integer.class || (bt.contains("INT") && !bt.contains("SMALLINT"))) {
            return "INTEGER GENERATED BY DEFAULT AS IDENTITY";
        }
        if (bt.contains("SMALLINT") || t == short.class || t == Short.class || t == byte.class || t == Byte.class) {
            return "SMALLINT GENERATED BY DEFAULT AS IDENTITY";
        }
        // For non-numeric types, identity is invalid; keep original.
        return baseType;
    }
}
