package org.oldskooler.entity4j.select;

import org.oldskooler.entity4j.functions.SFunction;
import org.oldskooler.entity4j.util.LambdaUtils;

public final class SelectionPart {
    public enum Kind {COLUMN, STAR, AGGREGATE}

    public enum AggregateFunction {SUM, AVG, COUNT, MIN, MAX}

    public final Kind kind;
    public final Class<?> entityType;   // null => root
    public final String propertyName;   // for COLUMN and AGGREGATE (can be null for COUNT(*))
    public final String alias;          // optional alias for COLUMN or AGGREGATE

    // only used for aggregates:
    public final AggregateFunction aggregateFunction;
    public final boolean distinct;      // for COUNT(DISTINCT ...)

    private SelectionPart(Kind kind,
                          Class<?> entityType,
                          String propertyName,
                          String alias,
                          AggregateFunction aggregateFunction,
                          boolean distinct) {
        this.kind = kind;
        this.entityType = entityType;
        this.propertyName = propertyName;
        this.alias = alias;
        this.aggregateFunction = aggregateFunction;
        this.distinct = distinct;
    }

    // existing column factory
    public static <T, R> SelectionPart forGetter(Class<?> entityType, SFunction<T, R> getter, String alias) {
        String prop = LambdaUtils.propertyName(getter); // reuse existing lambda->property util
        return new SelectionPart(Kind.COLUMN, entityType, prop, alias, null, false);
    }

    // star factory
    public static SelectionPart star(Class<?> entityType) {
        return new SelectionPart(Kind.STAR, entityType, null, null, null, false);
    }

    // aggregate factory: propertyName may be null for COUNT(*)
    public static <T, R> SelectionPart aggregate(Class<?> entityType,
                                                 AggregateFunction func,
                                                 SFunction<T, R> getter,
                                                 boolean distinct,
                                                 String alias) {
        String prop = (getter == null) ? null : LambdaUtils.propertyName(getter);
        return new SelectionPart(Kind.AGGREGATE, entityType, prop, alias, func, distinct);
    }

    // convenience for COUNT(*)
    public static SelectionPart countStar(String alias) {
        return new SelectionPart(Kind.AGGREGATE, null, null, alias, AggregateFunction.COUNT, false);
    }

    public SelectionPart withAlias(String alias) {
        return new SelectionPart(kind, entityType, propertyName, alias, aggregateFunction, distinct);
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
