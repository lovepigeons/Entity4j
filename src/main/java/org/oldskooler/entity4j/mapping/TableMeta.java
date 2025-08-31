package org.oldskooler.entity4j.mapping;

import org.oldskooler.entity4j.annotations.Column;
import org.oldskooler.entity4j.annotations.Entity;
import org.oldskooler.entity4j.annotations.Id;
import org.oldskooler.entity4j.annotations.NotMapped;

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
    public final String idColumn;               // unquoted
    public final Field idField;                // may be null
    public final boolean idAuto;
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
        if (mapped.isPresent()) return from(mapped.get());

        TableMeta<T> annBased = tryAnnotations(type);
        if (annBased != null) return annBased;

        return convention(type);
    }

    /**
     * Deprecated: registry-unaware (kept for compatibility).
     */
    @Deprecated
    public static <T> TableMeta<T> of(Class<T> type) {
        TableMeta<T> annBased = tryAnnotations(type);
        if (annBased != null) return annBased;
        return convention(type);
    }

    /**
     * Build from fluent mapping (MappingRegistry)
     */
    private static <T> TableMeta<T> from(EntityMapping<T> em) {
        Field idF = em.idProperty == null ? null : em.propToField.get(em.idProperty);
        return new TableMeta<>(
                em.type, em.table, em.idColumn, idF, em.idAuto,
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
        // If @Entity is not present, bail out (use convention or registry instead).
        Entity entity = type.getAnnotation(Entity.class);
        if (entity == null) return null;

        String tableName = entity.table();
        if (tableName == null || tableName.isEmpty()) {
            tableName = defaultTableName(type);
        }

        LinkedHashMap<String, Field> p2f = new LinkedHashMap<>();
        LinkedHashMap<String, String> p2c = new LinkedHashMap<>();
        LinkedHashMap<String, ColumnMeta> cols = new LinkedHashMap<>();

        String idProp = null;
        String idCol = null;
        Field idF = null;
        boolean idAuto = true; // your original default

        for (Field f : allInstanceFields(type)) {
            if (Modifier.isStatic(f.getModifiers())) continue;

            // Skip @NotMapped
            if (f.getAnnotation(NotMapped.class) != null) continue;

            String prop = f.getName();

            // Determine final column name (Column.name, then Id.name, else default)
            Column colAnn = f.getAnnotation(Column.class);
            Id idAnn = f.getAnnotation(Id.class);

            String col = defaultColumnName(prop);
            if (colAnn != null && !colAnn.name().isEmpty()) col = colAnn.name();
            if (idAnn != null && !idAnn.name().isEmpty())   col = idAnn.name();

            // Gather column hints from @Column (nullable/type/precision/scale/length)
            boolean nullable = true;
            String typeOverride = "";
            int precision = 0;
            int scale = 0;
            int length = -1;

            if (colAnn != null) {
                nullable     = colAnn.nullable();
                typeOverride = colAnn.type();
                precision    = colAnn.precision();
                scale        = Math.max(0, colAnn.scale());
                length       = colAnn.length();
            }

            // If @Id is present, PK is non-nullable and we capture id settings
            if (idAnn != null) {
                idProp = prop;
                idF = f;
                idCol = col;
                idAuto = idAnn.auto(); // matches your Id.java
                nullable = false;      // PK non-nullable by default
            }

            // Build maps (note: columns keyed by *column name* to match dialect lookups)
            p2f.put(prop, f);
            p2c.put(prop, col);
            cols.put(col, new ColumnMeta(prop, col, nullable, typeOverride, precision, scale, length));
        }

        return new TableMeta<>(type, tableName, idCol, idF, idAuto, p2c, p2f, cols);
    }

    /**
     * Convention fallback if not configured or annotated.
     */
    private static <T> TableMeta<T> convention(Class<T> type) {
        String table = type.getSimpleName().toLowerCase(Locale.ROOT);
        LinkedHashMap<String, String> p2c = new LinkedHashMap<>();
        LinkedHashMap<String, Field> p2f = new LinkedHashMap<>();
        LinkedHashMap<String, ColumnMeta> cols = new LinkedHashMap<>();
        Field idF = null;
        String idCol = null;
        boolean idAuto = false;

        for (Field f : allInstanceFields(type)) {
            if (Modifier.isStatic(f.getModifiers())) continue;

            f.setAccessible(true);
            p2c.put(f.getName(), f.getName());
            p2f.put(f.getName(), f);
            // Default column meta: nullable true, no explicit type, no precision/scale.
            cols.put(f.getName(), new ColumnMeta(f.getName(), f.getName(), true, "", 0, 0, -1));
            if ("id".equals(f.getName())) {
                idF = f;
                idCol = "id";
                cols.put("id", new ColumnMeta("id", "id", false, "", 0, 0, -1)); // PK non-nullable by default
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
        this.propToField = Collections.unmodifiableMap(new LinkedHashMap<>(propToField));
        this.columns = (columns == null) ? Collections.unmodifiableMap(new HashMap<>())
                : Collections.unmodifiableMap(new LinkedHashMap<>(columns));
    }

    private static List<Field> allInstanceFields(Class<?> c) {
        ArrayList<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) out.add(f);
            }
        }
        return out;
    }

    /**
     * Best-effort snake_case-ish default for table names (fallback if @Entity.table is empty).
     */
    private static String defaultTableName(Class<?> t) {
        return t.getSimpleName().toLowerCase(Locale.ROOT);
    }

    /**
     * Best-effort snake_case-ish default for column names (fallback if @Column.name is empty).
     */
    private static String defaultColumnName(String prop) {
        // Convert simple camelCase -> snake_case; keep simple fallback too.
        StringBuilder sb = new StringBuilder(prop.length() + 4);
        for (int i = 0; i < prop.length(); i++) {
            char ch = prop.charAt(i);
            if (Character.isUpperCase(ch)) {
                sb.append('_').append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static Class<?> classOrNull(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
