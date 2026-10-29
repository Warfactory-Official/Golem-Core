package dev.golem.core.world;

import dev.golem.core.hook.Hook;

import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
//? if >=26.1 {
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
//?}

/**
 * Live world rules. Each rule gets vanilla's answer at the block and returns the one to use. On 26.1 they are
 * attribute layers after the dimension, biome, timeline and weather layers. Before 26.1 a bed's base is the
 * dimension's natural() (bedWorks for a saved bed), sleep also needs night and burning gets Level.isDay().
 * Either bed rule opens a bed the base keeps shut.
 */
public final class WorldAttributes {
    private WorldAttributes() {
    }

    @FunctionalInterface
    public interface Rule {
        boolean apply(Level level, BlockPos pos, boolean value);
    }

    /** Whether a bed may be slept in now, also asked each tick to keep a sleeper asleep. */
    public static final Hook<Rule> BED_SLEEP = rules();
    /** Whether a bed sets the spawn point, and at respawn whether a saved bed still works: false clears it. */
    public static final Hook<Rule> BED_SPAWN = rules();
    /** Whether daylight burns a sun-sensitive mob here, asked before the burn roll. */
    public static final Hook<Rule> MONSTERS_BURN = rules();

    static {
        //? if fabric && <26.1 {
        /*net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, pos) ->
                BED_SPAWN.invoker().apply(player.level(), pos, player.level().dimensionType().natural()));
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, pos, vanilla) -> {
            var level = player.level();
            boolean allowed = BED_SLEEP.invoker().apply(level, pos, level.dimensionType().natural() && vanilla);
            return allowed == vanilla ? InteractionResult.PASS : allowed ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        });
        *///?} elif neoforge && <26.1 {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent event) -> {
                    var problem = event.getProblem();
                    if (problem != null && problem != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) return;
                    var level = event.getLevel();
                    boolean allowed = BED_SLEEP.invoker().apply(level, event.getPos(),
                            level.dimensionType().natural() && problem == null);
                    event.setProblem(allowed ? null : Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
                    if (!allowed || problem == null || event.getEntity().isCreative()) return;
                    // vanilla returned at its time check, before the monster check a night sleep would run
                    var centre = net.minecraft.world.phys.Vec3.atBottomCenterOf(event.getPos());
                    var box = new net.minecraft.world.phys.AABB(centre.x - 8.0, centre.y - 5.0, centre.z - 8.0,
                            centre.x + 8.0, centre.y + 5.0, centre.z + 8.0);
                    if (!level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, box,
                            monster -> monster.isPreventingPlayerRest(event.getEntity())).isEmpty())
                        event.setProblem(Player.BedSleepingProblem.NOT_SAFE);
                });
        // Player.tick wakes a sleeper through this event, so the same rule keeps them asleep.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent event) -> {
                    var problem = event.getProblem();
                    if (!(event.getEntity() instanceof Player player)
                            || problem != null && problem != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) return;
                    var pos = player.getSleepingPos().orElse(player.blockPosition());
                    boolean allowed = BED_SLEEP.invoker().apply(player.level(), pos,
                            player.level().dimensionType().natural() && problem == null);
                    if (allowed != (problem == null)) event.setContinueSleeping(allowed);
                });
        *///?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.SleepingTimeCheckEvent event) -> {
                    var player = event.getEntity();
                    var level = player.level();
                    var pos = event.getSleepingLocation().orElse(player.blockPosition());
                    var result = event.getResult();
                    boolean current = result == net.minecraftforge.eventbus.api.Event.Result.ALLOW
                            || result == net.minecraftforge.eventbus.api.Event.Result.DEFAULT && !level.isDay();
                    boolean allowed = BED_SLEEP.invoker().apply(level, pos, level.dimensionType().natural() && current);
                    if (allowed != current) event.setResult(allowed
                            ? net.minecraftforge.eventbus.api.Event.Result.ALLOW : net.minecraftforge.eventbus.api.Event.Result.DENY);
                });
        *///?}
    }

    private static Hook<Rule> rules() {
        return Hook.of(listeners -> (level, pos, value) -> {
            for (var listener : listeners) value = listener.apply(level, pos, value);
            return value;
        });
    }

    //? if >=26.1 {
    public static final Hook<BiConsumer<Level, EnvironmentAttributeSystem.Builder>> LAYERS = Hook.notifying();

    public static void apply(Level level, EnvironmentAttributeSystem.Builder attributes) {
        attributes.addPositionalLayer(EnvironmentAttributes.MONSTERS_BURN,
                (vanilla, pos, _) -> MONSTERS_BURN.invoker().apply(level, BlockPos.containing(pos), vanilla));
        attributes.addPositionalLayer(EnvironmentAttributes.BED_RULE, (rule, pos, _) -> {
            var block = BlockPos.containing(pos);
            boolean baseSleep = rule.canSleep(level), baseSpawn = rule.canSetSpawn(level);
            boolean sleep = BED_SLEEP.invoker().apply(level, block, baseSleep);
            boolean spawn = BED_SPAWN.invoker().apply(level, block, baseSpawn);
            if (sleep == baseSleep && spawn == baseSpawn) return rule;
            return new BedRule(sleep ? BedRule.Rule.ALWAYS : BedRule.Rule.NEVER,
                    spawn ? BedRule.Rule.ALWAYS : BedRule.Rule.NEVER, rule.explodes(), rule.errorMessage());
        });
        for (var listener : LAYERS) listener.accept(level, attributes);
    }
    //?}
}
