package org.oldskooler.entity4j.dialect.types;

import org.oldskooler.entity4j.annotations.Column;
import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.mapping.ColumnMeta;
import org.oldskooler.entity4j.mapping.PrimaryKey;
import org.oldskooler.entity4j.mapping.TableMeta;

import java.lang.reflect.Field;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class MySqlDialect implements SqlDialect {
    @Override public String q(String ident) { return "`" + ident.replace("`","``") + "`"; }
    @Override public String autoIncrementClause() { return " AUTO_INCREMENT"; }

    @Override
    public <T> String createTableDdl(TableMeta<T> m, boolean ifNotExists) {
        List<String> defs = new ArrayList<>();

        // Build a quick lookup: prop -> (isPk, isAuto)
        Map<String, Boolean> isPkProp   = new HashMap<>();
        Map<String, Boolean> isAutoProp = new HashMap<>();
        if (m.keys != null) {
            m.keys.values().forEach(pk -> {
                String prop = m.propToField.get(pk.property).getName();
                isPkProp.put(prop, true);
                isAutoProp.put(prop, pk.auto());
            });
        }

        // Column definitions
        for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
            String prop = e.getKey();
            String col  = e.getValue();

            Field f = m.propToField.get(prop);

            boolean nullable = true;
            String defaultValue = "";

            Column colAnn = f.getAnnotation(Column.class);
            if (colAnn == null) {
                if (m.columns.containsKey(col)) {
                    ColumnMeta meta = m.columns.get(col);
                    nullable = meta.nullable;
                    defaultValue = meta.value;
                }
            } else {
                nullable = colAnn.nullable();
                defaultValue = colAnn.defaultValue();
            }

            String type = resolveSqlType(m, f, col);
            boolean auto = Boolean.TRUE.equals(isAutoProp.get(prop));

            StringBuilder d = new StringBuilder(q(col)).append(' ').append(type);
            if (auto) d.append(autoIncrementClause()); // per-dialect auto/identity
            if (!nullable) d.append(" NOT NULL");
            if (!Column.DEFAULT_NONE.equals(defaultValue)) {
                d.append(" DEFAULT ").append(defaultValue).append("");
            }
            defs.add(d.toString());
        }

        // PRIMARY KEY (...) — supports 0/1/many keys
        if (!m.keys.isEmpty()) {
            // Keep deterministic order: iterate over m.keys.values()
            List<String> pkCols = new ArrayList<>(m.keys.size());
            for (PrimaryKey pk : m.keys.values()) {
                String prop = pk.property; // pk.getField().getName();
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
            if (user.contains("CHAR") || user.equals("VARCHAR")) {
                return (length > 0) ? user + "(" + length + ")" : user;
            }
            if ((user.equals("DECIMAL") || user.equals("NUMERIC")) && precision > 0) {
                return "DECIMAL(" + precision + "," + scale + ")";
            }
            return user; // DATETIME(6), BIGINT, etc.
        }
        if (precision > 0) return "DECIMAL(" + precision + "," + scale + ")";
        if (length > 0 && f.getType() == String.class) return "VARCHAR(" + length + ")";

        Class<?> t = f.getType();
        if (t == Long.class || t == long.class) return "BIGINT";
        if (t == Integer.class || t == int.class) return "INT";
        if (t == Short.class || t == short.class) return "SMALLINT";
        if (t == Byte.class || t == byte.class) return "TINYINT";
        if (t == Double.class || t == double.class) return "DOUBLE";
        if (t == Float.class || t == float.class) return "FLOAT";
        if (t == Boolean.class || t == boolean.class) return "TINYINT(1)";
        if (t == java.math.BigDecimal.class) return "DECIMAL(38,10)";
        if (t.getName().equals("java.util.UUID")) return "CHAR(36)";
        if (t == LocalDate.class || t == Date.class) return "DATE";
        if (t == LocalDateTime.class || t == Timestamp.class) return "DATETIME(6)";
        if (t == Instant.class) return "DATETIME(6)";
        return "VARCHAR(255)";
    }

    @Override
    public <T> String buildInsertSql(TableMeta<T> m, List<String> cols) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(cols.size(), "?"));
        return "INSERT INTO " + q(m.table) + " (" + String.join(", ", quoted(cols)) + ") VALUES (" + placeholders + ")";
    }

    private List<String> quoted(List<String> cols) {
        List<String> out = new ArrayList<>(cols.size());
        for (String c : cols) out.add(q(c));
        return out;
    }
}
