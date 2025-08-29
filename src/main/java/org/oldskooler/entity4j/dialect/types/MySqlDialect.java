package org.oldskooler.entity4j.dialect.types;

import org.oldskooler.entity4j.annotations.Column;
import org.oldskooler.entity4j.dialect.SqlDialect;
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
        for (Map.Entry<String,String> e : m.propToColumn.entrySet()) {
            String prop = e.getKey(); String col = e.getValue();
            Field f = m.propToField.get(prop);
            Column colAnn = f.getAnnotation(Column.class);

            String type = resolveSqlType(f);
            boolean isId   = (m.idField != null && f.getName().equals(m.idField.getName()));
            boolean auto   = isId && m.idAuto;
            boolean nullable = colAnn == null || colAnn.nullable();

            StringBuilder d = new StringBuilder(q(col)).append(' ').append(type);
            if (auto) d.append(autoIncrementClause());
            if (!nullable) d.append(" NOT NULL");
            defs.add(d.toString());
        }
        if (m.idColumn != null) defs.add("PRIMARY KEY (" + q(m.idColumn) + ")");

        return "CREATE TABLE" + (ifNotExists ? " IF NOT EXISTS" : "") + " " + q(m.table) +
                " (\n  " + String.join(",\n  ", defs) + "\n)";
    }

    @Override
    public <T> String dropTableDdl(TableMeta<T> m, boolean ifExists) {
        return "DROP TABLE" + (ifExists ? " IF EXISTS" : "") + " " + q(m.table);
    }

    @Override
    public String resolveSqlType(Field f) {
        Column colAnn = f.getAnnotation(Column.class);
        if (colAnn != null) {
            String user = SqlDialect.userTypeOrNull(colAnn.type());
            int precision = colAnn.precision();
            int scale = Math.max(0, colAnn.scale());
            int length = colAnn.length();

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
        }

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
