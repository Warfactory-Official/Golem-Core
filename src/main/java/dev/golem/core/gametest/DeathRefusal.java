package dev.golem.core.gametest;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * A selected entity refuses the real loader death event after earlier listeners have run.
 */
public final class DeathRefusal {
    private static @Nullable LivingEntity target;
    public static int attempts;

    static {
        //? if fabric {
        /*net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity != target) return true;
            attempts++;
            entity.setHealth(entity.getMaxHealth());
            return false;
        });
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                (net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
                    if (event.getEntity() == target) {
                        attempts++;
                        event.getEntity().setHealth(event.getEntity().getMaxHealth());
                        event.setCanceled(true);
                    }
                });
        //?} else {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                (net.minecraftforge.event.entity.living.LivingDeathEvent event) -> {
                    if (event.getEntity() == target) {
                        attempts++;
                        event.getEntity().setHealth(event.getEntity().getMaxHealth());
                        event.setCanceled(true);
                    }
                });
        *///?}
    }

    private DeathRefusal() {
    }

    public static void hold(LivingEntity entity, Runnable action) {
        target = entity;
        attempts = 0;
        try {
            action.run();
        } finally {
            target = null;
        }
    }
}
