package dev.golem.core.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * Mob spawning across the eras: running a freshly constructed mob's spawn initialisation, and refusing natural
 * spawns at runtime ({@link #onSpawnCheck}).
 *
 * <p>Running a freshly constructed mob's spawn initialisation — 1.7 {@code onSpawnWithEgg(null)} — from outside
 * the mob, across the {@code finalizeSpawn} signature changes: 1.20.1 took a trailing NBT argument, 1.21.1
 * dropped it, 26.1 renamed the spawn-reason type.
 */
public final class Spawns {

    private Spawns() {}

    /** Equips and initialises {@code mob} as a triggered spawn at its current position (1.7 {@code onSpawnWithEgg}). */
    public static void finalizeTriggered(Mob mob, ServerLevel level) {
        //? if >=26.1 {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                net.minecraft.world.entity.EntitySpawnReason.TRIGGERED, null);
        //?} else if >=1.21.1 {
        /*mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.TRIGGERED, null);
        *///?} else {
        /*mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.TRIGGERED, null, null);
        *///?}
    }
    /** Why a spawn check is asked. Spawners, eggs, breeding and commands are {@link #OTHER} and never reach a check. */
    public enum Reason {
        /** The periodic natural spawner (1.7 {@code SpawnerAnimals.findChunksForSpawning}). */
        NATURAL,
        /** Animals placed while a chunk generates (1.7 {@code SpawnerAnimals.performWorldGenSpawning}). */
        CHUNK_GENERATION,
        OTHER
    }

    /** A runtime veto on natural spawns: true refuses the spawn of {@code mob} at its current position. */
    @FunctionalInterface
    public interface SpawnCheck {
        boolean deny(Mob mob, ServerLevel level, Reason reason);
    }

    private static final java.util.List<SpawnCheck> CHECKS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean hooked;

    /**
     * Registers a veto asked for every natural and chunk-generation spawn, after the vanilla placement rules - the
     * modern stand-in for 1.7's {@code LivingSpawnEvent.CheckSpawn} (natural) and {@code PopulateChunkEvent.Populate}
     * {@code ANIMALS} (generation). Forge-like loaders answer it from {@code MobSpawnEvent.PositionCheck} (natural and
     * chunk-generation spawns only; spawner checks are passed through so every loader agrees); Fabric, which has no
     * spawn event, from {@code MixinNaturalSpawnerCheck} at the same two call sites.
     */
    public static synchronized void onSpawnCheck(SpawnCheck check) {
        CHECKS.add(check);
        if (hooked) return;
        hooked = true;
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck event) -> {
                    if (event.getLevel() instanceof net.minecraft.world.level.ServerLevelAccessor accessor
                            && denied(event.getEntity(), accessor.getLevel(), reason(event.getSpawnType()))) {
                        event.setResult(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL);
                    }
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck event) -> {
                    if (denied(event.getEntity(), event.getLevel().getLevel(), reason(event.getSpawnType()))) {
                        event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                    }
                });
        *///?}
    }

    // ------------------------------------------------------------------------------------------- potential spawns

    /** A notification that the natural spawner asks for the spawn list of {@code category} at {@code pos}. */
    @FunctionalInterface
    public interface PotentialSpawnsHook {
        void onPotentialSpawns(ServerLevel level, net.minecraft.world.entity.MobCategory category, net.minecraft.core.BlockPos pos);
    }

    private static final java.util.List<PotentialSpawnsHook> POTENTIAL = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean potentialHooked;

    /**
     * Registers a hook run every time the natural spawner reads the spawn list at a position (vanilla's private
     * {@code NaturalSpawner.mobsAt}, asked once to pick a group entry and once per position to confirm it) - the
     * modern stand-in for 1.7's {@code WorldEvent.PotentialSpawns}, which {@code WorldServer.spawnRandomCreature} and
     * {@code canCreatureTypeSpawnHere} fired at the same two places. Forge-like loaders answer from their own
     * {@code LevelEvent.PotentialSpawns}, which they fire from {@code mobsAt}; Fabric from
     * {@code MixinNaturalSpawnerCheck}'s HEAD inject on {@code mobsAt}. Notification only: the list is not changed.
     */
    public static synchronized void onPotentialSpawns(PotentialSpawnsHook hook) {
        POTENTIAL.add(hook);
        if (potentialHooked) return;
        potentialHooked = true;
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.LevelEvent.PotentialSpawns event) -> {
                    if (event.getLevel() instanceof ServerLevel level) potentialSpawns(level, event.getMobCategory(), event.getPos());
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.level.LevelEvent.PotentialSpawns event) -> {
                    if (event.getLevel() instanceof ServerLevel level) potentialSpawns(level, event.getMobCategory(), event.getPos());
                });
        *///?}
    }

    /** Runs the potential-spawn hooks. Called by the loader hooks. */
    public static void potentialSpawns(ServerLevel level, net.minecraft.world.entity.MobCategory category, net.minecraft.core.BlockPos pos) {
        if (POTENTIAL.isEmpty()) return;
        for (PotentialSpawnsHook hook : POTENTIAL) hook.onPotentialSpawns(level, category, pos);
    }

    /** True when a registered check refuses the spawn. Called by the loader hooks; OTHER spawns are never refused. */
    public static boolean denied(Mob mob, ServerLevel level, Reason reason) {
        if (reason == Reason.OTHER || CHECKS.isEmpty()) return false;
        for (SpawnCheck check : CHECKS) {
            if (check.deny(mob, level, reason)) return true;
        }
        return false;
    }

    //? if >=26.1 {
    public static Reason reason(net.minecraft.world.entity.EntitySpawnReason type) {
        return type == net.minecraft.world.entity.EntitySpawnReason.NATURAL ? Reason.NATURAL
                : type == net.minecraft.world.entity.EntitySpawnReason.CHUNK_GENERATION ? Reason.CHUNK_GENERATION
                : Reason.OTHER;
    }
    //?} else {
    /*public static Reason reason(net.minecraft.world.entity.MobSpawnType type) {
        return type == net.minecraft.world.entity.MobSpawnType.NATURAL ? Reason.NATURAL
                : type == net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION ? Reason.CHUNK_GENERATION
                : Reason.OTHER;
    }
    *///?}

    /** One natural spawn entry of a biome, in neutral form. */
    public record SpawnEntry(net.minecraft.world.entity.EntityType<?> type, int weight, int minCount, int maxCount) {}

    /** The natural spawn entries {@code biome} lists for {@code category} (after biome modifiers). */
    public static java.util.List<SpawnEntry> entries(net.minecraft.world.level.biome.Biome biome,
                                                     net.minecraft.world.entity.MobCategory category) {
        java.util.List<SpawnEntry> out = new java.util.ArrayList<>();
        //? if >=26.1 {
        for (var weighted : biome.getMobSettings().getMobs(category).unwrap()) {
            out.add(new SpawnEntry(weighted.value().type(), weighted.weight(), weighted.value().minCount(),
                    weighted.value().maxCount()));
        }
        //?} else {
        /*for (var data : biome.getMobSettings().getMobs(category).unwrap()) {
            out.add(new SpawnEntry(data.type, data.getWeight().asInt(), data.minCount, data.maxCount));
        }
        *///?}
        return out;
    }

    // ------------------------------------------------------------------------------------------- spawn placements

    /** Where a natural spawn of a type may be placed (vanilla's {@code SpawnPlacementType}). */
    public enum Placement { ON_GROUND, IN_WATER }

    /** The type-level spawn rule run before the mob's own {@code checkSpawnRules} (vanilla's {@code SpawnPredicate}). */
    public enum Rule {
        /** {@code Monster.checkMonsterSpawnRules}: not peaceful, dark enough, a valid block below. */
        MONSTER,
        /** {@code Mob.checkMobSpawnRules}: a valid block below. */
        MOB,
        /** A liquid below the spawn position (1.7 {@code SpawnerAnimals} for water creatures). */
        LIQUID_BELOW
    }

    private record PlacementEntry(java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<? extends Mob>> type,
                                  Placement placement, Rule rule) {}

    private static final java.util.List<PlacementEntry> PLACEMENTS = new java.util.ArrayList<>();
    private static boolean placementsHooked;

    /**
     * Declares the natural-spawn placement and type rule of a mod mob. Forge-like loaders deliver it from their spawn
     * placement registration event; Fabric, which has none, through {@link #flushFabricPlacements} once the types exist.
     */
    public static synchronized void placement(java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<? extends Mob>> type,
                                              Placement placement, Rule rule) {
        PLACEMENTS.add(new PlacementEntry(type, placement, rule));
        if (placementsHooked) return;
        placementsHooked = true;
        //? if neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) -> {
                    for (PlacementEntry entry : PLACEMENTS) registerNeoForge(event, entry);
                });
        //?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.event.entity.SpawnPlacementRegisterEvent event) -> {
                    for (PlacementEntry entry : PLACEMENTS) registerForge(event, entry);
                });
        *///?}
    }

    /** Fabric only: registers every declared placement (vanilla's private register, access-widened). No-op elsewhere. */
    public static void flushFabricPlacements() {
        //? if fabric {
        /*for (PlacementEntry entry : PLACEMENTS) registerVanilla(entry);
        *///?}
    }

    @SuppressWarnings("unchecked")
    private static <T extends Mob> net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> predicate(Rule rule) {
        return switch (rule) {
            case MONSTER -> (type, level, reason, pos, random) -> net.minecraft.world.entity.monster.Monster.checkMonsterSpawnRules(
                    (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.monster.Monster>)
                            (net.minecraft.world.entity.EntityType<?>) type, level, reason, pos, random);
            case MOB -> (type, level, reason, pos, random) -> Mob.checkMobSpawnRules(type, level, reason, pos, random);
            case LIQUID_BELOW -> (type, level, reason, pos, random) -> !level.getFluidState(pos.below()).isEmpty();
        };
    }

    //? if >=1.20.5 {
    private static net.minecraft.world.entity.SpawnPlacementType placementType(Placement placement) {
        return placement == Placement.IN_WATER ? net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER
                : net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND;
    }
    //?} else {
    /*private static net.minecraft.world.entity.SpawnPlacements.Type placementType(Placement placement) {
        return placement == Placement.IN_WATER ? net.minecraft.world.entity.SpawnPlacements.Type.IN_WATER
                : net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND;
    }
    *///?}

    //? if neoforge {
    @SuppressWarnings("unchecked")
    private static <T extends Mob> void registerNeoForge(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event,
                                                         PlacementEntry entry) {
        event.register((net.minecraft.world.entity.EntityType<T>) entry.type().get(), placementType(entry.placement()),
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, predicate(entry.rule()),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
    //?} elif forge {
    /*@SuppressWarnings("unchecked")
    private static <T extends Mob> void registerForge(net.minecraftforge.event.entity.SpawnPlacementRegisterEvent event,
                                                      PlacementEntry entry) {
        event.register((net.minecraft.world.entity.EntityType<T>) entry.type().get(), placementType(entry.placement()),
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, predicate(entry.rule()),
                net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
    *///?} else {
    /*@SuppressWarnings("unchecked")
    private static <T extends Mob> void registerVanilla(PlacementEntry entry) {
        net.minecraft.world.entity.SpawnPlacements.register((net.minecraft.world.entity.EntityType<T>) entry.type().get(),
                placementType(entry.placement()), net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                predicate(entry.rule()));
    }
    *///?}
}
