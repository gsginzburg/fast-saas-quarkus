/*
 * Copyright 2026 Gary Ginzburg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gsginzburg.shared.converter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Generic bidirectional converter between a DTO record (D) and a domain entity (E).
 *
 * Property matching rules:
 *   - Bean getters (getXxx / isXxx) and record component accessors (xxx) are treated uniformly.
 *   - Methods declared on Object (getClass, hashCode, equals, toString, …) are excluded.
 *   - When the property name ends with "id" (case-insensitive) and one side is UUID while the
 *     other is String, the conversion is performed automatically.
 *   - Enum ↔ String conversion is performed automatically (via name() / valueOf).
 *   - Unresolvable mismatches are silently skipped (the target component receives null / default).
 *
 * Subclasses override toDto() / toDomain() to handle properties that require richer context
 * (e.g. navigating a relationship or performing a secondary query).
 */
public abstract class DtoConverter<D, E> {

    private static final Set<String> OBJECT_METHODS = Set.of(
            "getClass", "hashCode", "equals", "toString",
            "wait", "notify", "notifyAll", "finalize");

    protected final Class<D> dtoClass;
    protected final Class<E> entityClass;

    protected DtoConverter(Class<D> dtoClass, Class<E> entityClass) {
        this.dtoClass = dtoClass;
        this.entityClass = entityClass;
    }

    public D toDto(E entity) {
        if (entity == null) return null;
        return copyToRecord(entity, dtoClass);
    }

    public E toDomain(D dto) {
        if (dto == null) return null;
        return copyToBean(dto, entityClass);
    }

    public List<D> toDtoList(List<E> list) {
        if (list == null) return List.of();
        return list.stream().map(this::toDto).toList();
    }

    // ── record construction ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> T copyToRecord(Object source, Class<T> target) {
        RecordComponent[] components = target.getRecordComponents();
        if (components == null) {
            throw new IllegalArgumentException(target + " is not a record");
        }
        Map<String, Method> getters = readableProperties(source.getClass());
        Object[] args = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent rc = components[i];
            types[i] = rc.getType();
            Method getter = getters.get(rc.getName());
            if (getter != null) {
                try {
                    args[i] = coerce(getter.invoke(source), types[i], rc.getName());
                } catch (Exception e) {
                    args[i] = primitiveDefault(types[i]);
                }
            } else {
                args[i] = primitiveDefault(types[i]);
            }
        }

        try {
            Constructor<T> ctor = target.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Cannot construct record " + target.getSimpleName(), e);
        }
    }

    // ── bean population via setters ───────────────────────────────────────────

    private <T> T copyToBean(Object source, Class<T> target) {
        Map<String, Method> getters = readableProperties(source.getClass());
        Map<String, Method> setters = writableProperties(target);
        try {
            T bean = target.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Method> entry : setters.entrySet()) {
                Method getter = getters.get(entry.getKey());
                if (getter == null) continue;
                try {
                    Object value = getter.invoke(source);
                    Class<?> targetType = entry.getValue().getParameterTypes()[0];
                    Object converted = coerce(value, targetType, entry.getKey());
                    if (converted != null) entry.getValue().invoke(bean, converted);
                } catch (Exception ignored) {}
            }
            return bean;
        } catch (Exception e) {
            throw new RuntimeException("Cannot construct bean " + target.getSimpleName(), e);
        }
    }

    // ── reflection helpers ────────────────────────────────────────────────────

    private Map<String, Method> readableProperties(Class<?> clazz) {
        Map<String, Method> result = new LinkedHashMap<>();
        for (Method m : clazz.getMethods()) {
            if (OBJECT_METHODS.contains(m.getName())) continue;
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() == void.class) continue;

            String prop;
            String n = m.getName();
            if (n.startsWith("get") && n.length() > 3 && Character.isUpperCase(n.charAt(3))) {
                prop = Character.toLowerCase(n.charAt(3)) + n.substring(4);
            } else if (n.startsWith("is") && n.length() > 2
                    && m.getReturnType() == boolean.class
                    && Character.isUpperCase(n.charAt(2))) {
                prop = Character.toLowerCase(n.charAt(2)) + n.substring(3);
            } else {
                prop = n; // record accessor or other no-prefix accessor
            }
            result.putIfAbsent(prop, m);
        }
        return result;
    }

    private Map<String, Method> writableProperties(Class<?> clazz) {
        Map<String, Method> result = new LinkedHashMap<>();
        for (Method m : clazz.getMethods()) {
            String n = m.getName();
            if (!n.startsWith("set") || n.length() <= 3) continue;
            if (m.getParameterCount() != 1) continue;
            if (!Character.isUpperCase(n.charAt(3))) continue;
            String prop = Character.toLowerCase(n.charAt(3)) + n.substring(4);
            result.put(prop, m);
        }
        return result;
    }

    // ── type coercion ─────────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object coerce(Object value, Class<?> targetType, String propName) {
        if (value == null) return null;
        Class<?> sourceType = value.getClass();

        if (targetType.isAssignableFrom(sourceType)) return value;

        // String ↔ UUID for *id properties (case-insensitive "id" suffix)
        if (propName.toLowerCase().endsWith("id")) {
            if (targetType == String.class && value instanceof UUID uuid) {
                return uuid.toString();
            }
            if (targetType == UUID.class && value instanceof String s && !s.isBlank()) {
                try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
            }
        }

        // Enum ↔ String
        if (targetType.isEnum() && value instanceof String s) {
            try { return Enum.valueOf((Class<? extends Enum>) targetType, s); }
            catch (IllegalArgumentException e) { return null; }
        }
        if (targetType == String.class && sourceType.isEnum()) {
            return ((Enum<?>) value).name();
        }

        return null;
    }

    private Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class)     return 0;
        if (type == long.class)    return 0L;
        if (type == double.class)  return 0.0d;
        if (type == float.class)   return 0.0f;
        if (type == byte.class)    return (byte) 0;
        if (type == short.class)   return (short) 0;
        if (type == char.class)    return '\0';
        return null;
    }
}
