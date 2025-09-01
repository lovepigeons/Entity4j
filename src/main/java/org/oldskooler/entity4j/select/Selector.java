package org.oldskooler.entity4j.select;

import org.oldskooler.entity4j.functions.SFunction;
import org.oldskooler.entity4j.util.LambdaUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
     * Add e.* for the given entity (root or joined).
     */
    public <E> Selector all(Class<E> entity) {
        parts.add(SelectionPart.star(entity));
        return this;

    }

    /**
     * Alias the most recently added column (no-op if last was STAR).
     */


    public Selector as(String alias) {
        if (!parts.isEmpty()) {
            SelectionPart last = parts.get(parts.size() - 1);
            if (last.kind == SelectionPart.Kind.COLUMN) {
                last = last.withAlias(alias);
                parts.set(parts.size() - 1, last);
            }
        }
        return this;

    }


    public List<SelectionPart> parts() {
        return parts;
    }
}