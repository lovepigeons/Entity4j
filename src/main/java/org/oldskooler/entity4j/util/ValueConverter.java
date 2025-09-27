package org.oldskooler.entity4j.util;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;

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

        if (targetType == LocalDate.class) {
            if (val instanceof Date) {
                return ((Date) val).toLocalDate();
            }
            if (val instanceof String) {
                String s = (String) val;
                try {
                    // Default ISO format, e.g. "2025-09-27"
                    return LocalDate.parse(s);
                } catch (Exception e) {
                    // Fallback for common SQL patterns like "yyyy-MM-dd HH:mm:ss"
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    return LocalDate.parse(s, fmt);
                }
            }
        }

        if (targetType == LocalDateTime.class) {
            if (val instanceof Timestamp) {
                return ((Timestamp) val).toLocalDateTime();
            }
            if (val instanceof String) {
                // Try to parse using ISO or a custom pattern
                String s = (String) val;
                try {
                    return LocalDateTime.parse(s); // ISO-8601 default (e.g. 2025-09-27T15:30:00)
                } catch (Exception e) {
                    // Fallback for common SQL style "yyyy-MM-dd HH:mm:ss"
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    return LocalDateTime.parse(s, fmt);
                }
            }
        }

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