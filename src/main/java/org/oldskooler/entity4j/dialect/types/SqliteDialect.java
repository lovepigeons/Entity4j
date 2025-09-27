package org.oldskooler.entity4j.dialect.types;

import org.oldskooler.entity4j.annotations.Column;
import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.mapping.ColumnMeta;
import org.oldskooler.entity4j.mapping.PrimaryKey;
import org.oldskooler.entity4j.mapping.TableMeta;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class SqliteDialect implements SqlDialect {

    @Override public String q(String ident) { return "\"" + ident.replace("\"","\"\"") + "\""; }

    // Only valid for the special "INTEGER PRIMARY KEY AUTOINCREMENT" form on the single auto PK column
    @Override public String autoIncrementClause() { return " AUTOINCREMENT"; }

    @Override public boolean supportsCreateIfNotExists() { return true; }

    @Override public boolean useInsertReturning() { return false; }

    @Override
    public <T> String createTableDdl(TableMeta<T> m, boolean ifNotExists) {
        // Quick maps from property -> flags
        Map<String, Boolean> isPkProp   = new HashMap<>();
        Map<String, Boolean> isAutoProp = new HashMap<>();
        if (m.keys != null && !m.keys.isEmpty()) {
            for (PrimaryKey pk : m.keys.values()) {
                isPkProp.put(pk.property, true);
                isAutoProp.put(pk.property, pk.auto());
            }
        }

        // Determine if we have exactly one PK and it's marked auto — and is eligible for SQLite AUTOINCREMENT.
        // Eligibility: we'll emit INTEGER PRIMARY KEY AUTOINCREMENT **only** if the Java type/declared type maps to INTEGER affinity.
        String singlePkProp = null;
        boolean singlePkAuto = false;
        if (m.keys != null && m.keys.size() == 1) {
            PrimaryKey pk = m.keys.values().iterator().next();
            singlePkProp = pk.property;
            singlePkAuto = pk.auto();
        }

        List<String> defs = new ArrayList<>();

        for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
            String prop = e.getKey();
            String col  = e.getValue();
            Field f     = m.propToField.get(prop);

            // nullability
            boolean nullable = true;
            String defaultValue = "";
            boolean ignored = false;

            Column colAnn = f.getAnnotation(Column.class);
            if (colAnn == null) {
                if (m.columns.containsKey(col)) {
                    ColumnMeta meta = m.columns.get(col);
                    nullable = meta.nullable;
                    defaultValue = meta.defaultValue;
                    ignored = meta.ignored;
                }
            } else {
                nullable = colAnn.nullable();
                defaultValue = colAnn.defaultValue();
                ignored = colAnn.ignore();
            }

            if (ignored) {
                continue;
            }

            // Base type inference for SQLite
            String baseType = resolveSqlType(m, f, col);

            // Special case: single auto PK => must be inline "INTEGER PRIMARY KEY AUTOINCREMENT"
            if (singlePkAuto && prop.equals(singlePkProp) && hasIntegerAffinity(baseType, f)) {
                // Force exact "INTEGER" (SQLite AUTOINCREMENT requires this exact type name)
                String def = q(col) + " INTEGER PRIMARY KEY" + autoIncrementClause();
                defs.add(def);
                // Do NOT add NOT NULL here; PRIMARY KEY implies NOT NULL and we keep the exact form
                continue;
            }

            // Normal column
            StringBuilder d = new StringBuilder(q(col)).append(' ').append(baseType);
            if (!nullable) d.append(" NOT NULL");
            if (!Column.DEFAULT_NONE.equals(defaultValue)) {
                d.append(" DEFAULT ").append(defaultValue).append("");
            }
            defs.add(d.toString());
        }

        // Primary key clause:
        // - If we emitted inline INTEGER PRIMARY KEY AUTOINCREMENT above, skip table-level PK.
        // - Otherwise (single non-auto PK OR composite PKs), add table-level PRIMARY KEY (…)
        boolean needTablePk =
                (m.keys != null && !m.keys.isEmpty())
                        && !(m.keys.size() == 1 && singlePkAuto && hasIntegerAffinity(resolveSqlType(m, m.propToField.get(singlePkProp), m.propToColumn.get(singlePkProp)), m.propToField.get(singlePkProp)));

        if (needTablePk) {
            List<String> pkCols = new ArrayList<>(m.keys.size());
            for (PrimaryKey pk : m.keys.values()) {
                String prop = pk.property;
                String col  = m.propToColumn.get(prop);
                if (col == null) {
                    throw new IllegalStateException("Primary key property has no column mapping: " + prop);
                }
                pkCols.add(q(col));
            }
            defs.add("PRIMARY KEY (" + String.join(", ", pkCols) + ")");
        }

        return "CREATE TABLE" + (ifNotExists ? " IF NOT EXISTS" : "") + " " + q(m.table) +
                " (\n  " + String.join(",\n  ", defs) + "\n)";
    }

    @Override
    public <T> String dropTableDdl(TableMeta<T> m, boolean ifExists) {
        return "DROP TABLE" + (ifExists ? " IF EXISTS" : "") + " " + q(m.table);
    }

    @Override
    public <T> String resolveSqlType(TableMeta<T> m, Field f, String col) {
        // SQLite is dynamically typed; we pick pragmatic defaults with correct affinity.
        Column colAnn = f.getAnnotation(Column.class);

        String user = null;
        int precision = 0;
        int length = -1;

        if (colAnn == null) {
            if (m.columns.containsKey(col)) {
                ColumnMeta meta = m.columns.get(col);
                user = SqlDialect.userTypeOrNull(meta.effectiveType());
                precision = meta.precision;
                length = meta.length;
            }
        } else {
            user = SqlDialect.userTypeOrNull(colAnn.type());
            precision = colAnn.precision();
            length = colAnn.length();
        }

        if (user != null) {
            // Normalize common shapes to expected SQLite affinities
            String U = user.toUpperCase(Locale.ROOT);
            if ((U.contains("CHAR") || U.equals("VARCHAR"))) return "TEXT";
            if ((U.equals("DECIMAL") || U.equals("NUMERIC"))) return "NUMERIC";
            if (U.equals("INTEGER") || U.equals("INT") || U.equals("BIGINT") || U.equals("SMALLINT") || U.equals("TINYINT")) return "INTEGER";
            if (U.equals("REAL") || U.equals("FLOAT") || U.equals("DOUBLE") || U.equals("DOUBLE PRECISION")) return "REAL";
            if (U.equals("BOOLEAN") || U.equals("BIT")) return "INTEGER";
            if (U.equals("UUID")) return "TEXT";
            return U; // fallback; SQLite accepts arbitrary type names with affinity rules
        }

        Class<?> t = f.getType();
        if (t == Long.class || t == long.class || t == Integer.class || t == int.class
                || t == Short.class || t == short.class || t == Byte.class || t == byte.class) return "INTEGER";
        if (t == Double.class || t == double.class || t == Float.class || t == float.class) return "REAL";
        if (t == Boolean.class || t == boolean.class) return "INTEGER"; // 0/1
        if (t == java.math.BigDecimal.class) return "NUMERIC";
        if (t.getName().equals("java.util.UUID")) return "TEXT";
        // Dates: store as TEXT (ISO8601) by default
        return "TEXT";
    }

    @Override
    public <T> String buildInsertSql(TableMeta<T> m, List<String> cols) {
        String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
        return "INSERT INTO " + q(m.table) + " (" +
                cols.stream().map(this::q).collect(Collectors.joining(", ")) +
                ") VALUES (" + placeholders + ")";
    }

    /* ---------------- helpers ---------------- */

    private boolean hasIntegerAffinity(String baseType, Field f) {
        // SQLite AUTOINCREMENT requires exact "INTEGER" storage class on the column.
        // We check both the resolved type string and the Java type.
        String bt = baseType == null ? "" : baseType.trim().toUpperCase(Locale.ROOT);
        if (bt.equals("INTEGER")) return true;

        Class<?> t = f.getType();
        return (t == long.class || t == Long.class ||
                t == int.class  || t == Integer.class ||
                t == short.class|| t == Short.class ||
                t == byte.class || t == Byte.class);
    }
}
