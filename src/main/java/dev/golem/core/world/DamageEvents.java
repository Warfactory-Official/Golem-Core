package dev.golem.core.world;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side living damage hooks with one event shape for every supported loader.
 *
 * <p>The damage context carries the flat threshold and percentage piercing values used by HBM. Core
 * does not interpret those values; it only exposes them and applies percentage piercing to vanilla's
 * armor reduction where the loader provides that reduction callback.
 */
public final class DamageEvents {

    private DamageEvents() {}

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final dev.golem.core.hook.Hook<Consumer<DamageEvent>> ATTACK = dev.golem.core.hook.Hook.notifying();
    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final dev.golem.core.hook.Hook<Consumer<DamageEvent>> HURT = dev.golem.core.hook.Hook.notifying();
    private static final ThreadLocal<Piercing> PIERCING = new ThreadLocal<>();
    private static boolean installed;

    public static void onAttack(Consumer<DamageEvent> handler) {
        ATTACK.register(handler);
    }

    public static void onHurt(Consumer<DamageEvent> handler) {
        HURT.register(handler);
    }

    /** Runs a damage call with HBM-style flat-threshold and percentage piercing context. */
    public static void withPiercing(float threshold, float resistance, Runnable action) {
        Piercing previous = PIERCING.get();
        PIERCING.set(new Piercing(threshold, resistance));
        try {
            action.run();
        } finally {
            if (previous == null) PIERCING.remove();
            else PIERCING.set(previous);
        }
    }

    private static DamageEvent event(LivingEntity entity, DamageSource source, float amount) {
        Piercing piercing = PIERCING.get();
        return new DamageEvent(entity, source, amount,
                piercing == null ? 0F : piercing.threshold,
                piercing == null ? 0F : piercing.resistance);
    }

    private static void dispatchAttack(DamageEvent event) {
        for (Consumer<DamageEvent> handler : ATTACK) handler.accept(event);
    }

    private static void dispatchHurt(DamageEvent event) {
        for (Consumer<DamageEvent> handler : HURT) handler.accept(event);
    }

    /** Installs the loader event bridge once. Call after the loader has constructed its event bus. */
    public static void install() {
        if (installed) return;
        installed = true;

        //? if fabric {
        /*net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (REPLAY.get()) return true;

                    DamageEvent damage = event(entity, source, amount);
                    dispatchAttack(damage);
                    if (damage.isCanceled()) return false;

                    dispatchHurt(damage);
                    if (damage.isCanceled()) return false;
                    compensateArmorForPiercing(damage);
                    if (damage.amount() == amount) return true;

                    // Fabric exposes this hook before armor but does not expose a mutable amount. Re-enter
                    // vanilla once with the adjusted amount; the guard prevents a second custom dispatch.
                    REPLAY.set(true);
                    try {
                        entity.hurt(source, damage.amount());
                    } finally {
                        REPLAY.remove();
                    }
                    return false;
                });
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) -> {
                    if (event.isCanceled()) return;
                    DamageEvent damage = event(event.getEntity(), event.getSource(), event.getAmount());
                    dispatchAttack(damage);
                    if (damage.isCanceled()) {
                        event.setCanceled(true);
                        return;
                    }
                    dispatchHurt(damage);
                    if (damage.isCanceled()) {
                        event.setCanceled(true);
                        return;
                    }
                    event.setAmount(damage.amount());
                    if (damage.piercingResistance() != 0F) {
                        event.addReductionModifier(
                                net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR,
                                (container, reduction) -> reduction * (1F - damage.piercingResistance()));
                    }
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.living.LivingAttackEvent event) -> {
                    DamageEvent damage = event(event.getEntity(), event.getSource(), event.getAmount());
                    dispatchAttack(damage);
                    if (damage.isCanceled()) event.setCanceled(true);
                });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.living.LivingHurtEvent event) -> {
                    if (event.isCanceled()) return;
                    DamageEvent damage = event(event.getEntity(), event.getSource(), event.getAmount());
                    dispatchHurt(damage);
                    if (damage.isCanceled()) {
                        event.setCanceled(true);
                        return;
                    }
                    compensateArmorForPiercing(damage);
                    event.setAmount(damage.amount());
                });
        *///?}
    }

    /**
     * Fabric and Forge expose mutable damage before vanilla armor but no armor-reduction callback. Feed
     * vanilla the inverse input that produces the same result as applying the effective armor value after
     * HBM's PDR. NeoForge uses its native reduction callback above, so its generated method is empty.
     */
    private static void compensateArmorForPiercing(DamageEvent damage) {
        //? if fabric && >=1.21.1 {
        /*compensate(damage,
                (entity, amount, source, armor, toughness) ->
                        net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(
                                entity, amount, source, armor, toughness));
        *///?} elif fabric {
        /*compensate(damage,
                (entity, amount, source, armor, toughness) ->
                        net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(amount, armor, toughness));
        *///?} elif forge {
        /*compensate(damage,
                (entity, amount, source, armor, toughness) ->
                        net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(amount, armor, toughness));
        *///?}
    }

    private static void compensate(DamageEvent damage, ArmorFormula formula) {
        if (damage.piercingResistance() == 0F
                || damage.source().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) return;

        float armor = damage.entity().getArmorValue();
        if (armor <= 0F) return;
        float toughness = (float) damage.entity().getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        float target = formula.apply(damage.entity(), damage.amount(), damage.source(),
                armor * (1F - damage.piercingResistance()), toughness);

        float low = 0F;
        float high = Math.max(damage.amount(), target) + armor + toughness + 1F;
        for (int i = 0; i < 24; i++) {
            float middle = (low + high) * 0.5F;
            if (formula.apply(damage.entity(), middle, damage.source(), armor, toughness) < target) low = middle;
            else high = middle;
        }
        damage.setAmount((low + high) * 0.5F);
    }

    @FunctionalInterface
    private interface ArmorFormula {
        float apply(LivingEntity entity, float amount, DamageSource source, float armor, float toughness);
    }

    /** Mutable, loader-neutral damage event passed to HBM and other Core consumers. */
    public static final class DamageEvent {
        private final LivingEntity entity;
        private final DamageSource source;
        private final float originalAmount;
        private final float piercingThreshold;
        private final float piercingResistance;
        private float amount;
        private boolean canceled;

        private DamageEvent(LivingEntity entity, DamageSource source, float amount,
                            float piercingThreshold, float piercingResistance) {
            this.entity = entity;
            this.source = source;
            this.originalAmount = amount;
            this.amount = amount;
            this.piercingThreshold = piercingThreshold;
            this.piercingResistance = piercingResistance;
        }

        public LivingEntity entity() { return entity; }
        public DamageSource source() { return source; }
        public float originalAmount() { return originalAmount; }
        public float amount() { return amount; }
        public void setAmount(float amount) { this.amount = amount; }
        public void cancel() { this.canceled = true; }
        public boolean isCanceled() { return canceled; }
        public float piercingThreshold() { return piercingThreshold; }
        public float piercingResistance() { return piercingResistance; }
    }

    private record Piercing(float threshold, float resistance) {}

    // Fabric's cancellation-and-replay bridge must not dispatch this event twice.
    //? if fabric {
    /*private static final ThreadLocal<Boolean> REPLAY = ThreadLocal.withInitial(() -> false);
    *///?}
}
