package org.oldskooler.entity4j.util;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;

public final class ValueConverter {
    private ValueConverter() {}

    public static Object convert(Object val, Class<?> targetType) {
        if (val == null) return null;
        if (targetType.isInstance(val)) return val;
        if (targetType == Long.class || targetType == long.class) return ((Number) val).longValue();
        if (targetType == Integer.class || targetType == int.class) return ((Number) val).intValue();
        if (targetType == Double.class || targetType == double.class) return ((Number) val).doubleValue();
        if (targetType == Float.class || targetType == float.class) return ((Number) val).floatValue();
        if (targetType == Short.class || targetType == short.class) return ((Number) val).shortValue();
        if (targetType == Byte.class || targetType == byte.class) {
            if (val instanceof Boolean) {
                return (byte) (((Boolean) val) ? 1 : 0);
            }
            return ((Number) val).byteValue();
        }

        if (targetType == Boolean.class || targetType == boolean.class) {
            if (val instanceof Number) return ((Number) val).intValue() != 0;
            if (val instanceof String) return Boolean.parseBoolean((String) val);
        }
        if (targetType == String.class) return String.valueOf(val);
        if (targetType.getName().equals("java.util.UUID")) return java.util.UUID.fromString(String.valueOf(val));
        if (targetType == LocalDate.class && val instanceof Date) return ((Date) val).toLocalDate();
        if (targetType == LocalDateTime.class && val instanceof Timestamp)
            return ((Timestamp) val).toLocalDateTime();

        if (targetType == OffsetDateTime.class) {
            if (val instanceof Timestamp) {
                Instant instant = ((Timestamp) val).toInstant();
                return OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
            }
            if (val instanceof Date) {
                Instant instant = ((Date) val).toInstant();
                return OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
            }
            if (val instanceof String) {
                return OffsetDateTime.parse((String) val);
            }
        }

        return val;
    }
}