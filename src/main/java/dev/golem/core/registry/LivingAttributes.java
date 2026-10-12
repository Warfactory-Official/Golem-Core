package dev.golem.core.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/**
 * Default attribute sets for custom living entities, declared once and delivered on every loader.
 *
 * <p>A {@code LivingEntity} subclass crashes on spawn unless its type has an attribute supplier. The
 * loaders collect those differently: NeoForge and Forge fire an {@code EntityAttributeCreationEvent}
 * on the mod bus after entity registration, Fabric exposes a registry the consumer writes into at any
 * time. Consumers declare the pair here from their common initializer, before registration flushes,
 * and never see which.
 *
 * <p>The mod-bus subscription is Golem Core's own bus, the same one {@link Tabs} uses: the loaders
 * dispatch the event to every mod container, and the registration is keyed by entity type, so which
 * bus receives it does not matter.
 */
public final class LivingAttributes {

    private LivingAttributes() {}

    private record Entry(Supplier<? extends EntityType<? extends LivingEntity>> type,
                         Supplier<AttributeSupplier.Builder> attributes) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static boolean installed;

    /** Declares {@code attributes} as the default attribute set of {@code type}. */
    public static void register(Supplier<? extends EntityType<? extends LivingEntity>> type,
                                Supplier<AttributeSupplier.Builder> attributes) {
        ENTRIES.add(new Entry(type, attributes));
        install();
    }

    private static void install() {
        if (installed) return;
        installed = true;
        //? if neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) -> {
                    for (Entry entry : ENTRIES) event.put(entry.type().get(), entry.attributes().get().build());
                });
        //?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.event.entity.EntityAttributeCreationEvent event) -> {
                    for (Entry entry : ENTRIES) event.put(entry.type().get(), entry.attributes().get().build());
                });
        *///?}
    }

    /**
     * Fabric only: writes every declared set into Fabric's default-attribute registry. Called by the
     * consumer's Fabric initializer after its registries have flushed, so the entity types resolve.
     * A no-op on the Forge-like loaders, whose event already delivered them.
     */
    public static void flushFabric() {
        //? if fabric {
        /*for (Entry entry : ENTRIES) {
            net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                    entry.type().get(), entry.attributes().get());
        }
        ENTRIES.clear(); // Golem Core flushes once (ModEntries); a second call registers nothing twice
        *///?}
    }
}
