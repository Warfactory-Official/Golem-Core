package dev.golem.core.mixin;

// Below 26.1 vanilla dispatches a key to one mapping, picked by the iteration order of a name-keyed map, so a mod's
// mouse bind could take right-click from Use depending on which other mods are installed. Neither vanilla nor
// fabric-api lets a mapping yield a key, and an access widener alone has no hook to re-apply after every reset.
// Forge and NeoForge dispatch to every mapping; Golem mappings poll the device here, so losing the key costs them nothing.
//? if fabric && <26.1 {

/*import com.mojang.blaze3d.platform.InputConstants;
import dev.golem.core.client.KeyMappings;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class MixinLegacyKeyMappingWinner {

    @Shadow @Final private static Map<String, KeyMapping> ALL;
    @Shadow @Final private static Map<InputConstants.Key, KeyMapping> MAP;
    @Shadow private InputConstants.Key key;

    @Inject(method = "resetMapping", at = @At("TAIL"))
    private static void golem$yieldSharedKeys(CallbackInfo callback) {
        for (KeyMapping mapping : ALL.values()) {
            if (mapping instanceof KeyMappings.Mapping) continue;
            InputConstants.Key bound = ((MixinLegacyKeyMappingWinner) (Object) mapping).key;
            if (MAP.get(bound) instanceof KeyMappings.Mapping) MAP.put(bound, mapping);
        }
    }
}
*///?}
