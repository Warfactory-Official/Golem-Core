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

    /^* {@link #load}, running {@code afterApply} after every {@code apply()} (the 1.20.1 JSON-blend reset). ^/
    static ShaderInstance load(ResourceProvider provider, String id, VertexFormat format, Runnable afterApply)
            throws IOException {
        // FabricShaderProgram is final, and Fabric's constructor hook rewrites a "namespace:path" program name
        // only for FabricShaderProgram itself (both 1.20.1 and 1.21.1 check instanceof), so a subclass given
        // "golemcore:x" crashes with an invalid minecraft:shaders/core/golemcore:x.json. Instead give vanilla a
        // plain "namespace/path" name and redirect that one program lookup to the real namespace. Stage names
        // ("golemcore:x" in the JSON) are rewritten by Fabric for every ShaderInstance, so they need nothing.
        net.minecraft.resources.ResourceLocation real = dev.golem.core.Ids.parse(id);
        String plain = real.getNamespace() + "/" + real.getPath();
        String redirected = "shaders/core/" + plain + ".json";
        ResourceProvider redirecting = location -> location.getNamespace().equals("minecraft")
                && location.getPath().equals(redirected)
                ? provider.getResource(dev.golem.core.Ids.of(real.getNamespace(), "shaders/core/" + real.getPath() + ".json"))
                : provider.getResource(location);
        return new ShaderInstance(redirecting, plain, format) {
            @Override
            public void apply() {
                super.apply();
                afterApply.run();
            }
        };
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

    /^* {@link #load}, running {@code afterApply} after every {@code apply()} (the 1.20.1 JSON-blend reset). ^/
    static ShaderInstance load(ResourceProvider provider, String id, VertexFormat format, Runnable afterApply)
            throws IOException {
        return new ShaderInstance(provider, dev.golem.core.Ids.parse(id), format) {
            @Override
            public void apply() {
                super.apply();
                afterApply.run();
            }
        };
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

    /^* {@link #load}, running {@code afterApply} after every {@code apply()} (the 1.20.1 JSON-blend reset). ^/
    static ShaderInstance load(ResourceProvider provider, String id, VertexFormat format, Runnable afterApply)
            throws IOException {
        return new ShaderInstance(provider, dev.golem.core.Ids.parse(id), format) {
            @Override
            public void apply() {
                super.apply();
                afterApply.run();
            }
        };
    }
}
*///?}
