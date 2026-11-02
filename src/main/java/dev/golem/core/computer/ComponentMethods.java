package dev.golem.core.computer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The methods a {@link ComputerComponent} answers, in the order computers list them: a class's {@link ComputerMethod}s
 * by name, or a {@link #dynamic} table. Arguments arrive as Lua values in Java form (Double, Boolean, String, Map,
 * null) and are checked with CC: Tweaked's error messages, so both computer mods report a bad call alike.
 */
public final class ComponentMethods {

    /** One method: the component and the raw Lua arguments; the values Lua receives, or null for none. */
    @FunctionalInterface
    public interface Invoker {
        @Nullable Object[] call(ComputerComponent component, Object[] arguments);
    }

    private record Entry(String name, boolean mainThread, Invoker invoker) {
    }

    private static final ClassValue<ComponentMethods> BY_CLASS = new ClassValue<>() {
        @Override
        protected ComponentMethods computeValue(Class<?> type) {
            return scan(type);
        }
    };

    private final List<Entry> entries;
    private final String[] names;

    private ComponentMethods(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.names = entries.stream().map(Entry::name).toArray(String[]::new);
    }

    public static ComponentMethods of(Class<?> type) {
        return BY_CLASS.get(type);
    }

    /** Methods by name in the map's iteration order, each run on the server thread. */
    public static ComponentMethods dynamic(Map<String, Invoker> methods) {
        List<Entry> entries = new ArrayList<>();
        methods.forEach((name, invoker) -> entries.add(new Entry(name, true, invoker)));
        return new ComponentMethods(entries);
    }

    public int size() {
        return entries.size();
    }

    public String[] names() {
        return names.clone();
    }

    public int indexOf(String name) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return i;
        return -1;
    }

    public boolean mainThread(int method) {
        return entries.get(method).mainThread();
    }

    public @Nullable Object[] call(ComputerComponent component, int method, Object[] arguments) {
        return entries.get(method).invoker().call(component, arguments);
    }

    private static ComponentMethods scan(Class<?> type) {
        List<Method> annotated = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(ComputerMethod.class) && !Modifier.isStatic(method.getModifiers())) {
                annotated.add(method);
            }
        }
        annotated.sort(Comparator.comparing(Method::getName));
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Method method : annotated) {
            ComputerMethod meta = method.getAnnotation(ComputerMethod.class);
            Invoker invoker = invoker(method);
            String[] names = meta.value().length == 0 ? new String[]{method.getName()} : meta.value();
            for (String name : names) {
                if (!seen.add(name)) throw new IllegalStateException(type.getName() + " has two computer methods " + name);
                entries.add(new Entry(name, meta.mainThread(), invoker));
            }
        }
        return new ComponentMethods(entries);
    }

    @FunctionalInterface
    private interface Coercer {
        @Nullable Object apply(Object[] arguments, int index);
    }

    private static Invoker invoker(Method method) {
        MethodHandle handle;
        try {
            handle = MethodHandles.publicLookup().unreflect(method);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(method + " is not public", e);
        }
        Class<?>[] types = method.getParameterTypes();
        Type[] generic = method.getGenericParameterTypes();
        Coercer[] coercers = new Coercer[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == Object[].class && i != types.length - 1) {
                throw new IllegalStateException(method + ": Object[] must be the last parameter");
            }
            coercers[i] = coercer(method, types[i], generic[i]);
        }
        return (component, arguments) -> {
            Object[] call = new Object[coercers.length + 1];
            call[0] = component;
            for (int i = 0; i < coercers.length; i++) call[i + 1] = coercers[i].apply(arguments, i);
            try {
                return (Object[]) handle.invokeWithArguments(call);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private static Coercer coercer(Method method, Class<?> type, Type generic) {
        if (type == double.class) return (args, i) -> number(args, i);
        if (type == boolean.class) return (args, i) -> expect(args, i, Boolean.class, "boolean");
        if (type == String.class) return (args, i) -> expect(args, i, String.class, "string");
        if (type == Map.class) return (args, i) -> expect(args, i, Map.class, "table");
        if (type == Object[].class) return (args, i) -> i < args.length ? Arrays.copyOfRange(args, i, args.length) : new Object[0];
        if (type == Optional.class && generic instanceof ParameterizedType p
                && p.getActualTypeArguments()[0] == Double.class) {
            return (args, i) -> i >= args.length || args[i] == null ? Optional.empty() : Optional.of(number(args, i));
        }
        throw new IllegalStateException(method + ": computers cannot pass a " + generic.getTypeName());
    }

    private static double number(Object[] args, int i) {
        return expect(args, i, Number.class, "number").doubleValue();
    }

    private static <T> T expect(Object[] args, int i, Class<T> type, String luaType) {
        Object value = i < args.length ? args[i] : null;
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("bad argument #" + (i + 1) + " (" + luaType + " expected, got "
                    + luaType(value) + ")");
        }
        return type.cast(value);
    }

    private static String luaType(@Nullable Object value) {
        if (value == null) return "nil";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof String) return "string";
        if (value instanceof Map) return "table";
        return "userdata";
    }
}
