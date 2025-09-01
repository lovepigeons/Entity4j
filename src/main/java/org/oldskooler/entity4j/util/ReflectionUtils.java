package org.oldskooler.entity4j.util;

import org.oldskooler.entity4j.mapping.TableMeta;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReflectionUtils {
    private ReflectionUtils() {}

    public static <T> Map<String, Object> extractValues(T entity, TableMeta<T> m) {
        Map<String, Object> values = new LinkedHashMap<>();
        m.propToField.forEach((prop, f) -> values.put(prop, getField(entity, f)));
        return values;
    }

    public static Object getField(Object target, Field f) {
        try {
            f.setAccessible(true);
            return f.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setField(Object target, Field f, Object val) {
        try {
            f.setAccessible(true);
            f.set(target, ValueConverter.convert(val, f.getType()));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}