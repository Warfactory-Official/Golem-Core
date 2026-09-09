package dev.golem.core.render;

//? if fabric && <26.1 {
/*import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.impl.client.rendering.FabricShaderProgram;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;

final class ShaderLoader {
    private ShaderLoader() {}

    static ShaderInstance load(ResourceProvider provider, String id, VertexFormat format) throws IOException {
        return new FabricShaderProgram(provider, dev.golem.core.Ids.parse(id), format);
    }
}
*///?} elif neoforge && <26.1 {
/*import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;

final class ShaderLoader {
    private ShaderLoader() {}

    static ShaderInstance load(ResourceProvider provider, String id, VertexFormat format) throws IOException {
        return new ShaderInstance(provider, dev.golem.core.Ids.parse(id), format);
    }
}
*///?} elif forge {
/*import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;

final class ShaderLoader {
    private ShaderLoader() {}

    static ShaderInstance load(ResourceProvider provider, String id, VertexFormat format) throws IOException {
        return new ShaderInstance(provider, dev.golem.core.Ids.parse(id), format);
    }
}
*///?}
