package org.oldskooler.entity4j.mapping;

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
        // We reference annotations by name to avoid hard compile deps if they’re absent.
        // Expected FQCNs (adjust if yours differ):
        //   com.example.miniorm.annotations.Entity
        //   com.example.miniorm.annotations.Column
        //   com.example.miniorm.annotations.Id
        //   com.example.miniorm.annotations.NotMapped
        Class<?> entityAnn = classOrNull("com.example.miniorm.annotations.Entity");
        if (entityAnn == null) return null; // annotations not on classpath

        Object entity = type.getAnnotation((Class<java.lang.annotation.Annotation>) entityAnn);
        if (entity == null) return null; // not annotated as @Entity

        String tableName = null;
        try {
            // String table() default "";
            tableName = (String) entityAnn.getMethod("table").invoke(entity);
        } catch (ReflectiveOperationException ignore) {
        }
        if (tableName == null || tableName.isEmpty()) {
            tableName = defaultTableName(type);
        }

        Class<?> columnAnn = classOrNull("com.example.miniorm.annotations.Column");
        Class<?> idAnn = classOrNull("com.example.miniorm.annotations.Id");
        Class<?> notMapped = classOrNull("com.example.miniorm.annotations.NotMapped");

        LinkedHashMap<String, Field> p2f = new LinkedHashMap<>();
        LinkedHashMap<String, String> p2c = new LinkedHashMap<>();
        LinkedHashMap<String, ColumnMeta> cols = new LinkedHashMap<>();

        String idProp = null;
        String idCol = null;
        Field idF = null;
        boolean idAuto = true; // default true like your second file

        for (Field f : allInstanceFields(type)) {
            if (Modifier.isStatic(f.getModifiers())) continue;

            // @NotMapped?
            if (notMapped != null && f.getAnnotation((Class<java.lang.annotation.Annotation>) notMapped) != null) {
                continue;
            }

            String prop = f.getName();
            String col = defaultColumnName(prop);

            // @Column(name="...")?
            if (columnAnn != null) {
                java.lang.annotation.Annotation cA = f.getAnnotation((Class<java.lang.annotation.Annotation>) columnAnn);
                if (cA != null) {
                    try {
                        String name = (String) columnAnn.getMethod("name").invoke(cA);
                        if (name != null && !name.isEmpty()) col = name;
                    } catch (ReflectiveOperationException ignore) {
                    }
                }
            }

            p2f.put(prop, f);
            p2c.put(prop, col);

            // Default ColumnMeta: nullable true, no explicit type/precision/scale
            ColumnMeta meta = new ColumnMeta(prop, col, true, "", 0, 0, -1);
            cols.put(prop, meta);

            // @Id?
            if (idAnn != null) {
                java.lang.annotation.Annotation iA = f.getAnnotation((Class<java.lang.annotation.Annotation>) idAnn);
                if (iA != null) {
                    idProp = prop;
                    idF = f;

                    // Allow @Id(name="...") override
                    try {
                        String name = (String) idAnn.getMethod("name").invoke(iA);
                        if (name != null && !name.isEmpty()) {
                            p2c.put(prop, name);
                            col = name;
                            // reflect in ColumnMeta
                            cols.put(prop, new ColumnMeta(prop, col, false, "", 0, 0, -1));
                        }
                    } catch (ReflectiveOperationException ignore) {
                    }

                    // @Id(auto=...)
                    try {
                        Boolean auto = (Boolean) idAnn.getMethod("auto").invoke(iA);
                        if (auto != null) idAuto = auto;
                    } catch (ReflectiveOperationException ignore) {
                    }

                    idCol = col;

                    // Primary key non-nullable by default
                    cols.put(prop, new ColumnMeta(prop, col, false, "", 0, 0, -1));
                }
            }
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
