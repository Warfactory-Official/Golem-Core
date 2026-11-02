package dev.golem.core.computer;

//? if computercraft {
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ComputerComponent} as a CC: Tweaked peripheral. Main-thread methods wait for the server thread
 * ({@code executeMainThreadTask}); the rest run on the computer's thread. Made per lookup and equal by target and
 * type, which is what CC compares when a neighbour changes.
 */
public class ComputerCraftPeripheral implements IDynamicPeripheral {

    private final ComputerComponent component;
    private final ComponentMethods methods;

    protected ComputerCraftPeripheral(ComputerComponent component) {
        this.component = component;
        this.methods = component.methods();
    }

    static @Nullable IPeripheral of(@Nullable ComputerComponent component) {
        if (component == null) return null;
        //? if opencomputers {
        /*if (Computers.openComputers()) return new dev.golem.core.opencomputers.UnbridgedPeripheral(component);
        *///?}
        return new ComputerCraftPeripheral(component);
    }

    public final ComputerComponent component() {
        return component;
    }

    @Override
    public String[] getMethodNames() {
        return methods.names();
    }

    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments)
            throws LuaException {
        if (method < 0 || method >= methods.size()) throw new LuaException("No such method");
        Object[] raw = arguments.getAll();
        if (methods.mainThread(method)) return context.executeMainThreadTask(() -> call(method, raw));
        Object[] values = call(method, raw);
        return values == null ? MethodResult.of() : MethodResult.of(values);
    }

    private @Nullable Object[] call(int method, Object[] arguments) throws LuaException {
        try {
            return methods.call(component, method, arguments);
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
    }

    @Override
    public String getType() {
        return component.type();
    }

    @Override
    public Object getTarget() {
        return component.target();
    }

    @Override
    public final boolean equals(@Nullable IPeripheral other) {
        return other instanceof ComputerCraftPeripheral peripheral
                && peripheral.component.target() == component.target()
                && peripheral.component.type().equals(component.type());
    }
}
//?}
