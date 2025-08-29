package com.example.miniorm.meta;

import com.example.miniorm.mapping.EntityMapping;
import com.example.miniorm.mapping.MappingRegistry;

import java.lang.reflect.Field;
import java.util.*;

public final class TableMeta<T> {
    public final Class<T> type;
    public final String table;
    public final String idColumn;          // may be null
    public final Field  idField;           // may be null
    public final boolean idAuto;
    public final Map<String,String> propToColumn;
    public final Map<String,Field>  propToField;

    public static <T> TableMeta<T> of(Class<T> type, MappingRegistry registry) {
        Optional<EntityMapping<T>> mapped = registry.find(type);
        if (mapped.isPresent()) return from(mapped.get());
        return convention(type);
    }

    private static <T> TableMeta<T> from(EntityMapping<T> em) {
        Field idF = em.idProperty == null ? null : em.propToField.get(em.idProperty);
        return new TableMeta<>(
                em.type, em.table, em.idColumn, idF, em.idAuto,
                new LinkedHashMap<>(em.propToColumn), em.propToField
        );
    }

    private static <T> TableMeta<T> convention(Class<T> type) {
        String table = type.getSimpleName().toLowerCase(Locale.ROOT);
        LinkedHashMap<String,String> p2c = new LinkedHashMap<>();
        LinkedHashMap<String,Field>  p2f = new LinkedHashMap<>();
        Field idF = null; String idCol = null; boolean idAuto = false;

        for (Field f : allInstanceFields(type)) {
            f.setAccessible(true);
            p2c.put(f.getName(), f.getName());
            p2f.put(f.getName(), f);
            if ("id".equals(f.getName())) { idF = f; idCol = "id"; }
        }
        return new TableMeta<>(type, table, idCol, idF, idAuto, p2c, p2f);
    }

    public TableMeta(Class<T> type, String table, String idColumn, Field idField, boolean idAuto,
                     Map<String,String> propToColumn, Map<String,Field> propToField) {
        this.type = type;
        this.table = table;
        this.idColumn = idColumn;
        this.idField = idField;
        this.idAuto = idAuto;
        this.propToColumn = Collections.unmodifiableMap(new LinkedHashMap<>(propToColumn));
        this.propToField  = Collections.unmodifiableMap(new LinkedHashMap<>(propToField));
    }

    private static List<Field> allInstanceFields(Class<?> c) {
        ArrayList<Field> out = new ArrayList<>();
        for (Class<?> k=c; k!=null && k!=Object.class; k=k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) out.add(f);
            }
        }
        return out;
    }
}
