package com.example.miniorm.dialect.types;

import com.example.miniorm.annotations.Column;
import com.example.miniorm.dialect.SqlDialect;
import com.example.miniorm.meta.TableMeta;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class SqlServerDialect implements SqlDialect {
    @Override public String q(String ident) { return "[" + ident.replace("]", "]]") + "]"; }
    @Override public boolean supportsCreateIfNotExists() { return false; } // emulate via IF NOT EXISTS(..) if needed
    @Override public boolean supportsDropIfExists() { return true; } // modern SQL Server supports DROP TABLE IF EXISTS
    @Override public String autoIncrementClause() { return " IDENTITY(1,1)"; }

    @Override
    public <T> String createTableDdl(TableMeta<T> m, boolean ifNotExists) {
        List<String> defs = new ArrayList<>();
        for (Map.Entry<String,String> e : m.propToColumn.entrySet()) {
            String prop = e.getKey(); String col = e.getValue();
            Field f = m.propToField.get(prop);
            Column ca = f.getAnnotation(Column.class);

            boolean isId = (m.idField != null && f.getName().equals(m.idField.getName()));
            boolean auto = isId && m.idAuto;
            boolean nullable = ca == null || ca.nullable();

            String type = resolveSqlType(f);
            StringBuilder d = new StringBuilder(q(col)).append(' ').append(type);
            if (auto) d.append(autoIncrementClause());
            if (!nullable) d.append(" NOT NULL");
            defs.add(d.toString());
        }
        if (m.idColumn != null) defs.add("PRIMARY KEY (" + q(m.idColumn) + ")");

        String core = "CREATE TABLE " + q(m.table) + " (\n  " + String.join(",\n  ", defs) + "\n)";
        if (!ifNotExists) return core;

        // Emulate IF NOT EXISTS
        return "IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'" + m.table + "') AND type in (N'U'))\n" + core;
    }

    @Override
    public <T> String dropTableDdl(TableMeta<T> m, boolean ifExists) {
        if (ifExists) return "DROP TABLE IF EXISTS " + q(m.table);
        return "DROP TABLE " + q(m.table);
    }

    @Override
    public String resolveSqlType(Field f) {
        Class<?> t = f.getType();
        com.example.miniorm.annotations.Column ann = f.getAnnotation(com.example.miniorm.annotations.Column.class);
        if (ann != null) {
            String user = SqlDialect.userTypeOrNull(ann.type());
            int precision = ann.precision();
            int scale = Math.max(0, ann.scale());
            int length = ann.length();
            if (user != null) {
                if ((user.contains("CHAR") || user.equals("VARCHAR") || user.equals("NVARCHAR")) && length > 0)
                    return user + "(" + length + ")";
                if ((user.equals("DECIMAL") || user.equals("NUMERIC")) && precision > 0)
                    return "DECIMAL(" + precision + "," + scale + ")";
                return user;
            }
            if (precision > 0) return "DECIMAL(" + precision + "," + scale + ")";
            if (length > 0 && t == String.class) return "NVARCHAR(" + length + ")";
        }
        if (t == Long.class || t == long.class) return "BIGINT";
        if (t == Integer.class || t == int.class) return "INT";
        if (t == Short.class || t == short.class) return "SMALLINT";
        if (t == Byte.class || t == byte.class) return "TINYINT";
        if (t == Double.class || t == double.class) return "FLOAT"; // SQL Server FLOAT ~ double
        if (t == Float.class || t == float.class) return "REAL";
        if (t == Boolean.class || t == boolean.class) return "BIT";
        if (t == java.math.BigDecimal.class) return "DECIMAL(38,10)";
        if (t.getName().equals("java.util.UUID")) return "UNIQUEIDENTIFIER";
        if (t == java.time.LocalDate.class || t == java.sql.Date.class) return "DATE";
        if (t == java.time.LocalDateTime.class || t == java.sql.Timestamp.class) return "DATETIME2(6)";
        if (t == java.time.Instant.class) return "DATETIME2(6)";
        return "NVARCHAR(255)";
    }

    @Override
    public <T> String buildInsertSql(TableMeta<T> m, List<String> cols) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(cols.size(), "?"));
        return "INSERT INTO " + q(m.table) + " (" + String.join(", ", cols.stream().map(this::q).collect(Collectors.toList())) + ") VALUES (" + placeholders + ")";
    }

    @Override
    public String paginate(String selectSql, String orderByClause, Integer limit, Integer offset) {
        String ob = (orderByClause != null) ? orderByClause : "(SELECT 1)"; // SQL Server requires ORDER BY
        String base = selectSql;
        if (!selectSql.toLowerCase(Locale.ROOT).contains("order by")) {
            base = selectSql + " ORDER BY " + ob;
        }
        if (offset == null) offset = 0;
        if (limit == null) {
            // OFFSET … FETCH requires FETCH when LIMIT present; OFFSET-only is allowed
            return base + " OFFSET " + offset + " ROWS";
        }
        return base + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }
}
