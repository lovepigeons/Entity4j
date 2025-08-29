package com.example.miniorm.util;

public final class Names {
    private Names() {}
    public static String defaultTableName(Class<?> type) {
        return toSnake(type.getSimpleName());
    }
    public static String defaultColumnName(String fieldName) {
        return toSnake(fieldName);
    }
    public static String toSnake(String camel) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) b.append('_');
                b.append(Character.toLowerCase(c));
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
