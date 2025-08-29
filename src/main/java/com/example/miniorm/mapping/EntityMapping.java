package com.example.miniorm.mapping;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EntityMapping<T> {
    public final Class<T> type;
    public final String table;
    public final String idProperty;   // may be null
    public final String idColumn;     // may be null
    public final boolean idAuto;
    public final LinkedHashMap<String,String> propToColumn; // property -> column
    public final Map<String, Field> propToField;

    public EntityMapping(Class<T> type, String table, String idProperty, String idColumn,
                         boolean idAuto, LinkedHashMap<String,String> propToColumn) {
        this.type = Objects.requireNonNull(type);
        this.table = Objects.requireNonNull(table);
        this.idProperty = idProperty;
        this.idColumn = idColumn;
        this.idAuto = idAuto;
        this.propToColumn = new LinkedHashMap<>(propToColumn);

        Map<String,Field> fields = new LinkedHashMap<>();
        for (String prop : this.propToColumn.keySet()) {
            try {
                Field f = type.getDeclaredField(prop);
                f.setAccessible(true);
                fields.put(prop, f);
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException("No field '" + prop + "' on " + type.getName(), e);
            }
        }
        this.propToField = Collections.unmodifiableMap(fields);
    }
}
