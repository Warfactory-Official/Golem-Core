package dev.golem.core.kubejs;

//? if kubejs {

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A script event payload's readable properties ({@code event.radius}): a record's components, otherwise its public
 * {@code getX()} / {@code isX()} getters (as {@code x}). Read once per post, into an unmodifiable map.
 *
 * <p>Why: Rhino (every line KubeJS 6, 7 and 8 ship) makes properties only of bean getters, never of record accessors,
 * so {@link PayloadScriptable} answers these names first; the same spelling then reads alike on every line.
 *
 * <p>Only the payload type's public API is read (no access checks are bypassed); a payload that is not public is
 * reported once and shows no properties. No KubeJS types: every API line's adapter shares this.
 */
final class PayloadProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger("Golem Core scripts");

    private record Property(String name, Method getter) {}

    private static final Map<Class<?>, List<Property>> PROPERTIES = new ConcurrentHashMap<>();

    private PayloadProperties() {}

    static Map<String, Object> of(Object payload) {
        if (payload == null) return Map.of();
        List<Property> properties = PROPERTIES.computeIfAbsent(payload.getClass(), PayloadProperties::describe);
        Map<String, Object> values = new LinkedHashMap<>(properties.size() * 2);
        for (Property property : properties) {
            try {
                values.put(property.name(), property.getter().invoke(payload));
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Script event payload {}: reading {} failed", payload.getClass().getName(), property.name(), e);
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static List<Property> describe(Class<?> type) {
        if (!Modifier.isPublic(type.getModifiers())) {
            LOGGER.warn("Script event payload {} is not public: scripts see no properties", type.getName());
            return List.of();
        }
        List<Property> found = new ArrayList<>();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                found.add(new Property(component.getName(), component.getAccessor()));
            }
            return List.copyOf(found);
        }
        Map<String, Method> byName = new LinkedHashMap<>();
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || method.getReturnType() == void.class || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = propertyName(method);
            if (name != null) byName.putIfAbsent(name, method);
        }
        byName.forEach((name, method) -> found.add(new Property(name, method)));
        return List.copyOf(found);
    }

    private static String propertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))
                && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return null;
    }

    private static String decapitalize(String name) {
        // Bean rules, as Rhino's own bean properties: "URL" stays "URL", "Radius" becomes "radius".
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
//?}
