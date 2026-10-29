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
 * Adds live world rules after vanilla's dimension, biome, timeline and weather attribute layers.
 */
public final class WorldAttributes {
    private WorldAttributes() {
    }

    @FunctionalInterface
    public interface Rule {
        boolean apply(Level level, BlockPos pos, boolean value);
    }

    public static final Hook<Rule> BED_SLEEP = rules();
    public static final Hook<Rule> BED_SPAWN = rules();
    public static final Hook<Rule> MONSTERS_BURN = rules();

    static {
        //? if fabric && <26.1 {
        /*net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, pos) ->
                BED_SPAWN.invoker().apply(player.level(), pos, true));
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, pos, vanilla) -> {
            boolean allowed = BED_SLEEP.invoker().apply(player.level(), pos, vanilla);
            return allowed == vanilla ? InteractionResult.PASS : allowed ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        });
        *///?} elif neoforge && <26.1 {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent event) -> {
                    var problem = event.getProblem();
                    if (problem != null && problem != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) return;
                    boolean allowed = BED_SLEEP.invoker().apply(event.getLevel(), event.getPos(), problem == null);
                    event.setProblem(allowed ? null : Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
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
        attributes.addPositionalLayer(EnvironmentAttributes.WATER_EVAPORATES,
                (vanilla, pos, _) -> Evaporation.water(level, BlockPos.containing(pos), vanilla));
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
