package dev.golem.core.client;

import dev.golem.core.render.CompatBERProvider;
import dev.golem.core.render.CompatRenderState;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Menu screens and block-entity / entity renderers, declared once from a mod's client start-up and bound at each
 * loader's own moment: NeoForge's {@code RegisterMenuScreensEvent} and {@code EntityRenderersEvent.RegisterRenderers},
 * Forge's client setup and {@code RegisterRenderers}, Fabric at once (its client entrypoints run after every registry
 * is filled). The declarations run inside that moment, so they may resolve registry objects ({@code .get()}).
 *
 * <pre>{@code
 * ClientRegistrations.screens(screens -> screens.register(MyMenus.FURNACE.get(), FurnaceScreen::new));
 * ClientRegistrations.renderers(renderers -> renderers.blockEntity(MyBlocks.FURNACE_BE.get(), FurnaceRenderer::new));
 * }</pre>
 */
public final class ClientRegistrations {

    private ClientRegistrations() {}

    /** Where screens are bound to menu types. */
    public interface Screens {
        <M extends AbstractContainerMenu, U extends AbstractContainerScreen<M>> void register(
                MenuType<M> menu, MenuScreens.ScreenConstructor<M, U> factory);
    }

    /** Where renderers are bound to block-entity and entity types. */
    public interface Renderers {
        <T extends BlockEntity, S extends CompatRenderState> void blockEntity(
                BlockEntityType<? extends T> type, CompatBERProvider<T, S> provider);

        <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider);
    }

    private static final List<Consumer<Screens>> SCREENS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Renderers>> RENDERERS = new CopyOnWriteArrayList<>();
    private static boolean hooked;

    /** Declares screens; {@code declarations} runs at the loader's screen-registration moment. */
    public static void screens(Consumer<Screens> declarations) {
        //? if fabric {
        /*declarations.accept(MenuScreens::register);
        *///?} else {
        SCREENS.add(declarations);
        hook();
        //?}
    }

    /** Declares renderers; {@code declarations} runs at the loader's renderer-registration moment. */
    public static void renderers(Consumer<Renderers> declarations) {
        //? if fabric {
        /*declarations.accept(new Renderers() {
            @Override
            public <T extends BlockEntity, S extends CompatRenderState> void blockEntity(
                    BlockEntityType<? extends T> type, CompatBERProvider<T, S> provider) {
                net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(type,
                        dev.golem.core.render.Renderers.toVanilla(provider));
            }

            @Override
            public <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(type, provider);
            }
        });
        *///?} else {
        RENDERERS.add(declarations);
        hook();
        //?}
    }

    //? if neoforge {
    private static synchronized void hook() {
        if (hooked) return;
        hooked = true;
        var bus = dev.golem.core.platform.NeoForgeEntrypoint.modBus();
        bus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) -> {
            for (Consumer<Screens> declarations : SCREENS) declarations.accept(event::register);
        });
        bus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> {
            Renderers sink = new Renderers() {
                @Override
                public <T extends BlockEntity, S extends CompatRenderState> void blockEntity(
                        BlockEntityType<? extends T> type, CompatBERProvider<T, S> provider) {
                    event.registerBlockEntityRenderer(type, dev.golem.core.render.Renderers.toVanilla(provider));
                }

                @Override
                public <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                    event.registerEntityRenderer(type, provider);
                }
            };
            for (Consumer<Renderers> declarations : RENDERERS) declarations.accept(sink);
        });
    }
    //?} elif forge {
    /*private static synchronized void hook() {
        if (hooked) return;
        hooked = true;
        var bus = dev.golem.core.platform.ForgeEntrypoint.modBus();
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            for (Consumer<Screens> declarations : SCREENS) declarations.accept(MenuScreens::register);
        }));
        bus.addListener((net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> {
            Renderers sink = new Renderers() {
                @Override
                public <T extends BlockEntity, S extends CompatRenderState> void blockEntity(
                        BlockEntityType<? extends T> type, CompatBERProvider<T, S> provider) {
                    event.registerBlockEntityRenderer(type, dev.golem.core.render.Renderers.toVanilla(provider));
                }

                @Override
                public <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                    event.registerEntityRenderer(type, provider);
                }
            };
            for (Consumer<Renderers> declarations : RENDERERS) declarations.accept(sink);
        });
    }
    *///?}
}
