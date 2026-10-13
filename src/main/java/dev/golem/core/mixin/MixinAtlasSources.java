package dev.golem.core.mixin;

import dev.golem.core.render.AtlasSources;
import dev.golem.core.render.Sprites;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds Core's code-declared sprite sources to the atlas that asked for them; see {@link AtlasSources}.
 *
 * <p>1.21 renamed this class from {@code SpriteResourceLoader} to {@code SpriteSourceList}, and the id
 * it is handed is an {@code Identifier} from 26.1. Nothing else about it moved.
 */
//? if >=1.21 {
@Mixin(net.minecraft.client.renderer.texture.atlas.SpriteSourceList.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader.class)
*///?}
public abstract class MixinAtlasSources implements AtlasSources {

    @Shadow
    @Final
    @Mutable
    private List<SpriteSource> sources;

    @Override
    public void golem$prependSources(List<SpriteSource> declared) {
        List<SpriteSource> merged = new ArrayList<>(declared);
        merged.addAll(sources);
        sources = merged;
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void golem$addDeclaredSources(ResourceManager resources,
            //? if >=26.1 {
            net.minecraft.resources.Identifier atlas,
            //?} else {
            /*net.minecraft.resources.ResourceLocation atlas,
            *///?}
            CallbackInfoReturnable<
                    //? if >=1.21 {
                    net.minecraft.client.renderer.texture.atlas.SpriteSourceList
                    //?} else {
                    /*net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader
                    *///?}
                    > callback) {
        List<SpriteSource> declared = Sprites.declaredFor(atlas.toString());
        if (!declared.isEmpty()) {
            ((AtlasSources) (Object) callback.getReturnValue()).golem$prependSources(declared);
            // One line per atlas per reload, and the only place the hook is visible from outside: it
            // says the sources were declared, that this version's hook found them, and which atlas
            // they landed in, which is not the same id on every version.
            dev.golem.core.GolemCore.LOG.info("Added {} declared sprite source(s) to {}",
                    declared.size(), atlas);
        }
    }
}
