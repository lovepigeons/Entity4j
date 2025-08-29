package com.example.miniorm.meta;

import com.example.miniorm.annotations.Column;
import com.example.miniorm.annotations.Entity;
import com.example.miniorm.annotations.Id;
import com.example.miniorm.annotations.NotMapped;
import com.example.miniorm.util.Names;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public final class TableMeta<T> {
    public final Class<T> type;
    public final String table;
    public final Map<String, Field> propToField;
    public final Map<String, String> propToColumn;
    public final Map<String, String> columnToProp;
    public final Field idField;
    public final String idColumn;
    public final boolean idAuto;

    private TableMeta(Class<T> type, String table, Map<String, Field> propToField,
                      Map<String, String> propToColumn, String idProp, boolean idAuto) {
        this.type = type;
        this.table = table;
        this.propToField = propToField;
        this.propToColumn = propToColumn;
        this.columnToProp = propToColumn.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        Field f = idProp != null ? propToField.get(idProp) : null;
        this.idField = f;
        this.idColumn = idProp != null ? propToColumn.get(idProp) : null;
        this.idAuto = idAuto;
    }

    public static <T> TableMeta<T> of(Class<T> type) {
        String tableName;
        Entity en = type.getAnnotation(Entity.class);
        if (en != null && !en.table().isEmpty()) tableName = en.table();
        else tableName = Names.defaultTableName(type);

        Map<String, Field> propToField = new LinkedHashMap<>();
        Map<String, String> propToColumn = new LinkedHashMap<>();
        String idProp = null;
        boolean idAuto = true;

        for (Field f : allFields(type)) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }

            if (f.isAnnotationPresent(NotMapped.class)) {
                continue; // ignore unmapped fields
            }

            String prop = f.getName();
            String col;
            Column colAnn = f.getAnnotation(Column.class);
            if (colAnn != null && !colAnn.name().isEmpty()) col = colAnn.name();
            else col = Names.defaultColumnName(prop);
            propToField.put(prop, f);
            propToColumn.put(prop, col);
            Id idAnn = f.getAnnotation(Id.class);
            if (idAnn != null) {
                idProp = prop;
                if (!idAnn.name().isEmpty()) propToColumn.put(prop, idAnn.name());
                idAuto = idAnn.auto();
            }
        }
        return new TableMeta<>(type, tableName, propToField, propToColumn, idProp, idAuto);
    }

    private static List<Field> allFields(Class<?> t) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) out.add(f);
        }
        return out;
    }
}
