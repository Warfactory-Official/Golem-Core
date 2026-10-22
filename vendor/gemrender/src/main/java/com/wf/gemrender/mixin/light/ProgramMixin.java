package com.wf.gemrender.mixin.light;

import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.wf.gemrender.light.LightShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;

@Mixin(Program.class)
abstract class ProgramMixin {
    @Unique
    private static String gemrender$name;

    @Unique
    private static boolean gemrender$vertex;

    @Inject(method = "compileShaderInternal", at = @At("HEAD"))
    private static void gemrender$capture(Program.Type type, String name, InputStream shaderData, String sourceName,
                                          GlslPreprocessor preprocessor, CallbackInfoReturnable<Integer> cir) {
        gemrender$name = name;
        gemrender$vertex = type == Program.Type.VERTEX;
    }

    @ModifyArg(method = "compileShaderInternal", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/util/List;)V"), index = 1)
    private static List<String> gemrender$patch(List<String> source) {
        return List.of(LightShaders.vanilla(gemrender$name, gemrender$vertex, String.join("", source)));
    }
}
