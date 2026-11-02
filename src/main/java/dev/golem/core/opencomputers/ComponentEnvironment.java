package dev.golem.core.opencomputers;

//? if opencomputers {
/*import dev.golem.core.computer.ComponentMethods;
import dev.golem.core.computer.ComputerComponent;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedPeripheral;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/^*
 * A {@link ComputerComponent} as an OpenComputers component. OC runs these calls on the server thread (a
 * {@code ManagedPeripheral} method is never direct), so main-thread methods need nothing extra. OC passes strings as
 * byte arrays and has no Java list conversion, so arguments and results are translated here.
 ^/
final class ComponentEnvironment extends AbstractManagedEnvironment
        implements ManagedPeripheral, NamedBlock, SidedEnvironment {

    private final ComputerComponent component;
    private final ComponentMethods methods;

    ComponentEnvironment(ComputerComponent component) {
        this.component = component;
        this.methods = component.methods();
        setNode(Network.newNode(this, Visibility.Network).withComponent(component.type()).create());
    }

    ComputerComponent component() {
        return component;
    }

    @Override
    public String[] methods() {
        return methods.names();
    }

    @Override
    public Object[] invoke(String method, Context context, Arguments arguments) throws Exception {
        int index = methods.indexOf(method);
        if (index < 0) throw new NoSuchMethodException(method);
        Object[] raw = arguments.toArray();
        for (int i = 0; i < raw.length; i++) raw[i] = fromLua(raw[i]);
        Object[] values = methods.call(component, index, raw);
        if (values == null) return new Object[0];
        Object[] out = new Object[values.length];
        for (int i = 0; i < values.length; i++) out[i] = toLua(values[i]);
        return out;
    }

    private static @Nullable Object fromLua(@Nullable Object value) {
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(fromLua(k), fromLua(v)));
            return out;
        }
        return value;
    }

    private static @Nullable Object toLua(@Nullable Object value) {
        if (value instanceof Collection<?> list) return list.stream().map(ComponentEnvironment::toLua).toArray();
        if (value instanceof Object[] array) {
            Object[] out = new Object[array.length];
            for (int i = 0; i < array.length; i++) out[i] = toLua(array[i]);
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(toLua(k), toLua(v)));
            return out;
        }
        return value;
    }

    @Override
    public String preferredName() {
        return component.type();
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public @Nullable Node sidedNode(Direction side) {
        return node();
    }

    @Override
    public boolean canConnect(Direction side) {
        return true;
    }
}
*///?}
