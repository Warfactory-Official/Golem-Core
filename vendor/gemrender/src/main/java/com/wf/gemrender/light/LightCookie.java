package com.wf.gemrender.light;

import net.minecraft.resources.ResourceLocation;

/**
 * A spot light's projected texture: RGB multiplies the beam across the outer cone, centre = axis,
 * {@code +v} = the spot's {@code up}. Made by {@link Lights#cookie}.
 */
public record LightCookie(ResourceLocation texture, int layer) {
}
