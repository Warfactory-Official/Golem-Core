package dev.golem.core.client;

import dev.golem.core.render.CompatBERProvider;
import dev.golem.core.render.CompatRenderState;
import dev.golem.core.render.Renderers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Menu screens and renderers. A batch runs at the loader's registration moment, after registries flushed, so it may
 * resolve registry objects; declare during mod construction ({@code GolemMod.initClient}). Client-only.
 */
public final class ClientRegistry {

    private ClientRegistry() {}

    @FunctionalInterface
    public interface ScreenSink {
        <M extends AbstractContainerMenu, U extends AbstractContainerScreen<M>> void register(
                MenuType<M> menu, MenuScreens.ScreenConstructor<M, U> factory);
    }

    public interface RendererSink {
        <T extends BlockEntity, S extends CompatRenderState> void blockEntity(
                BlockEntityType<? extends T> type, CompatBERProvider<T, S> provider);

        <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider);
    }

    public static <M extends AbstractContainerMenu, U extends AbstractContainerScreen<M>> void screen(
            Supplier<MenuType<M>> menu, MenuScreens.ScreenConstructor<M, U> factory) {
        screens(sink -> sink.register(menu.get(), factory));
    }

    public static <T extends BlockEntity, S extends CompatRenderState> void blockEntityRenderer(
            Supplier<? extends BlockEntityType<? extends T>> type, CompatBERProvider<T, S> provider) {
        renderers(sink -> sink.blockEntity(type.get(), provider));
    }

    public static <T extends Entity> void entityRenderer(Supplier<? extends EntityType<? extends T>> type,
            EntityRendererProvider<T> provider) {
        renderers(sink -> sink.entity(type.get(), provider));
    }

    public static void screens(Consumer<ScreenSink> registrations) {
        //? if fabric {
        /*registrations.accept(MenuScreens::register);
        *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) -> registrations.accept(event::register));
        //?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
                        event.enqueueWork(() -> registrations.accept(MenuScreens::register)));
        *///?}
    }

    public static void renderers(Consumer<RendererSink> registrations) {
        //? if fabric {
        /*registrations.accept(new RendererSink() {
            @Override
            public <T extends BlockEntity, S extends CompatRenderState> void blockEntity(
                    BlockEntityType<? extends T> type, CompatBERProvider<T, S> provider) {
                net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(type, Renderers.toVanilla(provider));
            }

            @Override
            public <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(type, provider);
            }
        });
        *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                        registrations.accept(new RendererSink() {
                            @Override
                            public <T extends BlockEntity, S extends CompatRenderState> void blockEntity(
                                    BlockEntityType<? extends T> type, CompatBERProvider<T, S> provider) {
                                event.registerBlockEntityRenderer(type, Renderers.toVanilla(provider));
                            }

                            @Override
                            public <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                                event.registerEntityRenderer(type, provider);
                            }
                        }));
        //?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                        registrations.accept(new RendererSink() {
                            @Override
                            public <T extends BlockEntity, S extends CompatRenderState> void blockEntity(
                                    BlockEntityType<? extends T> type, CompatBERProvider<T, S> provider) {
                                event.registerBlockEntityRenderer(type, Renderers.toVanilla(provider));
                            }

                            @Override
                            public <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                                event.registerEntityRenderer(type, provider);
                            }
                        }));
        *///?}
    }
}
