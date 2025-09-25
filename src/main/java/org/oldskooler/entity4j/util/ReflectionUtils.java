package org.oldskooler.entity4j.util;

import org.oldskooler.entity4j.mapping.TableMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReflectionUtils {
    private ReflectionUtils() {}

    public static <T> Map<String, Object> extractValues(T entity, TableMeta<T> m) throws IllegalAccessException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Field> entry : m.propToField.entrySet()) {
            String prop = entry.getKey();
            Field f = entry.getValue();
            values.put(prop, getField(entity, f));
        }
        return values;
    }

    public static List<Field> getInstanceFields(Class<?> c) {
        ArrayList<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) out.add(f);
            }
        }
        return out;
    }

    public static Object getField(Object target, Field f) throws IllegalAccessException {
        f.setAccessible(true);
        return f.get(target);
    }

    public static void setField(Object target, Field f, Object val) throws IllegalAccessException {
        f.setAccessible(true);
        f.set(target, ValueConverter.convert(val, f.getType()));
    }
}