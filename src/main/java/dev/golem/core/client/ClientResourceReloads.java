package dev.golem.core.client;

import dev.golem.core.Ids;

/** Registers one client resource-reload callback without exposing loader or era event APIs to consumers. */
public final class ClientResourceReloads {

    private ClientResourceReloads() {}

    /** Registers a callback that runs after the client resource manager reloads. */
    public static void register(String namespace, String path, Runnable callback) {
        //? if fabric {
        /*net.fabricmc.fabric.api.resource.ResourceManagerHelper
                .get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override
                    public
                            //? if >=26.1 {
                            net.minecraft.resources.Identifier
                            //?} else {
                            /^net.minecraft.resources.ResourceLocation^/
                            //?}
                            getFabricId() {
                        return Ids.of(namespace, path);
                    }

                    @Override
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resources) {
                        callback.run();
                    }
                });
        *///?} elif neoforge {
        //? if >=26.1 {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) ->
                        event.addListener(Ids.of(namespace, path),
                                new net.minecraft.server.packs.resources.ResourceManagerReloadListener() {
                                    @Override
                                    public void onResourceManagerReload(
                                            net.minecraft.server.packs.resources.ResourceManager resources) {
                                        callback.run();
                                    }
                                }));
        //?} else {
        /*dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                        event.registerReloadListener(
                                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources ->
                                        callback.run()));
        *///?}
        //?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) ->
                        event.registerReloadListener(
                                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources ->
                                        callback.run()));
        *///?}
    }
}
