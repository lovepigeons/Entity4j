package org.oldskooler.entity4j.util;

import org.oldskooler.entity4j.functions.SFunction;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class LambdaUtils {
    private LambdaUtils() {}

    public static <T> String propertyName(SFunction<T, ?> getter) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = getter.getClass().getDeclaredMethod("writeReplace");
        m.setAccessible(true);
        Object sl = m.invoke(getter);
        if (!(sl instanceof SerializedLambda)) {
            throw new IllegalStateException("Not a SerializedLambda");
        }
        SerializedLambda s = (SerializedLambda) sl;
        String impl = s.getImplMethodName();
        if (impl.startsWith("get") && impl.length() > 3) {
            return decap(impl.substring(3));
        } else if (impl.startsWith("is") && impl.length() > 2) {
            return decap(impl.substring(2));
        }
        return impl;
    }

    private static String decap(String s) {
        if (s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
