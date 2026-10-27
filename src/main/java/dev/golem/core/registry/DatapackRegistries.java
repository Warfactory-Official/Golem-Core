package dev.golem.core.registry;

//? if forge {
/*import dev.golem.core.platform.ForgeEntrypoint;
*///?}
//? if neoforge {
import dev.golem.core.platform.NeoForgeEntrypoint;
//?}
//? if fabric {
/*import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
*///?}
//? if forge {
/*import net.minecraftforge.registries.DataPackRegistryEvent;
*///?}
//? if neoforge {
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
//?}

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** Declares a real server datapack registry whose entries vanilla also synchronizes to remote clients. */
public final class DatapackRegistries {
    private static final Set<ResourceKey<?>> DECLARED = new HashSet<>();
    private DatapackRegistries() {}

    public static synchronized <T> void registerSynced(ResourceKey<Registry<T>> key, Codec<T> codec) {
        if (!DECLARED.add(key)) throw new IllegalStateException("Datapack registry declared twice: " + key);
        //? if fabric {
        /*DynamicRegistries.registerSynced(key, codec);
        *///?} elif neoforge {
        NeoForgeEntrypoint.modBus().addListener(
                (DataPackRegistryEvent.NewRegistry event) ->
                        event.dataPackRegistry(key, codec, codec));
        //?} elif forge {
        /*ForgeEntrypoint.modBus().addListener(
                (DataPackRegistryEvent.NewRegistry event) ->
                        event.dataPackRegistry(key, codec, codec));
        *///?}
    }
}
