package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side living death hooks with one shape on every loader.
 *
 * <p>{@link #onAllowDeath} handlers run first, at the loader's highest priority, and any of them may
 * veto the death by returning {@code false}, the legacy "cancel {@code LivingDeathEvent} and restore
 * health" revive pattern. {@link #onDeath} handlers run only for deaths nobody vetoed, before drops.
 */
public final class Deaths {

    private Deaths() {}

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<BiPredicate<LivingEntity, DamageSource>> ALLOW = Hook.notifying();
    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<BiConsumer<LivingEntity, DamageSource>> AFTER = Hook.notifying();
    private static boolean installed;

    /** Registers a veto: return {@code false} to cancel the death (the handler must restore health). */
    public static void onAllowDeath(BiPredicate<LivingEntity, DamageSource> handler) {
        ALLOW.register(handler);
    }

    /** Registers a notification for a death that was not vetoed. */
    public static void onDeath(BiConsumer<LivingEntity, DamageSource> handler) {
        AFTER.register(handler);
    }

    private static boolean allow(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) return true;
        for (BiPredicate<LivingEntity, DamageSource> handler : ALLOW) {
            if (!handler.test(entity, source)) return false;
        }
        return true;
    }

    private static void after(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) return;
        for (BiConsumer<LivingEntity, DamageSource> handler : AFTER) handler.accept(entity, source);
    }

    /** Installs the loader bridge once. Call after the loader has constructed its event bus. */
    public static void install() {
        if (installed) return;
        installed = true;

        //? if fabric {
        /*net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register(
                (entity, source, amount) -> allow(entity, source));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(Deaths::after);
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.HIGHEST,
                (net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
                    if (event.isCanceled()) return;
                    if (!allow(event.getEntity(), event.getSource())) event.setCanceled(true);
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.LOWEST,
                (net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
                    if (!event.isCanceled()) after(event.getEntity(), event.getSource());
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
                (net.minecraftforge.event.entity.living.LivingDeathEvent event) -> {
                    if (event.isCanceled()) return;
                    if (!allow(event.getEntity(), event.getSource())) event.setCanceled(true);
                });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                (net.minecraftforge.event.entity.living.LivingDeathEvent event) -> {
                    if (!event.isCanceled()) after(event.getEntity(), event.getSource());
                });
        *///?}
    }
}
