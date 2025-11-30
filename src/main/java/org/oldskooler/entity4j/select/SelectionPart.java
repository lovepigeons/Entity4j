package org.oldskooler.entity4j.select;

import org.oldskooler.entity4j.functions.SFunction;
import org.oldskooler.entity4j.util.LambdaUtils;

import java.io.Serializable;
import java.util.function.Supplier;

public final class SelectionPart implements Serializable {
    public enum Kind {COLUMN, STAR, AGGREGATE, COMPUTED}
    public enum AggregateFunction {SUM, AVG, COUNT, MIN, MAX}

    public final Kind kind;
    public final Class<?> entityType;   // null => root
    public final String propertyName;   // for COLUMN and AGGREGATE (can be null for COUNT(*))
    public final String alias;          // optional alias for COLUMN or AGGREGATE
    public final Supplier<String> expression;
    public final SelectionOrder orderBy;

    // only used for aggregates:
    public final AggregateFunction aggregateFunction;
    public final boolean distinct;      // for COUNT(DISTINCT ...)

    private SelectionPart(Kind kind,
                          Class<?> entityType,
                          String propertyName,
                          String alias,
                          AggregateFunction aggregateFunction,
                          boolean distinct,
                          Supplier<String> expression,
                          SelectionOrder asc) {
        this.kind = kind;
        this.entityType = entityType;
        this.propertyName = propertyName;
        this.alias = alias;
        this.aggregateFunction = aggregateFunction;
        this.distinct = distinct;
        this.expression = expression;
        this.orderBy = asc;
    }

    public static <T, R> SelectionPart forGetter(Class<?> entityType, SFunction<T, R> getter, String alias) {
        String prop = LambdaUtils.propertyName(getter); // reuse existing lambda->property util
        return new SelectionPart(Kind.COLUMN, entityType, prop, alias, null, false, null, null);
    }

    public static <T, R> SelectionPart forGetter(Class<?> entityType, SFunction<T, R> getter, String alias, SelectionOrder order) {
        String prop = LambdaUtils.propertyName(getter); // reuse existing lambda->property util
        return new SelectionPart(Kind.COLUMN, entityType, prop, alias, null, false, null, order);
    }

    public static <E> SelectionPart computed(Class<E> entity, Supplier<String> expression) {
        return computed(entity, expression, null);
    }

    public static <E> SelectionPart computed(Class<E> entity, Supplier<String> expression, SelectionOrder order) {
        return new SelectionPart(Kind.COMPUTED, entity, null, null, null, false, expression, order);
    }

    // star factory
    public static SelectionPart star(Class<?> entityType) {
        return star(entityType, null);
    }

    public static SelectionPart star(Class<?> entityType, SelectionOrder order) {
        return new SelectionPart(Kind.STAR, entityType, null, null, null, false, null, order);
    }

    // aggregate factory: propertyName may be null for COUNT(*)
    public static <T, R> SelectionPart aggregate(Class<?> entityType,
                                                 AggregateFunction func,
                                                 SFunction<T, R> getter,
                                                 boolean distinct,
                                                 String alias) {
       return aggregate(entityType, func, getter, distinct, alias, null);
    }

    public static <T, R> SelectionPart aggregate(Class<?> entityType,
                                                 AggregateFunction func,
                                                 SFunction<T, R> getter,
                                                 boolean distinct,
                                                 String alias,
                                                 SelectionOrder order) {
        String prop = (getter == null) ? null : LambdaUtils.propertyName(getter);
        return new SelectionPart(Kind.AGGREGATE, entityType, prop, alias, func, distinct, null, order);
    }

    // convenience for COUNT(*)
    public static SelectionPart count(String alias) {
        return count(alias, null);
    }

    public static SelectionPart count(String alias, SelectionOrder order) {
        return new SelectionPart(Kind.AGGREGATE, null, null, alias, AggregateFunction.COUNT, false, null, order);
    }

    public SelectionPart withAlias(String alias) {
        return withAlias(alias, null);
    }

    public SelectionPart withAlias(String alias, SelectionOrder order) {
        return new SelectionPart(kind, entityType, propertyName, alias, aggregateFunction, distinct, expression, order);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SelectionPart{");
        sb.append("kind=").append(kind);
        if (entityType != null) {
            sb.append(", entityType=").append(entityType.getSimpleName());
        }
        if (propertyName != null) {
            sb.append(", propertyName='").append(propertyName).append("'");
        }
        if (alias != null) {
            sb.append(", alias='").append(alias).append("'");
        }
        if (aggregateFunction != null) {
            sb.append(", aggregate=").append(aggregateFunction);
            if (distinct) sb.append(" DISTINCT");
        }
        sb.append('}');
        return sb.toString();
    }
}