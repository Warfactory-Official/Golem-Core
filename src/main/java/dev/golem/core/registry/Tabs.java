package dev.golem.core.registry;

//? if neoforge {
import dev.golem.core.platform.NeoForgeEntrypoint;
//?}
//? if neoforge {
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
//?}

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import dev.golem.core.world.ItemStacks;

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

    /**
     * Registers a callback that inserts stacks right before the anchor stack in one tab (1.7 addons slotting
     * vanilla-tab entries at authored positions). Handles resolve when the tab builds, not here. Copies of the
     * stacks already in the tab are moved, not duplicated.
     */
    public static void insertBefore(ResourceKey<CreativeModeTab> tab, Supplier<ItemStack> anchor,
            Supplier<List<ItemStack>> stacks) {
        //? if neoforge {
        // Golem Core's own mod bus, the same one Tints subscribes to.
        NeoForgeEntrypoint.modBus().addListener(
                (BuildCreativeModeTabContentsEvent event) -> {
                    if (!event.getTabKey().equals(tab)) return;
                    ItemStack before = anchor.get();
                    for (ItemStack stack : stacks.get()) {
                        // The tab pre-populates spawn eggs and the like: take the existing copy out first,
                        // or the positional insert refuses the duplicate.
                        event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                        event.insertBefore(before, stack,
                                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                    }
                });
        //?} elif fabric && >=26.1 {
        /*net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(tab)
                .register(output -> {
                    var moved = stacks.get();
                    removeCopies(output.getDisplayStacks(), moved);
                    removeCopies(output.getSearchTabStacks(), moved);
                    output.insertBefore(anchor.get(), moved);
                });
        *///?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) -> {
                    if (!event.getTabKey().equals(tab)) return;
                    var entries = event.getEntries();
                    var before = anchor.get();
                    for (var stack : stacks.get()) {
                        entries.remove(stack);
                        entries.putBefore(before, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                    }
        });
        *///?} else {
        /*net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(tab)
                .register(output -> {
                    var moved = stacks.get();
                    removeCopies(output.getDisplayStacks(), moved);
                    removeCopies(output.getSearchTabStacks(), moved);
                    output.addBefore(anchor.get(), moved);
        });
        *///?}
    }

    private static void removeCopies(List<ItemStack> entries, List<ItemStack> moved) {
        for (var iterator = entries.iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            for (var stack : moved)
                if (ItemStacks.sameItemAndData(entry, stack)) {
                    iterator.remove();
                    break;
                }
        }
    }
}
