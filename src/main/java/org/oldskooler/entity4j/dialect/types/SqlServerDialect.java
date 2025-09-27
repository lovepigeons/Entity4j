package org.oldskooler.entity4j.dialect.types;

import org.oldskooler.entity4j.annotations.Column;
import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.mapping.ColumnMeta;
import org.oldskooler.entity4j.mapping.PrimaryKey;
import org.oldskooler.entity4j.mapping.TableMeta;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class SqlServerDialect implements SqlDialect {

    @Override
    public String q(String ident) { return "[" + ident.replace("]", "]]") + "]"; }

    // SQL Server does NOT support CREATE TABLE IF NOT EXISTS (without an IF wrapper),
    // so the builder should ignore IF NOT EXISTS text. Keep this false.
    @Override
    public boolean supportsCreateIfNotExists() { return false; }

    // We'll use JDBC getGeneratedKeys() instead of OUTPUT INSERTED...
    @Override
    public boolean useInsertReturning() { return false; }

    /* =========================
       DDL
       ========================= */

    @Override
    public <T> String createTableDdl(TableMeta<T> m, boolean ifNotExistsIgnored) {
        List<String> defs = new ArrayList<>();

        // property -> isAuto
        Map<String, Boolean> isAutoProp = new HashMap<>();
        if (m.keys != null && !m.keys.isEmpty()) {
            for (PrimaryKey pk : m.keys.values()) {
                isAutoProp.put(pk.property, pk.auto());
            }
        }

        // Column definitions
        for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
            String prop = e.getKey();
            String col  = e.getValue();
            Field f     = m.propToField.get(prop);

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

            String baseType = resolveSqlType(m, f, col);
            boolean auto = Boolean.TRUE.equals(isAutoProp.get(prop));

            StringBuilder d = new StringBuilder(q(col)).append(' ').append(baseType);
            if (auto) d.append(autoIncrementClause()); // IDENTITY(1,1)
            if (!nullable) d.append(" NOT NULL");
            if (!Column.DEFAULT_NONE.equals(defaultValue)) {
                d.append(" DEFAULT ").append(defaultValue).append("");
            }
            defs.add(d.toString());
        }

        // PRIMARY KEY (supports 0/1/many)
        if (m.keys != null && !m.keys.isEmpty()) {
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

        return "CREATE TABLE " + q(m.table) + " (\n  " + String.join(",\n  ", defs) + "\n)";
    }

    @Override
    public <T> String dropTableDdl(TableMeta<T> m, boolean ifExists) {
        // SQL Server supports DROP TABLE IF EXISTS from 2016+
        return "DROP TABLE" + (ifExists ? " IF EXISTS" : "") + " " + q(m.table);
    }

    @Override
    public String autoIncrementClause() { return " IDENTITY(1,1)"; }

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
            // Respect user choice, normalize common shapes
            String U = user.toUpperCase(Locale.ROOT);
            if ((U.contains("CHAR")) && length > 0) {
                // Prefer NVARCHAR for Java strings unless explicitly VARCHAR given
                if (U.startsWith("N") || U.contains("NCHAR")) return "NVARCHAR(" + length + ")";
                return "VARCHAR(" + length + ")";
            }
            if ((U.equals("DECIMAL") || U.equals("NUMERIC")) && precision > 0) {
                return "DECIMAL(" + precision + "," + scale + ")";
            }
            return user; // assume valid T-SQL type
        }

        if (precision > 0) return "DECIMAL(" + precision + "," + scale + ")";
        if (length > 0 && f.getType() == String.class) return "NVARCHAR(" + length + ")";

        Class<?> t = f.getType();
        if (t == Long.class || t == long.class) return "BIGINT";
        if (t == Integer.class || t == int.class) return "INT";
        if (t == Short.class || t == short.class) return "SMALLINT";
        if (t == Byte.class || t == byte.class) return "TINYINT";
        if (t == Double.class || t == double.class) return "FLOAT(53)"; // double precision
        if (t == Float.class || t == float.class) return "REAL";
        if (t == Boolean.class || t == boolean.class) return "BIT";
        if (t == java.math.BigDecimal.class) return "DECIMAL(38,10)";
        if (t.getName().equals("java.util.UUID")) return "UNIQUEIDENTIFIER";
        if (t == java.time.LocalDate.class || t == java.sql.Date.class) return "DATE";
        if (t == java.time.LocalDateTime.class || t == java.sql.Timestamp.class) return "DATETIME2(6)";
        if (t == java.time.Instant.class) return "DATETIME2(6)"; // store UTC in app layer
        return "NVARCHAR(255)";
    }

    /* =========================
       INSERT builders
       ========================= */

    @Override
    public <T> String buildInsertSql(TableMeta<T> m, List<String> cols) {
        String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
        return "INSERT INTO " + q(m.table) + " (" +
                cols.stream().map(this::q).collect(Collectors.joining(", ")) +
                ") VALUES (" + placeholders + ")";
    }

    @Override
    public String insertReturningSuffix(TableMeta<?> m) {
        // not used for SQL Server; rely on JDBC getGeneratedKeys()
        return "";
    }
}
