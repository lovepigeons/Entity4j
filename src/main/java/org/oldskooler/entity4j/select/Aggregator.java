package org.oldskooler.entity4j.select;

import org.oldskooler.entity4j.Query;
import org.oldskooler.entity4j.functions.SFunction;

import java.util.function.Supplier;

public final class Aggregator extends Selector {

    public Aggregator(Query<?> query) {
        super(query);
    }

    // Column selection with order
    @Override
    public <T, R> Aggregator col(SFunction<T, R> getter) {
        super.col(getter);
        return this;
    }

    public <T, R> Aggregator col(SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.forGetter(null, getter, null, order));
        return this;
    }

    public <E, R> Aggregator col(Class<E> entity, SFunction<E, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.forGetter(entity, getter, null, order));
        return this;
    }

    public <E, R> Aggregator col(Class<E> entity, SFunction<E, R> getter, String alias, SelectionOrder order) {
        getParts().add(SelectionPart.forGetter(entity, getter, alias, order));
        return this;
    }

    // Computed expressions with order
    @Override
    public Aggregator computed(Supplier<String> expression) {
        super.computed(expression);
        return this;
    }

    public Aggregator computed(Supplier<String> expression, SelectionOrder order) {
        getParts().add(SelectionPart.computed(null, expression, order));
        return this;
    }

    public <E> Aggregator computed(Class<E> entity, Supplier<String> expression, SelectionOrder order) {
        getParts().add(SelectionPart.computed(entity, expression, order));
        return this;
    }

    // Aggregates - SUM with order
    @Override
    public <T, R> Aggregator sum(SFunction<T, R> getter) {
        super.sum(getter);
        return this;
    }

    public <T, R> Aggregator sum(SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.SUM, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator sum(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.SUM, getter, false, null, order));
        return this;
    }

    // Aggregates - AVG with order
    @Override
    public <T, R> Aggregator avg(SFunction<T, R> getter) {
        super.avg(getter);
        return this;
    }

    public <T, R> Aggregator avg(SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.AVG, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator avg(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.AVG, getter, false, null, order));
        return this;
    }

    // Aggregates - MAX with order
    @Override
    public <T, R> Aggregator max(SFunction<T, R> getter) {
        super.max(getter);
        return this;
    }

    public <T, R> Aggregator max(SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.MAX, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator max(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.MAX, getter, false, null, order));
        return this;
    }

    // Aggregates - MIN with order
    @Override
    public <T, R> Aggregator min(SFunction<T, R> getter) {
        super.min(getter);
        return this;
    }

    public <T, R> Aggregator min(SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.MIN, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator min(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.MIN, getter, false, null, order));
        return this;
    }

    // Aggregates - COUNT with order
    @Override
    public Aggregator count() {
        super.count();
        return this;
    }

    public Aggregator count(SelectionOrder order) {
        getParts().add(SelectionPart.count(null, order));
        return this;
    }

    @Override
    public <T, R> Aggregator count(SFunction<T, R> getter) {
        super.count(getter);
        return this;
    }

    public <T, R> Aggregator count(SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.COUNT, getter, false, null, order));
        return this;
    }

    public <E, T, R> Aggregator count(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.COUNT, getter, false, null, order));
        return this;
    }

    @Override
    public <T, R> Aggregator countDistinct(SFunction<T, R> getter) {
        super.countDistinct(getter);
        return this;
    }

    public <T, R> Aggregator countDistinct(SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.COUNT, getter, true, null, order));
        return this;
    }

    public <E, T, R> Aggregator countDistinct(Class<E> entity, SFunction<T, R> getter, SelectionOrder order) {
        getParts().add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.COUNT, getter, true, null, order));
        return this;
    }

    public Aggregator asc() {
        if (!parts.isEmpty()) {
            SelectionPart last = parts.get(parts.size() - 1);
            if (last.kind != SelectionPart.Kind.STAR) {
                parts.set(parts.size() - 1, last.withOrder(SelectionOrder.ASC));
            }
        }
        return this;
    }

    public Aggregator desc() {
        if (!parts.isEmpty()) {
            SelectionPart last = parts.get(parts.size() - 1);
            if (last.kind != SelectionPart.Kind.STAR) {
                parts.set(parts.size() - 1, last.withOrder(SelectionOrder.DESC));
            }
        }
        return this;
    }

    // Override as() to return Aggregator type
    @Override
    public Aggregator as(String alias) {
        super.as(alias);
        return this;
    }

    // Star selection
    @Override
    public <E> Aggregator all(Class<E> entity) {
        super.all(entity);
        return this;
    }

    // Aggregates - SUM (entity version)
    @Override
    public <E, T, R> Aggregator sum(Class<E> entity, SFunction<T, R> getter) {
        super.sum(entity, getter);
        return this;
    }

    // Aggregates - AVG (entity version)
    @Override
    public <E, T, R> Aggregator avg(Class<E> entity, SFunction<T, R> getter) {
        super.avg(entity, getter);
        return this;
    }

    // Aggregates - MAX (entity version)
    @Override
    public <E, T, R> Aggregator max(Class<E> entity, SFunction<T, R> getter) {
        super.max(entity, getter);
        return this;
    }

    // Aggregates - MIN (entity version)
    @Override
    public <E, T, R> Aggregator min(Class<E> entity, SFunction<T, R> getter) {
        super.min(entity, getter);
        return this;
    }

    // Aggregates - COUNT (entity version)
    @Override
    public <E, T, R> Aggregator count(Class<E> entity, SFunction<T, R> getter) {
        super.count(entity, getter);
        return this;
    }

    @Override
    public <E, T, R> Aggregator countDistinct(Class<E> entity, SFunction<T, R> getter) {
        super.countDistinct(entity, getter);
        return this;
    }

    // Column selection (entity versions)
    @Override
    public <E, R> Aggregator col(Class<E> entity, SFunction<E, R> getter) {
        super.col(entity, getter);
        return this;
    }

    @Override
    public <E, R> Aggregator col(Class<E> entity, SFunction<E, R> getter, String alias) {
        super.col(entity, getter, alias);
        return this;
    }

    // Computed expressions (entity version)
    @Override
    public <E> Aggregator computed(Class<E> entity, Supplier<String> expression) {
        super.computed(entity, expression);
        return this;
    }
}