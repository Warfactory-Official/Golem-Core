package dev.golem.core.info;

import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/** One tank for a block-info overlay, in millibuckets; a null fluid is an empty tank. */
public record Tank(@Nullable Fluid fluid, long millibuckets, long capacity) {
}
