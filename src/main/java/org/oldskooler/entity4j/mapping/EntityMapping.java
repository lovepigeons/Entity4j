package org.oldskooler.entity4j.mapping;

import org.oldskooler.entity4j.mapping.ColumnMeta;

import java.lang.reflect.Field;
import java.util.*;

/** Immutable, runtime mapping of an entity class to a table/columns. */
public final class EntityMapping<T> {
    public final Class<T> type;
    public final String table;
    public final Map<String, PrimaryKey> keys;

    /** property - column name (unquoted) */
    public final LinkedHashMap<String, String> propToColumn;

    /** property - Field (resolved up front) */
    public final Map<String, Field> propToField;

    /** NEW: property - ColumnMeta (DDL hints) */
    public final Map<String, ColumnMeta> columns;

    public EntityMapping(Class<T> type,
                         String table,
                         Map<String, PrimaryKey> keys,
                         LinkedHashMap<String, String> propToColumn,
                         Map<String, ColumnMeta> columns) {
        this.type = Objects.requireNonNull(type, "type");
        this.table = Objects.requireNonNull(table, "table");
        this.keys = Collections.unmodifiableMap(keys);

        this.propToColumn = new LinkedHashMap<>(Objects.requireNonNull(propToColumn, "propToColumn"));

        Map<String, Field> fMap = new LinkedHashMap<>();
        for (String prop : this.propToColumn.keySet()) {
            try {
                Field f = type.getDeclaredField(prop);
                f.setAccessible(true);
                fMap.put(prop, f);
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException("No field '" + prop + "' on " + type.getName(), e);
            }
        }
        this.propToField = Collections.unmodifiableMap(fMap);

        this.columns = (columns == null) ? Collections.unmodifiableMap(new HashMap<>())
                : Collections.unmodifiableMap(new LinkedHashMap<>(columns));
    }
}
