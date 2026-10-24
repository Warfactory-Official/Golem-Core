package dev.golem.core.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.golem.core.GolemCore;
import dev.golem.core.registry.DeferredRegister;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.jspecify.annotations.Nullable;

/**
 * Entity loot that scales with the killer's looting. The number provider {@code golemcore:looting} is
 * {@code add + factor * level}, the looting level of the entity that dealt the killing blow, or 0 when
 * {@code player_only} is set and that was not a player. {@link #roll} rolls a table as a death would.
 */
public final class EntityLoot {

    private static boolean registered;

    private EntityLoot() {}

    public record Looting(int factor, int add, boolean playerOnly) implements NumberProvider {

        public static final MapCodec<Looting> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("factor", 1).forGetter(Looting::factor),
                Codec.INT.optionalFieldOf("add", 0).forGetter(Looting::add),
                Codec.BOOL.optionalFieldOf("player_only", false).forGetter(Looting::playerOnly)
        ).apply(i, Looting::new));

        //? if >=1.20.5 && <26.1 {
        /*private static final net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType TYPE =
                new net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType(CODEC);
        *///?} elif <1.20.5 {
        /*private static final net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType TYPE =
                new net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType(new LootJson<>(CODEC));
        *///?}

        @Override
        public float getFloat(LootContext context) {
            Entity killer = killer(context);
            boolean counts = killer instanceof LivingEntity && (!playerOnly || killer instanceof Player);
            return add + factor * (counts ? Compat.lootingLevel(context.getLevel(), (LivingEntity) killer) : 0);
        }

        //? if >=26.1 {
        @Override
        public MapCodec<? extends NumberProvider> codec() {
            return CODEC;
        }
        //?} else {
        /*@Override
        public net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType getType() {
            return TYPE;
        }
        *///?}
    }

    /** Registers {@code golemcore:looting}; a mod whose tables use it calls this during construction. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        //? if >=26.1 {
        DeferredRegister.create(() -> Registries.LOOT_NUMBER_PROVIDER_TYPE, GolemCore.MOD_ID).register("looting", () -> Looting.CODEC);
        //?} else {
        /*DeferredRegister.create(() -> Registries.LOOT_NUMBER_PROVIDER_TYPE, GolemCore.MOD_ID).register("looting", () -> Looting.TYPE);
        *///?}
    }

    /** Rolls the entity loot table {@code table} for {@code victim} killed by {@code source}, {@code player} credited. */
    public static List<ItemStack> roll(ServerLevel level, String table, LivingEntity victim, DamageSource source,
                                       @Nullable Player player) {
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source);
        //? if >=1.20.5 {
        params.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
        //?} else {
        /*params.withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity());
        *///?}
        if (player != null) params.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).withLuck(player.getLuck());
        return new ArrayList<>(ChestLoot.table(level, table).getRandomItems(params.create(LootContextParamSets.ENTITY)));
    }

    private static @Nullable Entity killer(LootContext context) {
        //? if >=26.1 {
        return context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY);
        //?} elif >=1.20.5 {
        /*return context.getParamOrNull(LootContextParams.ATTACKING_ENTITY);
        *///?} else {
        /*return context.getParamOrNull(LootContextParams.KILLER_ENTITY);
        *///?}
    }
}
