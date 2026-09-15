package dev.golem.core.render;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The entity responsible for the item render currently being submitted.
 *
 * <p>Older vanilla item-render callbacks do not carry the entity that their enclosing
 * {@code ItemRenderer.renderStatic} call does. The legacy bridge fills this scope only while that
 * vanilla call is active; callers must treat an absent owner as genuinely unknown.
 */
public final class ItemRenderContext {

    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private ItemRenderContext() {}

    /** Returns the owner supplied by the enclosing vanilla item render, or null if there is none. */
    public static @Nullable LivingEntity owner() {
        Frame frame = CURRENT.get();
        return frame == null ? null : frame.owner;
    }

    /** Opens an owner scope. Used by the legacy vanilla bridge. */
    public static void push(@Nullable LivingEntity owner) {
        CURRENT.set(new Frame(owner, CURRENT.get()));
    }

    /** Closes the most recent owner scope and restores any enclosing item render. */
    public static void pop() {
        Frame frame = CURRENT.get();
        if (frame == null || frame.parent == null) CURRENT.remove();
        else CURRENT.set(frame.parent);
    }

    private record Frame(@Nullable LivingEntity owner, @Nullable Frame parent) {}
}
