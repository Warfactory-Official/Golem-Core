package dev.golem.core.registry;

import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Adds entries to a creative tab without naming the loader's event.
 *
 * <p>Vanilla makes {@code CreativeModeTab.Output} and {@code TabVisibility} protected, so tab contents
 * cannot be written from loader-neutral code at all: every consumer would need a per-loader lambda.
 * fabric-api's hook is also named differently in the 26.1 line ({@code ItemGroupEvents.modifyEntriesEvent}
 * below, {@code CreativeModeTabEvents.modifyOutputEvent} from 26.1); same event, same accept overloads.
 *
 * <p>{@link Entries} is the neutral seam: consumers accept stacks through it and never touch vanilla's
 * protected types.
 */
public final class Tabs {

    private Tabs() {}

    /** Somewhere to put items, without exposing vanilla's protected {@code Output}. */
    @FunctionalInterface
    public interface Entries {
        void accept(ItemStack stack);
    }

    /** Registers a callback that contributes entries to one tab. */
    public static void modifyEntries(ResourceKey<CreativeModeTab> tab, Consumer<Entries> contents) {
        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(tab)
                .register(output -> contents.accept(output::accept));
        *///?} elif fabric {
        /*// Same event under its older name; the entries object exposes the same accept overloads.
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(tab)
                .register(output -> contents.accept(output::accept));
        *///?} elif neoforge {
        // Golem Core's own mod bus, the same one Tints subscribes to. The event is dispatched to every
        // container and carries the tab key, so listening from here reaches all tabs; only the
        // attribution differs, and nothing reads that.
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
                    if (event.getTabKey().equals(tab)) contents.accept(event::accept);
                });
        //?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) -> {
                    if (event.getTabKey().equals(tab)) contents.accept(event::accept);
                });
        *///?}
    }
}
