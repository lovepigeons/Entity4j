package org.oldskooler.entity4j.mapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MappingRegistry {
    private final Map<Class<?>, EntityMapping<?>> byType = new LinkedHashMap<>();
    public <T> void register(EntityMapping<T> m) { byType.put(m.type, m); }
    @SuppressWarnings("unchecked")
    public <T> Optional<EntityMapping<T>> find(Class<T> type) {
        return Optional.ofNullable((EntityMapping<T>) byType.get(type));
    }
}
