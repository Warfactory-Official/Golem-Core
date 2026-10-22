package com.wf.gemrender.direct;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Texture per slot name ({@link com.wf.gemrender.gltf.GemRenderGltfModel#slotMeshes}) for one submit; {@code null} =>
 * that slot's meshes not drawn. Batched per (mesh, texture).
 */
@FunctionalInterface
public interface TextureSlots {
    TextureSlots NONE = slot -> null;

    @Nullable
    ResourceLocation texture(String slot);
}
