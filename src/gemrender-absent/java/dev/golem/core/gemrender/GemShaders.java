package dev.golem.core.gemrender;

import net.minecraft.server.packs.resources.ResourceManager;

/** A node with no GemRender build: vanilla's entity stages are never patched. */
public final class GemShaders {

    public enum Patch { NONE, MIRRORED, UNKNOWN }

    private GemShaders() {}

    public static Patch entityCutout(ResourceManager resources) {
        return Patch.NONE;
    }
}
