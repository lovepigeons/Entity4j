package org.oldskooler.entity4j.mapping;

import org.oldskooler.entity4j.annotations.Column;
import org.oldskooler.entity4j.annotations.Entity;
import org.oldskooler.entity4j.annotations.Id;
import org.oldskooler.entity4j.annotations.NotMapped;
import org.oldskooler.entity4j.util.Names;
import org.oldskooler.entity4j.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Minimal metadata used by DbContext and Query.
 * Merge behavior:
 *   - First tries MappingRegistry
 *   - Else tries annotations (@Entity, @Column, @Id, @NotMapped)
 *   - Else falls back to convention
 */
public final class TableMeta<T> {
    public final Class<T> type;
    public final String table;
    public final Map<String, PrimaryKey> keys;
    public final Map<String, String> propToColumn; // property -> column
    public final Map<String, Field> propToField;  // property -> Field

    /**
     * property - ColumnMeta (DDL hints for type/nullable/precision/scale)
     */
    public final Map<String, ColumnMeta> columns;

    /**
     * Preferred factory: registry-aware, then annotations, then convention.
     */
    public static <T> TableMeta<T> of(Class<T> type, MappingRegistry registry) {
        Optional<EntityMapping<T>> mapped = registry.find(type);

        if (mapped.isPresent()) {
            return from(mapped.get());
        }

        return tryAnnotations(type);

    }

    /**
     * Build from fluent mapping (MappingRegistry)
     */
    private static <T> TableMeta<T> from(EntityMapping<T> em) {
        return new TableMeta<>(
                em.type,
                em.table,
                em.keys,
                new LinkedHashMap<>(em.propToColumn),
                em.propToField,
                em.columns
        );
    }

    /**
     * Attempt to build from annotations; returns null if no @Entity on type.
     */
    @SuppressWarnings("unchecked")
    private static <T> TableMeta<T> tryAnnotations(Class<T> type) {
        String tableName = Names.toSnake(type.getName());

        Entity entity = type.getAnnotation(Entity.class);

        if (entity != null) {
            tableName = entity.table();

            if (tableName == null || tableName.isEmpty()) {
                tableName = Names.defaultTableName(type);
            }
        }

        LinkedHashMap<String, Field> p2f = new LinkedHashMap<>();
        LinkedHashMap<String, String> p2c = new LinkedHashMap<>();
        LinkedHashMap<String, ColumnMeta> cols = new LinkedHashMap<>();
        LinkedHashMap<String, PrimaryKey> keys = new LinkedHashMap<>();

        for (Field f : ReflectionUtils.getInstanceFields(type)) {
            if (Modifier.isStatic(f.getModifiers())) continue;

            String prop = f.getName();
            Column colAnn = f.getAnnotation(Column.class);
            Id idAnn = f.getAnnotation(Id.class);

            // Skip @NotMapped entirely
            if (f.getAnnotation(NotMapped.class) != null) continue;

            String col = Names.defaultColumnName(prop);
            if (colAnn != null && !colAnn.name().isEmpty()) col = colAnn.name();
            if (idAnn != null && !idAnn.name().isEmpty())   col = idAnn.name();

            // Column hints / defaults
            boolean nullable = true;
            String defaultValue = "";
            String typeOverride = "";
            int precision = 0;
            int scale = 0;
            int length = -1;

            if (colAnn != null) {
                nullable     = colAnn.nullable();
                defaultValue = colAnn.defaultValue();
                typeOverride = colAnn.type();
                precision    = colAnn.precision();
                scale        = Math.max(0, colAnn.scale());
                length       = colAnn.length();
            }

            // If @Id present -> mark PK and force non-nullable
            if (idAnn != null) {
                boolean auto = idAnn.auto();

                if (auto && !keys.isEmpty()) {
                    throw new IllegalArgumentException("auto=true not supported when multiple ID columns are declared for type: " + type.getName());
                }

                keys.put(prop, new PrimaryKey(prop, col, auto));
                nullable = false;
            }

            p2f.put(prop, f);
            p2c.put(prop, col);
            cols.put(col, new ColumnMeta(prop, col, nullable, typeOverride, defaultValue, precision, scale, length));
        }

        return new TableMeta<>(type, tableName, keys, p2c, p2f, cols);
    }

    /**
     * Convention fallback if not configured or annotated.
     */
    private static <T> TableMeta<T> convention(Class<T> type) {
        String table = type.getSimpleName().toLowerCase(Locale.ROOT);
        LinkedHashMap<String, String> p2c = new LinkedHashMap<>();
        LinkedHashMap<String, Field> p2f = new LinkedHashMap<>();
        LinkedHashMap<String, ColumnMeta> cols = new LinkedHashMap<>();

        LinkedHashMap<String, PrimaryKey> keys = new LinkedHashMap<>();

        Field idF = null;
        String idCol = null;
        boolean idAuto = false;

        for (Field f : ReflectionUtils.getInstanceFields(type)) {
            f.setAccessible(true);
            p2c.put(f.getName(), f.getName());
            p2f.put(f.getName(), f);
            // Default column meta: nullable true, no explicit type, no precision/scale.
            cols.put(f.getName(), new ColumnMeta(f.getName(), f.getName(), true, "", "", 0, 0, -1));
            if ("id".equals(f.getName())) {
                idF = f;
                idCol = "id";
                cols.put("id", new ColumnMeta("id", "id", false, "", "", 0, 0, -1)); // PK non-nullable by default

                keys.put("id", new PrimaryKey("id", idCol, true));
            }
        }
        return new TableMeta<>(type, table, keys, p2c, p2f, cols);
    }

    public TableMeta(Class<T> type,
                     String table,
                     Map<String, PrimaryKey> keys,
                     Map<String, String> propToColumn,
                     Map<String, Field> propToField,
                     Map<String, ColumnMeta> columns) {
        this.type = Objects.requireNonNull(type, "type");
        this.table = Objects.requireNonNull(table, "table");
        this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
        this.propToColumn = Collections.unmodifiableMap(new LinkedHashMap<>(propToColumn));
        this.propToField = Collections.unmodifiableMap(new LinkedHashMap<>(propToField));
        this.columns = (columns == null) ? Collections.unmodifiableMap(new HashMap<>())
                : Collections.unmodifiableMap(new LinkedHashMap<>(columns));
    }
}
