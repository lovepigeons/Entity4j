package org.oldskooler.entity4j.dialect.types;

import org.oldskooler.entity4j.annotations.Column;
import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.mapping.ColumnMeta;
import org.oldskooler.entity4j.mapping.TableMeta;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class SqliteDialect implements SqlDialect {
    @Override public String q(String ident) { return "\"" + ident.replace("\"","\"\"") + "\""; }
    @Override public String autoIncrementClause() { return " AUTOINCREMENT"; } // only on INTEGER PRIMARY KEY

    @Override
    public <T> String createTableDdl(TableMeta<T> m, boolean ifNotExists) {
        List<String> defs = new ArrayList<>();
        for (Map.Entry<String,String> e : m.propToColumn.entrySet()) {
            String prop = e.getKey(); String col = e.getValue();
            Field f = m.propToField.get(prop);
            boolean nullable = true; // ca == null || ca.nullable();
            boolean isId = (m.idField != null && f.getName().equals(m.idField.getName()));
            boolean auto = isId && m.idAuto;

            Column colAnn = f.getAnnotation(Column.class);

            if (colAnn == null) {
                if (m.columns.containsKey(col)) {
                    ColumnMeta meta = m.columns.get(col);
                    nullable = meta.nullable;
                }
            } else {
                nullable = colAnn.nullable();
            }

            String type = resolveSqlType(m, f, col);
            StringBuilder d = new StringBuilder(q(col)).append(' ').append(type);
            if (!nullable) d.append(" NOT NULL");
            defs.add(d.toString());
        }

        // For AUTOINCREMENT, SQLite requires: INTEGER PRIMARY KEY AUTOINCREMENT (and it must be the PK column)
        if (m.idColumn != null) {
            String pkCol = q(m.idColumn);
            // Rebuild the column def for PK if needed
            for (int i = 0; i < defs.size(); i++) {
                String def = defs.get(i);
                if (def.startsWith(pkCol + " ")) {
                    if (m.idAuto) {
                        defs.set(i, pkCol + " INTEGER PRIMARY KEY" + autoIncrementClause());
                    } else {
                        defs.set(i, def + " PRIMARY KEY");
                    }
                    break;
                }
            }
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
        // SQLite is dynamically typed; we pick pragmatic defaults
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
                length = meta.length;
            }
        } else {

            user = SqlDialect.userTypeOrNull(colAnn.type());
            precision = colAnn.precision();
            length = colAnn.length();
        }

        if (user != null) {
            if ((user.contains("CHAR") || user.equals("VARCHAR")) && length > 0) return "TEXT"; // normalize
            if ((user.equals("DECIMAL") || user.equals("NUMERIC")) && precision > 0) return "NUMERIC";
            return user;
        }

        Class<?> t = f.getType();
        if (t == Long.class || t == long.class || t == Integer.class || t == int.class
                || t == Short.class || t == short.class || t == Byte.class || t == byte.class) return "INTEGER";
        if (t == Double.class || t == double.class || t == Float.class || t == float.class) return "REAL";
        if (t == Boolean.class || t == boolean.class) return "INTEGER";
        if (t == java.math.BigDecimal.class) return "NUMERIC";
        if (t.getName().equals("java.util.UUID")) return "TEXT";
        // Dates: store as TEXT (ISO8601) or INTEGER (epoch) or REAL; we'll use TEXT by default
        return "TEXT";
    }

    @Override
    public <T> String buildInsertSql(TableMeta<T> m, List<String> cols) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(cols.size(), "?"));
        return "INSERT INTO " + q(m.table) + " (" + String.join(", ", cols.stream().map(this::q).collect(Collectors.toList())) + ") VALUES (" + placeholders + ")";
    }
}
