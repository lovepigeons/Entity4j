package com.example.miniorm.dialect;

import com.example.miniorm.meta.TableMeta;

import java.lang.reflect.Field;
import java.util.Locale;

public interface SqlDialect {
    /** Quote an identifier (table/column). */
    String q(String ident);

    /** True if this dialect uses "IF NOT EXISTS" in CREATE TABLE. */
    default boolean supportsCreateIfNotExists() { return true; }

    /** True if this dialect uses "IF EXISTS" in DROP TABLE. */
    default boolean supportsDropIfExists() { return true; }

    /** Render CREATE TABLE DDL for an entity. */
    <T> String createTableDdl(TableMeta<T> m, boolean ifNotExists);

    /** Render DROP TABLE DDL for an entity. */
    <T> String dropTableDdl(TableMeta<T> m, boolean ifExists);

    /** Return the SQL fragment for auto-increment / identity on a PK column, or empty if none. */
    String autoIncrementClause();

    /** Map a Java field (and optional @Column) to a dialect-specific SQL type. */
    String resolveSqlType(Field f);

    /** True if INSERT should append "RETURNING [id]" instead of relying on getGeneratedKeys(). */
    default boolean useInsertReturning() { return false; }

    /** Build the INSERT statement; if useInsertReturning() returns true, include RETURNING id. */
    <T> String buildInsertSql(TableMeta<T> m, java.util.List<String> cols);

    /** Utility: normalize user-provided explicit type to dialect expectations. */
    static String userTypeOrNull(String userType) {
        if (userType == null) return null;
        String t = userType.trim();
        return t.isEmpty() ? null : t.toUpperCase(Locale.ROOT);
    }

    default String insertReturningSuffix(TableMeta<?> m) {
        return "";
    }

    default String paginate(String selectSql, String orderByClause, Integer limit, Integer offset) {
        // sensible defaults for Postgres/MySQL/SQLite
        if (limit == null && offset == null) return selectSql;
        StringBuilder s = new StringBuilder(selectSql);
        if (orderByClause != null && !selectSql.toLowerCase(Locale.ROOT).contains("order by")) {
            s.append(" ORDER BY ").append(orderByClause);
        }
        if (limit != null) s.append(" LIMIT ").append(limit);
        if (offset != null) s.append(" OFFSET ").append(offset);
        return s.toString();
    }
}
