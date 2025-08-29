package org.oldskooler.entity4j.mapping;

import org.oldskooler.entity4j.mapping.ColumnMeta;

import java.lang.reflect.Field;
import java.util.*;

/** Minimal metadata used by DbContext and Query. */
public final class TableMeta<T> {
    public final Class<T> type;
    public final String table;
    public final String idColumn;               // unquoted
    public final Field  idField;                // may be null
    public final boolean idAuto;
    public final Map<String, String> propToColumn; // property -> column
    public final Map<String, Field>  propToField;  // property -> Field

    /** NEW: property - ColumnMeta (DDL hints for type/nullable/precision/scale). */
    public final Map<String, ColumnMeta> columns;

    /** Preferred factory: registry-aware. */
    public static <T> TableMeta<T> of(Class<T> type, MappingRegistry registry) {
        Optional<EntityMapping<T>> mapped = registry.find(type);
        if (mapped.isPresent()) return from(mapped.get());
        return convention(type);
    }

    @Deprecated
    public static <T> TableMeta<T> of(Class<T> type) {
        return convention(type);
    }

    private static <T> TableMeta<T> from(EntityMapping<T> em) {
        Field idF = em.idProperty == null ? null : em.propToField.get(em.idProperty);
        return new TableMeta<>(
                em.type, em.table, em.idColumn, idF, em.idAuto,
                new LinkedHashMap<>(em.propToColumn),
                em.propToField,
                em.columns
        );
    }

    /** Convention fallback if not configured in onModelCreating. */
    private static <T> TableMeta<T> convention(Class<T> type) {
        String table = type.getSimpleName().toLowerCase(Locale.ROOT);
        LinkedHashMap<String,String> p2c = new LinkedHashMap<>();
        LinkedHashMap<String,Field>  p2f = new LinkedHashMap<>();
        LinkedHashMap<String,ColumnMeta> cols = new LinkedHashMap<>();
        Field idF = null; String idCol = null; boolean idAuto = false;

        for (Field f : allInstanceFields(type)) {
            f.setAccessible(true);
            p2c.put(f.getName(), f.getName());
            p2f.put(f.getName(), f);
            // Default column meta: nullable true, no explicit type, no precision/scale.
            cols.put(f.getName(), new ColumnMeta(f.getName(), f.getName(), true, "", 0, 0));
            if ("id".equals(f.getName())) {
                idF = f; idCol = "id";
                cols.put("id", new ColumnMeta("id", "id", false, "", 0, 0)); // primary key non-nullable by default
            }
        }
        return new TableMeta<>(type, table, idCol, idF, idAuto, p2c, p2f, cols);
    }

    public TableMeta(Class<T> type,
                     String table,
                     String idColumn,
                     Field idField,
                     boolean idAuto,
                     Map<String, String> propToColumn,
                     Map<String, Field> propToField,
                     Map<String, ColumnMeta> columns) {
        this.type = Objects.requireNonNull(type, "type");
        this.table = Objects.requireNonNull(table, "table");
        this.idColumn = idColumn;
        this.idField = idField;
        this.idAuto = idAuto;
        this.propToColumn = Collections.unmodifiableMap(new LinkedHashMap<>(propToColumn));
        this.propToField  = Collections.unmodifiableMap(new LinkedHashMap<>(propToField));
        this.columns      = (columns == null) ? Collections.unmodifiableMap(new HashMap<>())
                : Collections.unmodifiableMap(new LinkedHashMap<>(columns));
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
