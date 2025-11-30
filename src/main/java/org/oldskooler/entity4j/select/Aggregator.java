package org.oldskooler.entity4j.select;

import org.oldskooler.entity4j.Query;
import org.oldskooler.entity4j.functions.SFunction;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.util.LambdaUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Aggregator implements Serializable {
    private final List<SelectionPart> parts = new ArrayList<>();
    private final Query<?> query;

    public Aggregator(Query<?> query) {
        this.query = query;
    }


    public <T, R> Aggregator col(SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.forGetter(null, getter, null, order));
        return this;
    }

    public <E, R> Aggregator col(Class<E> entity, SFunction<E, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.forGetter(entity, getter, null, order));
        return this;
    }

    public <E, R> Aggregator col(Class<E> entity, SFunction<E, R> getter, String alias, SelectionOrder order) {
        parts.add(SelectionPart.forGetter(entity, getter, alias, order));
        return this;
    }


    public Aggregator computed(Supplier<String> expression, SelectionOrder order) {
        parts.add(SelectionPart.computed(null, expression, order));
        return this;
    }

    public <E> Aggregator computed(Class<E> entity, Supplier<String> expression, SelectionOrder order) {
        parts.add(SelectionPart.computed(entity, expression, order));
        return this;
    }

    public <E, R> String columnName(SFunction<E, R> getter) {
        return columnName(null, getter);
    }

    @SuppressWarnings("unchecked")
    public <E, R> String columnName(Class<E> entity, SFunction<E, R> getter) {
        String propertyName = LambdaUtils.propertyName(getter);

        Class<E> et = entity != null
                ? entity
                : (Class<E>) query.getTableMeta().type;

        String alias = query.getAlias(et);

        String column = query.context().dialect().q(
                TableMeta.<E>of(et, query.context().mappingRegistry())
                        .propToColumn.get(propertyName)
        );

        return (alias == null || alias.isEmpty())
                ? column
                : alias + "." + column;
    }

    /**
     * Alias the most recently added column / aggregate (no-op if last was STAR).
     */
    public Aggregator as(String alias) {
        if (!parts.isEmpty()) {
            SelectionPart last = parts.get(parts.size() - 1);
            // replace with aliased variant (works for COLUMN and AGGREGATE)
            if (last.kind != SelectionPart.Kind.STAR) {
                last = last.withAlias(alias);
                parts.set(parts.size() - 1, last);
            }
        }
        return this;
    }

    public List<SelectionPart> parts() {
        return parts;
    }


    public <T, R> Aggregator sum(SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.SUM, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator sum(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.SUM, getter, false, null, order));
        return this;
    }

    public <T, R> Aggregator avg(SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.AVG, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator avg(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.AVG, getter, false, null, order));
        return this;
    }

    public <T, R> Aggregator max(SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.MAX, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator max(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.MAX, getter, false, null, order));
        return this;
    }

    public <T, R> Aggregator min(SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.MIN, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator min(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.MIN, getter, false, null, order));
        return this;
    }

    public <T, R> Aggregator count(SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.COUNT, getter, false, null, order));
        return this;
    }

    public Aggregator count(SelectionOrder order) {
        parts.add(SelectionPart.count(null, order));
        return this;
    }

    public <T, R> Aggregator countDistinct(SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.COUNT, getter, true, null, order));
        return this;
    }

    public <E, T, R> Aggregator count(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.COUNT, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator countDistinct(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.COUNT, getter, true, null, order));
        return this;
    }

}
