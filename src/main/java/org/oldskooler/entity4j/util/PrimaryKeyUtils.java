package org.oldskooler.entity4j.util;

import org.oldskooler.entity4j.mapping.PrimaryKey;
import org.oldskooler.entity4j.mapping.TableMeta;

import java.util.*;

public final class PrimaryKeyUtils {
    private PrimaryKeyUtils() {}

    public static <T> Optional<Map.Entry<String, PrimaryKey>> getSingleAutoPk(TableMeta<T> m) {
        if (m.keys == null || m.keys.isEmpty()) return Optional.empty();
        Map.Entry<String, PrimaryKey> match = null;
        for (Map.Entry<String, PrimaryKey> e : m.keys.entrySet()) {
            if (e.getValue() != null && e.getValue().auto()) {
                if (match != null) return Optional.empty(); // more than one auto
                match = e;
            }
        }
        return Optional.ofNullable(match);
    }

    public static <T> Set<String> getAutoPkProps(TableMeta<T> m) {
        Set<String> s = new HashSet<>();
        if (m.keys == null) return s;
        for (Map.Entry<String, PrimaryKey> e : m.keys.entrySet()) {
            if (e.getValue() != null && e.getValue().auto()) s.add(e.getKey());
        }
        return s;
    }
}