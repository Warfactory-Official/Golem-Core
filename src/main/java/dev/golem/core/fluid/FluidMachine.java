package dev.golem.core.fluid;

import net.minecraft.world.level.material.Fluid;

/**
 * One face of a mod's fluid machine as a foreign fluid system sees it, in mB. Implemented by the mod; the
 * loaders' handlers ({@link FluidExposure}) are built on it.
 *
 * <p>The machine keeps its own law. Foreign insertion and extraction only ever go through {@link #receive}
 * and {@link #give}, which are the machine's own network entry points, after {@link #demand} and
 * {@link #available} said how much they would take or give; the per-tank reads exist so a foreign peer can
 * display and address the tanks, never to write them directly. A typed tank therefore takes only its type,
 * a machine refuses on the faces it refuses a pipe on, and every rate limit still holds.
 *
 * <p>Every method is asked on the server thread. {@link #fluid} answers {@code Fluids.EMPTY} for an empty or
 * untyped tank, and then {@link #amount} is 0.
 */
public interface FluidMachine {

    /**
     * The object every face of one machine shares, normally its block entity: one rollback record and one
     * staged budget per owner, so two faces asked in one transaction are not each told the whole demand.
     */
    Object owner();

    /** False once the owner is gone; a stale handler then answers nothing. */
    boolean valid();

    int tanks();

    Fluid fluid(int tank);

    long amount(int tank);

    long capacity(int tank);

    /** Whether {@code tank} may take {@code fluid} through this face (typed to it, unpressurised, a receiving face). */
    boolean accepts(int tank, Fluid fluid);

    /** Whether {@code tank} may give {@code fluid} through this face. */
    boolean provides(int tank, Fluid fluid);

    /** mB of {@code fluid} the machine would take through this face right now, rate limits included. */
    long demand(Fluid fluid);

    /** mB of {@code fluid} the machine would give through this face right now, rate limits included. */
    long available(Fluid fluid);

    /** Delivers {@code mb} of {@code fluid} through the machine's own entry point; returns what it took. */
    long receive(Fluid fluid, long mb);

    /** Takes {@code mb} of {@code fluid} through the machine's own exit point; returns what it gave. */
    long give(Fluid fluid, long mb);

    /** Marks the owner dirty after a committed move. */
    default void changed() {}
}
