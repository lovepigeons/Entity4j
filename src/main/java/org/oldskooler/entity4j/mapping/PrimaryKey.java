package org.oldskooler.entity4j.mapping;

import java.lang.reflect.Field;

public class PrimaryKey {
    public final String property;                // may be null
    public final String column;                  // may be null
    public final boolean auto;

    public PrimaryKey(String property, String column, boolean auto) {
        this.property = property;
        this.column = column;
        this.auto = auto;
    }

    public String property() {
        return property;
    }

    public String column() {
        return column;
    }


    public boolean auto() {
        return auto;
    }
}