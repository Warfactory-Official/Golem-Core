package dev.golem.core.kubejs;

//? if kubejs {

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.util.CustomJavaToJsWrapper;
import java.util.Map;

/**
 * The event object scripts receive for a {@link dev.golem.core.script.ScriptEvent}: KubeJS's event type for posting,
 * shown to scripts as the payload itself ({@link PayloadScriptable}): {@code event.radius} reads a getter or record
 * component, {@code event.material('x')} calls the payload's own public method. Rhino on every KubeJS line (6, 7, 8)
 * asks a {@link CustomJavaToJsWrapper} first when it wraps a Java object for a script, so the syntax is the same on
 * all of them; only this class header and the wrapper's signature differ between the lines.
 *
 * <p>KubeJS's own event methods ({@code cancel}, {@code exit}) are not shown: script events are observe-only.
 */
//? if >=1.20.5 {
public final class PayloadEvent implements dev.latvian.mods.kubejs.event.KubeEvent, CustomJavaToJsWrapper {
//?} else {
/*public final class PayloadEvent extends dev.latvian.mods.kubejs.event.EventJS implements CustomJavaToJsWrapper {
*///?}

    private final Object payload;
    private final Map<String, Object> properties;

    PayloadEvent(Object payload) {
        this.payload = payload;
        this.properties = PayloadProperties.of(payload);
    }

    /** The payload's readable properties, read once at post time. */
    Map<String, Object> properties() {
        return properties;
    }

    @Override
    //? if >=1.20.5 {
    public Scriptable convertJavaToJs(Context cx, Scriptable scope, dev.latvian.mods.rhino.type.TypeInfo staticType) {
    //?} else {
    /*public Scriptable convertJavaToJs(Context cx, Scriptable scope, Class<?> staticType) {
    *///?}
        return new PayloadScriptable(cx, scope, payload, properties);
    }

    @Override
    public String toString() {
        return String.valueOf(payload);
    }
}
//?}
