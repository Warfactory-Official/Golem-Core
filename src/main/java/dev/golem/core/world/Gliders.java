package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Elytra flight from something other than an elytra worn in the chest slot - for example an elytra stored inside a
 * chestplate - with vanilla's behaviour: the same start (jump in mid-air), the same stop conditions, firework boosts,
 * vanilla's wear cadence (1 durability every 20 flight ticks, the glide game event every 10) and vanilla's wings.
 *
 * <p>Each era grants elytra flight differently: 26.1 asks {@code LivingEntity.canGlide} (the {@code glider}
 * component), Forge 1.20.1 and NeoForge 1.21.1 ask the chest item's {@code canElytraFly}/{@code elytraFlightTick},
 * Fabric fires {@code EntityElytraEvents.CUSTOM}. Fabric needs no mixin; the patched loaders and NeoForge 26.1 call
 * {@link #canGlide}, {@link #flightTick} and {@link #wearNow} from {@code MixinGliderFlight} (and, below 26.1, the
 * start checks in {@code MixinGliderStart}/{@code MixinGliderClientStart}). The wings come from vanilla's own wings
 * layer, which {@code MixinGliderWings}/{@code MixinGliderCape} point at {@link #wingsFor}.
 */
public final class Gliders {

    private Gliders() {}

    /** One way to glide without wearing an elytra. */
    public interface Source {
        /** Whether {@code entity} may glide now through this source (vanilla's slot/levitation checks are already done). */
        boolean canGlide(LivingEntity entity);

        /** Server side, where vanilla would damage a worn elytra: spend this source's durability. */
        void wear(LivingEntity entity);
    }

    /** The stack whose wings a chest item shows; return {@code chest} itself (or empty) for none. */
    @FunctionalInterface
    public interface Wings {
        ItemStack wings(ItemStack chest);
    }

    public static final Hook<Source> SOURCES = Hook.notifying();
    public static final Hook<Wings> WINGS = Hook.notifying();
    private static boolean installed;

    /** Registers a glide source (common code; both sides must agree for client prediction). */
    public static void register(Source source) {
        install();
        SOURCES.register(source);
    }

    /** Registers which wings a chest item shows (safe from common code; only read while rendering). */
    public static void registerWings(Wings wings) {
        WINGS.register(wings);
    }

    /** The first source that lets {@code entity} glide, or null. */
    public static Source source(LivingEntity entity) {
        for (Source source : SOURCES) {
            if (source.canGlide(entity)) return source;
        }
        return null;
    }

    /** Whether any source lets {@code entity} glide. */
    public static boolean canGlide(LivingEntity entity) {
        return source(entity) != null;
    }

    /**
     * The per-tick flight check of the legacy loaders ({@code ElytraItem.elytraFlightTick}'s cadence): true while a
     * source holds the entity up; every 20th flight tick the source wears, every 10th the glide game event fires.
     */
    public static boolean flightTick(LivingEntity entity, int flightTicks) {
        Source source = source(entity);
        if (source == null) return false;
        int next = flightTicks + 1;
        if (!entity.level().isClientSide() && next % 10 == 0) {
            if ((next / 10) % 2 == 0) source.wear(entity);
            entity.gameEvent(GameEvent.ELYTRA_GLIDE);
        }
        return true;
    }

    /**
     * 26.1's damage point in {@code updateFallFlying} (reached every 20 flight ticks on the server): wears the source
     * and fires the game event vanilla fires right after. False when no source is in use (vanilla goes on).
     */
    public static boolean wearNow(LivingEntity entity) {
        Source source = source(entity);
        if (source == null) return false;
        source.wear(entity);
        entity.gameEvent(GameEvent.ELYTRA_GLIDE);
        return true;
    }

    /** The stack vanilla's wings layer should draw for a chest item: a registered substitute, else the item itself. */
    public static ItemStack wingsFor(ItemStack chest) {
        if (chest == null || chest.isEmpty()) return chest;
        for (Wings wings : WINGS) {
            ItemStack shown = wings.wings(chest);
            if (shown != null && !shown.isEmpty() && shown != chest) return shown;
        }
        return chest;
    }

    /** Whether an elytra-like stack has flight left: vanilla stops one point short of breaking ({@code isFlyEnabled}). */
    public static boolean hasFlightLeft(ItemStack stack) {
        return !stack.isEmpty() && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage() - 1);
    }

    private static synchronized void install() {
        if (installed) return;
        installed = true;
        // 26.1 Fabric asks with tickElytra=true only at vanilla's damage point (every 20 flight ticks); legacy Fabric
        // ticks a custom elytra every flight tick, as ElytraItem.elytraFlightTick did.
        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents.CUSTOM.register((entity, tick) ->
                tick ? wearNow(entity) : canGlide(entity));
        *///?} elif fabric {
        /*net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents.CUSTOM.register((entity, tick) ->
                tick ? flightTick(entity, entity.getFallFlyingTicks()) : canGlide(entity));
        *///?}
    }
}
