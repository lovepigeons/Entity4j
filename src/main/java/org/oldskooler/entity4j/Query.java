package org.oldskooler.entity4j;

import org.oldskooler.entity4j.functions.SFunction;
import org.oldskooler.entity4j.mapping.SetBuilder;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.select.SelectionPart;
import org.oldskooler.entity4j.select.Selector;
import org.oldskooler.entity4j.util.JdbcParamBinder;
import org.oldskooler.entity4j.util.Names;
import org.oldskooler.entity4j.util.LambdaUtils;
import org.oldskooler.entity4j.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Query<T> {
    private final IDbContext ctx;
    private final TableMeta<T> meta;

    // WHERE builder + params (unchanged behavior)
    final StringBuilder where = new StringBuilder();
    final List<Object> params = new ArrayList<>();

    // Multi-ORDER BY
    private final List<String> orderBys = new ArrayList<>();

    // Pagination
    private Integer limit = null;
    private Integer offset = null;

    // Aliasing + joins
    private String baseAlias = null; // optional alias for FROM base table
    private final List<JoinPart<?>> joins = new ArrayList<>();
    private final Map<Class<?>, AliasMeta<?>> aliases = new LinkedHashMap<>();

    private boolean hasExplicitSelect = false;
    private final List<SelectionPart> selectionParts = new ArrayList<>();

    Query(IDbContext ctx, TableMeta<T> meta) {
        this.ctx = ctx;
        this.meta = meta;
        // Register base type in alias map; alias will be null until as() is called
        aliases.put(meta.type, new AliasMeta<>(meta, null));
    }

    /* -------------------------------
       Public fluent API
       ------------------------------- */

    /** Optional alias for the base table in FROM clause. */
    public Query<T> as(String alias) {
        this.baseAlias = alias;
        aliases.put(meta.type, new AliasMeta<>(meta, alias));
        return this;
    }

    public Query<T> filter(Function<Filters<T>, Filters<T>> builder) {
        Filters<T> f = new Filters<>(this, meta);
        builder.apply(f);
        return this;
    }

    /** Backwards-compatible single-column ORDER BY on base table. */
    public Query<T> orderBy(SFunction<T, ?> getter, boolean asc) {
        orderBys.add(qualify(meta, getter, asc));
        return this;
    }

    /** Add another ORDER BY on base table (same as calling orderBy again). */
    public Query<T> thenBy(SFunction<T, ?> getter, boolean asc) {
        return orderBy(getter, asc);
    }

    /** ORDER BY using a getter from a joined type. */
    public <J> Query<T> orderBy(Class<J> type, SFunction<J, ?> getter, boolean asc) {
        orderBys.add(qualify(getMeta(type), getter, asc));
        return this;
    }

    /** Add another ORDER BY using a joined type. */
    public <J> Query<T> thenBy(Class<J> type, SFunction<J, ?> getter, boolean asc) {
        return orderBy(type, getter, asc);
    }

    public Query<T> limit(Integer n) { this.limit = n; return this; }
    public Query<T> offset(Integer n) { this.offset = n; return this; }

    /** JOIN */
    public <J> Query<T> join(Class<J> type, String alias, Function<On<T, J>, On<T, J>> on) {
        return addJoin(type, alias, "JOIN", on);
    }

    /** LEFT OUTER JOIN */
    public <J> Query<T> leftJoin(Class<J> type, String alias, Function<On<T, J>, On<T, J>> on) {
        return addJoin(type, alias, "LEFT JOIN", on);
    }

    /** RIGHT OUTER JOIN */
    public <J> Query<T> rightJoin(Class<J> type, String alias, Function<On<T, J>, On<T, J>> on) {
        return addJoin(type, alias, "RIGHT JOIN", on);
    }

    /** LEFT INNER JOIN */
    public <J> Query<T> leftInnerJoin(Class<J> type, String alias, Function<On<T, J>, On<T, J>> on) {
        return addJoin(type, alias, "LEFT INNER JOIN", on);
    }

    /** RIGHT INNER JOIN */
    public <J> Query<T> rightInnerJoin(Class<J> type, String alias, Function<On<T, J>, On<T, J>> on) {
        return addJoin(type, alias, "RIGHT INNER JOIN", on);
    }

    /** OUTER JOIN */
    public <J> Query<T> outerJoin(Class<J> type, String alias, Function<On<T, J>, On<T, J>> on) {
        return addJoin(type, alias, "OUTER JOIN", on);
    }

    /** INNER JOIN */
    public <J> Query<T> innerJoin(Class<J> type, String alias, Function<On<T, J>, On<T, J>> on) {
        return addJoin(type, alias, "INNER JOIN", on);
    }

    /** Returns the SELECT plus a second line showing numbered parameter bindings. */
    public String toSqlWithParams() {
        String sql = buildSelectSql();
        if (params.isEmpty()) return sql;

        StringBuilder out = new StringBuilder(sql);
        out.append("\n[Params] ");
        for (int i = 0; i < params.size(); i++) {
            out.append('?').append(i + 1).append('=').append(params.get(i));
            if (i < params.size() - 1) out.append(", ");
        }
        return out.toString();
    }

    public java.util.List<T> toList() {
        String sql = buildSelectSql();
        return ctx.executeQuery(meta, sql, params);
    }

    public java.util.Optional<T> first() {
        this.limit = (this.limit == null || this.limit > 1) ? 1 : this.limit;
        java.util.List<T> xs = toList();
        return xs.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(xs.get(0));
    }

    /** Select specific columns from the root and/or joined entities. */
    public Query<T> select(Consumer<Selector> s) {
        Selector sel = new Selector();
        s.accept(sel);
        this.selectionParts.addAll(sel.parts());
        this.hasExplicitSelect = true;
        return this;
    }

    /* -------------------------------
       Internal SQL building
       ------------------------------- */

    private String buildSelectSql() {
        StringBuilder sql = new StringBuilder("SELECT ");

        sql.append(buildSelectClause());

        sql.append(" FROM ").append(ctx.dialect().q(meta.table));
        if (baseAlias != null) sql.append(' ').append(ctx.dialect().q(baseAlias));

        // Joins
        for (JoinPart<?> j : joins) {
            sql.append(' ')
                    .append(j.kind).append(' ')
                    .append(ctx.dialect().q(j.meta.table)).append(' ')
                    .append(ctx.dialect().q(j.alias))
                    .append(" ON ").append(j.onSql);
        }

        // WHERE
        if (where.length() > 0) sql.append(" WHERE ").append(where);

        // ORDER BY (also fed to dialect for pagination variations)
        String orderByClause = null;
        if (!orderBys.isEmpty()) {
            orderByClause = String.join(", ", orderBys);
            sql.append(" ORDER BY ").append(orderByClause);
        }

        // Pagination is dialect-specific; let dialect rewrite/append as needed.
        return ctx.dialect().paginate(sql.toString(), orderByClause, limit, offset);
    }

    private String buildSelectClause() {
        if (!hasExplicitSelect) {
            if (baseAlias != null)
                return ctx.dialect().q(baseAlias) + ".*";
            else
                return "*";
        }

        List<String> columns = new ArrayList<>();
        for (SelectionPart p : selectionParts) {
            if (p.kind == SelectionPart.Kind.STAR) {
                String alias = getAlias(p.entityType != null ? p.entityType : this.meta.type);
                columns.add((alias != null ? ctx.dialect().q(alias) : "*") + ".*");
            } else {
                Class<?> et = (p.entityType != null ? p.entityType : this.meta.type);
                String tableAlias = getAlias(et);
                String columnName = getMeta(et).propToColumn.get(p.propertyName);
                String aliased =
                        (tableAlias != null ? ctx.dialect().q(tableAlias) + "." : "")
                                + ctx.dialect().q(columnName);
                String label = (p.alias != null && !p.alias.isEmpty()) ? (" AS " + p.alias) : "";
                columns.add(aliased + label);
            }
        }
        return String.join(", ", columns);
    }

    /** Generic map projection (column label to value). */
    public List<Map<String,Object>> toMapList() {
        String sql = buildSelectSql();
        return ctx.executeQueryMap(sql, params);
    }

    /** DTO projection via setters matching column labels (use AS to control labels). */
    public <R> List<R> toList(Class<R> dtoType) {
        String sql = buildSelectSql();

        TableMeta<R> tempMeta = TableMeta.of(dtoType, this.ctx.mappingRegistry());
        List<Map<String, Object>> rs = ctx.executeQueryMap(sql, params);

        List<R> result = new ArrayList<>();
        for (Map<String, Object> row : rs) {
            try {
                R dto = dtoType.getDeclaredConstructor().newInstance();

                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String column = entry.getKey();
                    Optional<String> property = tempMeta.propToColumn.entrySet().stream().filter(x -> x.getValue().equals(column)).map(Map.Entry::getKey).findFirst();
                    Object value = entry.getValue();

                    if (property.isPresent()) {
                        if (tempMeta.propToField.containsKey(property.get())) {
                            try {
                                Field field = tempMeta.propToField.get(property.get());

                                if (field == null) {
                                    field = dtoType.getDeclaredField(column);
                                }

                                ReflectionUtils.setField(dto, field, value);
                            } catch (NoSuchFieldException e) {
                                // If no matching field exists in the DTO, just ignore
                            }
                        }
                    }

                    /*
                    try {
                        Field field = Arrays.stream(dtoType.getDeclaredFields())
                                .filter(x -> x.getAnnotation(Column.class) != null
                                        && x.getAnnotation(Column.class).name().equals(column))
                                .findFirst()
                                .orElse(null);

                        if (field == null) {
                            field = dtoType.getDeclaredField(column);
                        }

                        ReflectionUtils.setField(dto, field, value);
                    } catch (NoSuchFieldException e) {
                        // If no matching field exists in the DTO, just ignore
                    }*/
                }

                result.add(dto);
            } catch (Exception e) {
                throw new RuntimeException("Failed to map row to DTO", e);
            }
        }

        return result;
    }

    public int update(Consumer<SetBuilder<T>> setter) {
        if (setter == null) throw new IllegalArgumentException("setter is required");
        if (where.length() == 0) throw new IllegalArgumentException("WHERE must not be empty for update()");

        SetBuilder<T> s = new SetBuilder<>(this.ctx.dialect(), meta);
        setter.accept(s);

        if (s.sets().isEmpty()) throw new IllegalArgumentException("No columns in SET");

        String sql = "UPDATE " + ctx.q(meta.table) +
                " SET " + String.join(", ", s.sets()) +
                " WHERE " + where;

        // bind SET params first, then WHERE params (existing order)
        List<Object> all = new ArrayList<>(s.params().size() + params.size());
        all.addAll(s.params());
        all.addAll(params);

        try (PreparedStatement ps = ctx.conn().prepareStatement(sql)) {
            JdbcParamBinder.bindParams(ps, all);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("update failed: " + sql, e);
        }
    }


    /** DELETE FROM {table} WHERE (built via filter(...)) */
    public int delete() {
        if (where.length() == 0) throw new IllegalArgumentException("WHERE must not be empty for delete()");

        String sql = "DELETE FROM " + ctx.q(meta.table) + " WHERE " + where;

        try (PreparedStatement ps = ctx.conn().prepareStatement(sql)) {
            JdbcParamBinder.bindParams(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete failed: " + sql, e);
        }
    }

    public String updateSql(Consumer<SetBuilder<T>> setter) {
        if (setter == null) throw new IllegalArgumentException("setter is required");
        if (where.length() == 0) throw new IllegalArgumentException("WHERE must not be empty for update()");

        SetBuilder<T> s = new SetBuilder<>(this.ctx.dialect(), meta);
        setter.accept(s);

        if (s.sets().isEmpty()) throw new IllegalArgumentException("No columns in SET");

        List<Object> all = new ArrayList<>(s.params().size() + params.size());
        all.addAll(s.params());
        all.addAll(params);

        String sql = "UPDATE " + ctx.q(meta.table) +
                " SET " + String.join(", ", s.sets()) +
                " WHERE " + where;

        if (all.isEmpty()) return sql;

        StringBuilder out = new StringBuilder(sql);
        out.append("\n[Params] ");
        for (int i = 0; i < params.size(); i++) {
            out.append('?').append(i + 1).append('=').append(params.get(i));
            if (i < params.size() - 1) out.append(", ");
        }
        return out.toString();

    }

    public String deleteSql() {
        if (where.length() == 0) throw new IllegalArgumentException("WHERE must not be empty for delete()");

        String sql = "DELETE FROM " + ctx.q(meta.table) + " WHERE " + where;

        if (params.isEmpty()) return sql;

        StringBuilder out = new StringBuilder(sql);
        out.append("\n[Params] ");
        for (int i = 0; i < params.size(); i++) {
            out.append('?').append(i + 1).append('=').append(params.get(i));
            if (i < params.size() - 1) out.append(", ");
        }
        return out.toString();
    }


    private <J> Query<T> addJoin(Class<J> type, String alias, String kind, Function<On<T, J>, On<T, J>> onBuilder) {
        TableMeta<J> jm = TableMeta.of(type, ctx.mappingRegistry());
        if (alias == null || alias.isEmpty())
            throw new IllegalArgumentException("Join alias must be provided for " + type.getName());
        aliases.put(type, new AliasMeta<>(jm, alias));

        On<T, J> on = new On<>(this, meta, jm, alias);
        onBuilder.apply(on);

        joins.add(new JoinPart<>(jm, alias, kind, on.toSql()));
        return this;
    }

    // Turn getter into "alias.col ASC/DESC"
    private <X> String qualify(TableMeta<X> m, SFunction<X, ?> getter, boolean asc) {
        String prop = LambdaUtils.propertyName(getter);
        String col = m.propToColumn.getOrDefault(prop, Names.defaultColumnName(prop));
        String alias = getAlias(m.type);
        String qcol = (alias != null ? ctx.dialect().q(alias) + "." : "") + ctx.dialect().q(col);
        return qcol + (asc ? " ASC" : " DESC");
    }

    @SuppressWarnings("unchecked")
    private <X> TableMeta<X> getMeta(Class<X> type) {
        AliasMeta<?> a = aliases.get(type);
        if (a == null) throw new IllegalArgumentException("Type not present in FROM/JOIN: " + type.getName());
        return (TableMeta<X>) a.meta;
    }

    private String getAlias(Class<?> type) {
        AliasMeta<?> a = aliases.get(type);
        // For base type, prefer explicit alias if present
        if (a != null && a.alias != null) return a.alias;
        if (type == meta.type) return baseAlias;
        return a != null ? a.alias : null;
    }

    /* ---- internal append helpers used by Filters ---- */

    /** columnExpr should already be qualified/quoted if needed. */
    void appendCondition(String columnExpr, String op, Object value) {
        autoAndIfNeeded();
        where.append(columnExpr).append(' ').append(op).append(' ');
        if ("IN".equals(op)) {
            @SuppressWarnings("unchecked")
            Collection<Object> vals = (Collection<Object>) value;
            if (vals == null || vals.isEmpty()) {
                // "IN ()" is invalid; emit a false condition instead.
                where.append("(SELECT 1 WHERE 1=0)");
            } else {
                where.append('(')
                        .append(vals.stream().map(v -> "?").collect(Collectors.joining(", ")))
                        .append(')');
                params.addAll(vals);
            }
        } else if (value == null) {
            if ("=".equals(op)) { where.setLength(where.length() - 2); where.append("IS NULL"); }
            else if ("<>".equals(op)) { where.setLength(where.length() - 2); where.append("IS NOT NULL"); }
            else { where.append("NULL"); }
        } else {
            where.append('?');
            params.add(value);
        }
    }

    void autoAndIfNeeded() {
        if (where.length() == 0) return;
        String s = where.toString().trim();
        if (s.endsWith("(") || s.endsWith("AND") || s.endsWith("OR")) return;
        where.append(" AND ");
    }

    /* -------------------------------
       Filters (base + typed for joins)
       ------------------------------- */

    public static class Filters<T> {
        private final Query<T> q;
        private final TableMeta<T> meta;
        Filters(Query<T> q, TableMeta<T> meta) { this.q = q; this.meta = meta; }

        private String baseCol(SFunction<T, ?> getter) {
            String prop = LambdaUtils.propertyName(getter);
            String col = meta.propToColumn.getOrDefault(prop, Names.defaultColumnName(prop));
            String alias = q.baseAlias;
            return (alias != null ? q.ctx.dialect().q(alias) + "." : "") + q.ctx.dialect().q(col);
        }

        // Base-table filters (backwards-compatible)
        public Filters<T> equals(SFunction<T, ?> getter, Object value) { q.appendCondition(baseCol(getter), "=", value); return this; }
        public Filters<T> notEquals(SFunction<T, ?> getter, Object value) { q.appendCondition(baseCol(getter), "<>", value); return this; }
        public Filters<T> greater(SFunction<T, ?> getter, Object value) { q.appendCondition(baseCol(getter), ">", value); return this; }
        public Filters<T> greaterOrEquals(SFunction<T, ?> getter, Object value) { q.appendCondition(baseCol(getter), ">=", value); return this; }
        public Filters<T> less(SFunction<T, ?> getter, Object value) { q.appendCondition(baseCol(getter), "<", value); return this; }
        public Filters<T> lessOrEquals(SFunction<T, ?> getter, Object value) { q.appendCondition(baseCol(getter), "<=", value); return this; }
        public Filters<T> like(SFunction<T, ?> getter, String pattern) { q.appendCondition(baseCol(getter), "LIKE", pattern); return this; }
        public Filters<T> in(SFunction<T, ?> getter, java.util.Collection<?> values) { q.appendCondition(baseCol(getter), "IN", new java.util.ArrayList<>(values)); return this; }

        // Typed filters for joined tables
        public <J> Filters<T> equals(Class<J> type, SFunction<J, ?> getter, Object value) { return op(type, getter, "=", value); }
        public <J> Filters<T> notEquals(Class<J> type, SFunction<J, ?> getter, Object value) { return op(type, getter, "<>", value); }
        public <J> Filters<T> greater(Class<J> type, SFunction<J, ?> getter, Object value) { return op(type, getter, ">", value); }
        public <J> Filters<T> greaterOrEquals(Class<J> type, SFunction<J, ?> getter, Object value) { return op(type, getter, ">=", value); }
        public <J> Filters<T> less(Class<J> type, SFunction<J, ?> getter, Object value) { return op(type, getter, "<", value); }
        public <J> Filters<T> lessOrEquals(Class<J> type, SFunction<J, ?> getter, Object value) { return op(type, getter, "<=", value); }
        public <J> Filters<T> like(Class<J> type, SFunction<J, ?> getter, String pattern) { return op(type, getter, "LIKE", pattern); }
        public <J> Filters<T> in(Class<J> type, SFunction<J, ?> getter, java.util.Collection<?> values) {
            String prop = LambdaUtils.propertyName(getter);
            TableMeta<J> m = q.getMeta(type);
            String col = m.propToColumn.getOrDefault(prop, Names.defaultColumnName(prop));
            String alias = q.getAlias(type);
            String qcol = (alias != null ? q.ctx.dialect().q(alias) + "." : "") + q.ctx.dialect().q(col);
            q.appendCondition(qcol, "IN", new java.util.ArrayList<>(values));
            return this;
        }

        private <J> Filters<T> op(Class<J> type, SFunction<J, ?> getter, String op, Object value) {
            String prop = LambdaUtils.propertyName(getter);
            TableMeta<J> m = q.getMeta(type);
            String col = m.propToColumn.getOrDefault(prop, Names.defaultColumnName(prop));
            String alias = q.getAlias(type);
            String qcol = (alias != null ? q.ctx.dialect().q(alias) + "." : "") + q.ctx.dialect().q(col);
            q.appendCondition(qcol, op, value);
            return this;
        }

        public Filters<T> and() { q.where.append(" AND "); return this; }
        public Filters<T> or() { q.where.append(" OR "); return this; }
        public Filters<T> open() { q.where.append('('); return this; }
        public Filters<T> close() { q.where.append(')'); return this; }

        public Query<T> done() { return q; }
    }

    /* -------------------------------
       ON clause builder for joins
       ------------------------------- */

    public static class On<A, B> {
        private final Query<A> q;
        private final TableMeta<A> a;
        private final TableMeta<B> b;
        private final String bAlias;
        private final StringBuilder on = new StringBuilder();

        On(Query<A> q, TableMeta<A> a, TableMeta<B> b, String bAlias) {
            this.q = q; this.a = a; this.b = b; this.bAlias = bAlias;
        }

        public On<A, B> eq(SFunction<A, ?> left, SFunction<B, ?> right) { return bin(left, "=", right); }
        public On<A, B> ne(SFunction<A, ?> left, SFunction<B, ?> right) { return bin(left, "<>", right); }
        public On<A, B> gt(SFunction<A, ?> left, SFunction<B, ?> right) { return bin(left, ">", right); }
        public On<A, B> lt(SFunction<A, ?> left, SFunction<B, ?> right) { return bin(left, "<", right); }
        public On<A, B> ge(SFunction<A, ?> left, SFunction<B, ?> right) { return bin(left, ">=", right); }
        public On<A, B> le(SFunction<A, ?> left, SFunction<B, ?> right) { return bin(left, "<=", right); }

        // Allow chaining multiple predicates with AND/OR
        public On<A, B> and() { on.append(" AND "); return this; }
        public On<A, B> or() { on.append(" OR "); return this; }
        public On<A, B> open() { on.append('('); return this; }
        public On<A, B> close() { on.append(')'); return this; }

        private On<A, B> bin(SFunction<A, ?> l, String op, SFunction<B, ?> r) {
            if (needsAnd(on)) on.append(" AND ");
            on.append(col(a, q.getAlias(a.type), l))
                    .append(' ').append(op).append(' ')
                    .append(col(b, bAlias, r));
            return this;
        }

        private static boolean needsAnd(StringBuilder sb) {
            if (sb.length() == 0) return false;
            String s = sb.toString().trim();
            return !(s.endsWith("(") || s.endsWith("AND") || s.endsWith("OR"));
        }

        private <X> String col(TableMeta<X> m, String alias, SFunction<X, ?> g) {
            String prop = LambdaUtils.propertyName(g);
            String col = m.propToColumn.getOrDefault(prop, Names.defaultColumnName(prop));
            String qa = (alias != null ? q.ctx.dialect().q(alias) + "." : "");
            return qa + q.ctx.dialect().q(col);
        }

        String toSql() { return on.toString(); }
    }

    /* -------------------------------
       Simple holders
       ------------------------------- */

    private static final class JoinPart<J> {
        final TableMeta<J> meta;
        final String alias;
        final String kind;
        final String onSql;
        JoinPart(TableMeta<J> meta, String alias, String kind, String onSql) {
            this.meta = meta; this.alias = alias; this.kind = kind; this.onSql = onSql;
        }
    }

    private static final class AliasMeta<X> {
        final TableMeta<X> meta;
        final String alias;
        AliasMeta(TableMeta<X> meta, String alias) { this.meta = meta; this.alias = alias; }
    }
}
