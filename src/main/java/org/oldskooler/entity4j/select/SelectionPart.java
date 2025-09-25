package org.oldskooler.entity4j.select;

// ---- internal representation ---

import org.oldskooler.entity4j.functions.SFunction;
import org.oldskooler.entity4j.util.LambdaUtils;

import java.lang.reflect.InvocationTargetException;

public final class SelectionPart {
    public enum Kind {COLUMN, STAR}

    public final Kind kind;
    public final Class<?> entityType;   // null => root
    public final String propertyName;   // for COLUMN
    public final String alias;          // for COLUMN


    private SelectionPart(Kind kind, Class<?> entityType, String propertyName, String alias) {
        this.kind = kind;
        this.entityType = entityType;
        this.propertyName = propertyName;
        this.alias = alias;

    }


    public static <T, R> SelectionPart forGetter(Class<?> entityType, SFunction<T, R> getter, String alias) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String prop = LambdaUtils.propertyName(getter); // reuse existing lambda->property util
        return new SelectionPart(Kind.COLUMN, entityType, prop, alias);

    }

    public static SelectionPart star(Class<?> entityType) {
        return new SelectionPart(Kind.STAR, entityType, null, null);

    }

    public SelectionPart withAlias(String alias) {
        return new SelectionPart(kind, entityType, propertyName, alias);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SelectionPart{");
        sb.append("kind=").append(kind);
        if (entityType != null) {
            sb.append(", entityType=").append(entityType.getSimpleName());
        }
        if (propertyName != null) {
            sb.append(", propertyName='").append(propertyName).append("'");
        }
        if (alias != null) {
            sb.append(", alias='").append(alias).append("'");
        }
        sb.append('}');
        return sb.toString();
    }

}