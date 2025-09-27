package org.oldskooler.entity4j.mapping;

import java.util.Objects;

/** Column metadata for DDL rendering (annotation-free). */
public final class ColumnMeta {
    public final String property;   // entity field name
    public final String name;       // db column name (unquoted)
    public final boolean nullable;  // default true
    public final String defaultValue;     // default value
    public final String type;       // explicit SQL type override (e.g. VARCHAR, DECIMAL, JSONB)
    public final int precision;     // for DECIMAL/NUMERIC; 0 means "unspecified"
    public final int scale;         // for DECIMAL/NUMERIC; only used if precision > 0
    public final int length;
    public final boolean ignored;

    public ColumnMeta(String property, String name, boolean nullable, String defaultValue, String type,
                      int precision, int scale, int length, boolean ignored) {
        this.property = Objects.requireNonNull(property, "property");
        this.name = Objects.requireNonNull(name, "name");
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.type = type == null ? "" : type;
        this.precision = precision;
        this.scale = scale;
        this.length = length;
        this.ignored = ignored;
    }

    /** Helper: return the best-effort type spec, e.g. DECIMAL(10,2) if precision/scale present. */
    public String effectiveType() {
        if (type == null || type.isEmpty()) return "";
        if ((precision > 0) && (scale >= 0)) {
            return type + "(" + precision + "," + scale + ")";
        }
        return type;
    }
}
