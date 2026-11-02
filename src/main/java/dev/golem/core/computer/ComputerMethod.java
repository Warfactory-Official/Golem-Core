package dev.golem.core.computer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A public method of a {@link ComputerComponent} that computers may call, under its own name or {@link #value}.
 * Parameters are {@code double}, {@code boolean}, {@code String}, {@code Optional<Double>}, {@code Map<?, ?>}, or one
 * trailing {@code Object[]} that takes every remaining argument. It returns the values Lua receives, or null for none.
 * A rejected argument is an {@link IllegalArgumentException}, whose message is the Lua error.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ComputerMethod {

    /** The names computers call it by; the method's own name when empty. */
    String[] value() default {};

    /** Whether it touches the live game and must run on the server thread (CC runs the rest on its own threads). */
    boolean mainThread() default false;
}
