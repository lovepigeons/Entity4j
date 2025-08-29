package com.example.miniorm.annotations;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
    /** Override column name. */
    String name() default "";

    /** Nullability hint for DDL. */
    boolean nullable() default true;

    /** Explicit SQL type override (e.g. "TEXT", "UUID", "JSONB", "VARCHAR"). */
    String type() default "";

    /** For variable length types like VARCHAR. Ignored if type() is non-empty and not length-based. */
    int length() default 255;

    /** For DECIMAL/NUMERIC types. If greater than 0, renderer will prefer DECIMAL(precision, scale). */
    int precision() default 0;

    /** For DECIMAL/NUMERIC types. Only used if precision() is greater than 0. */
    int scale() default 0;

    /**
     * Full column definition override (e.g. "NUMERIC(20,6) NOT NULL").
     * If set, it takes precedence over type/length/precision/scale/nullable.
     */
    String definition() default "";
}
