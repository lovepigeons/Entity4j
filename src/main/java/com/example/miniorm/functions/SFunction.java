package com.example.miniorm.functions;

import java.io.Serializable;

@FunctionalInterface
public interface SFunction<T, R> extends Serializable {
    R apply(T t);
}
