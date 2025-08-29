package org.oldskooler.entity4j.functions;

import java.io.Serializable;

@FunctionalInterface
public interface SFunction<T, R> extends Serializable {
    R apply(T t);
}
