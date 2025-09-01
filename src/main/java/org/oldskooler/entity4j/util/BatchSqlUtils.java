package org.oldskooler.entity4j.util;

import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.mapping.TableMeta;

import java.util.*;

public final class BatchSqlUtils {
    private BatchSqlUtils() {}

    public static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    public static <T> String buildMultiRowInsertSql(SqlDialect dialect, TableMeta<T> m, List<String> cols, int rows) {
        String colList = String.join(", ", cols.stream().map(dialect::q).toArray(String[]::new));
        String rowPlaceholders = "(" + String.join(", ", Collections.nCopies(cols.size(), "?")) + ")";
        String values = String.join(", ", Collections.nCopies(rows, rowPlaceholders));
        String sql = "INSERT INTO " + dialect.q(m.table) + " (" + colList + ") VALUES " + values;

        // Only add RETURNING if exactly one auto PK
        if (dialect.useInsertReturning() && PrimaryKeyUtils.getSingleAutoPk(m).isPresent()) {
            sql = sql + " " + dialect.insertReturningSuffix(m);
        }
        return sql;
    }

    public static <T> void collectInsertParams(T entity, TableMeta<T> m, List<String> cols, List<Object> out) {
        Map<String, Object> vals = ReflectionUtils.extractValues(entity, m);
        for (String col : cols) {
            String prop = findPropByColumn(m, col);
            out.add(vals.get(prop));
        }
    }

    public static <T> String findPropByColumn(TableMeta<T> m, String colName) {
        for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
            if (e.getValue().equals(colName)) return e.getKey();
        }
        throw new IllegalStateException("Column not found in meta: " + colName);
    }

    public static <T> List<String> buildPkEqualsList(SqlDialect dialect, TableMeta<T> m, List<String> pkProps) {
        List<String> parts = new ArrayList<>(pkProps.size());
        for (String prop : pkProps) {
            parts.add(dialect.q(m.propToColumn.get(prop)) + " = ?");
        }
        return parts;
    }
}