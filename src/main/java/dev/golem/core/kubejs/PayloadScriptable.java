package dev.golem.core.kubejs;

//? if kubejs {

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeJavaObject;
import dev.latvian.mods.rhino.Scriptable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A script event's payload as scripts see it: Rhino's ordinary view of the payload object (its public methods and bean
 * properties), with the payload's {@link PayloadProperties} read first, so a record component is a property
 * ({@code event.radius}) as a getter already is. Writing a property is refused: the event is observe-only.
 */
final class PayloadScriptable extends NativeJavaObject {

    private final Map<String, Object> properties;

    PayloadScriptable(Context cx, Scriptable scope, Object payload, Map<String, Object> properties) {
        //? if >=1.20.5 {
        super(scope, payload, dev.latvian.mods.rhino.type.TypeInfo.of(payload.getClass()), cx);
        //?} else {
        /*super(scope, payload, payload.getClass(), cx);
        *///?}
        this.properties = properties;
    }

    @Override
    public boolean has(Context cx, String name, Scriptable start) {
        return properties.containsKey(name) || super.has(cx, name, start);
    }

    @Override
    public Object get(Context cx, String name, Scriptable start) {
        if (properties.containsKey(name)) {
            //? if >=1.20.5 {
            return cx.javaToJS(properties.get(name), start);
            //?} else {
            /*return Context.javaToJS(cx, properties.get(name), start);
            *///?}
        }
        return super.get(cx, name, start);
    }

    @Override
    public void put(Context cx, String name, Scriptable start, Object value) {
        throw new UnsupportedOperationException("Script events are read-only (" + name + ")");
    }

    @Override
    public Object[] getIds(Context cx) {
        Set<Object> ids = new LinkedHashSet<>(properties.keySet());
        ids.addAll(List.of(super.getIds(cx)));
        return ids.toArray();
    }
}
//?}
