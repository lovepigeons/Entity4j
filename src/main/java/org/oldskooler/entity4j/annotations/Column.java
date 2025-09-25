package org.oldskooler.entity4j.annotations;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
    String DEFAULT_NONE = "\u0000";   // sentinel

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

    /** Default column value **/
    String defaultValue() default DEFAULT_NONE;

}
