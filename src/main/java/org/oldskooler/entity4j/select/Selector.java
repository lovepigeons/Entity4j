package org.oldskooler.entity4j.select;

import org.oldskooler.entity4j.functions.SFunction;
import java.util.ArrayList;
import java.util.List;

public final class Selector {
    private final List<SelectionPart> parts = new ArrayList<>();

    /**
     * Column from root entity via getter reference.
     */
    public <T, R> Selector col(SFunction<T, R> getter) {
        parts.add(SelectionPart.forGetter(null, getter, null));
        return this;
    }

    /**
     * Column from a joined entity via getter reference.
     */
    public <E, R> Selector col(Class<E> entity, SFunction<E, R> getter) {
        parts.add(SelectionPart.forGetter(entity, getter, null));
        return this;
    }

    /**
     * Column from a joined entity via getter reference.
     */
    public <E, R> Selector col(Class<E> entity, SFunction<E, R> getter, String alias) {
        parts.add(SelectionPart.forGetter(entity, getter, alias));
        return this;
    }

    /**
     * Add e.* for the given entity (root or joined).
     */
    public <E> Selector all(Class<E> entity) {
        parts.add(SelectionPart.star(entity));
        return this;
    }

    /**
     * Alias the most recently added column / aggregate (no-op if last was STAR).
     */
    public Selector as(String alias) {
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

    public <T, R> Selector sum(SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.SUM, getter, false, null));
        return this;
    }

    public <E, T, R> Selector sum(Class<E> entity, SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.SUM, getter, false, null));
        return this;
    }

    public <T, R> Selector avg(SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.AVG, getter, false, null));
        return this;
    }

    public <E, T, R> Selector avg(Class<E> entity, SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.AVG, getter, false, null));
        return this;
    }

    public <T, R> Selector max(SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.MAX, getter, false, null));
        return this;
    }

    public <E, T, R> Selector max(Class<E> entity, SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.MAX, getter, false, null));
        return this;
    }

    public <T, R> Selector min(SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.MIN, getter, false, null));
        return this;
    }

    public <E, T, R> Selector min(Class<E> entity, SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.MIN, getter, false, null));
        return this;
    }

    public <T, R> Selector count(SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.COUNT, getter, false, null));
        return this;
    }

    public Selector countStar() {
        // COUNT(*) - alias can be set with .as(...)
        parts.add(SelectionPart.countStar(null));
        return this;
    }

    public <T, R> Selector countDistinct(SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(null, SelectionPart.AggregateFunction.COUNT, getter, true, null));
        return this;
    }

    // overloads to accept entity param when selecting from joins
    public <E, T, R> Selector count(Class<E> entity, SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.COUNT, getter, false, null));
        return this;
    }

    public <E, T, R> Selector countDistinct(Class<E> entity, SFunction<T, R> getter) {
        parts.add(SelectionPart.aggregate(entity, SelectionPart.AggregateFunction.COUNT, getter, true, null));
        return this;
    }
}
