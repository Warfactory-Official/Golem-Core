package com.wf.gemrender.iris;

import net.irisshaders.iris.api.v0.IrisApi;

public final class ShaderPacks {
    private static volatile boolean irisLoaded;

    private ShaderPacks() {
    }

    static void irisLoaded(boolean loaded) {
        irisLoaded = loaded;
    }

    /**
     * An Iris pack replaces GemRender's fragment stage.
     */
    public static boolean inUse() {
        return irisLoaded && IrisApi.getInstance()
                .isShaderPackInUse();
    }
}
