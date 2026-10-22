package dev.golem.core.sable;

import dev.golem.core.world.SubLevels;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link SubLevels.MassRule}s as Sable block masses, applied on server levels right after Sable re-applies its own data.
 * Blocks Sable's data names keep its mass: its datapack sync re-applies that data later, on the client thread.
 */
final class SableBlockMasses {

    private static final ResourceLocation MASS = Sable.sablePath("mass");

    private SableBlockMasses() {}

    static void install() {
        SableEventPlatform.INSTANCE.onSubLevelContainerReady((level, container) -> {
            if (!level.isClientSide()) apply();
        });
    }

    private static void apply() {
        double iron = PhysicsBlockPropertyHelper.getMass(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                Blocks.IRON_BLOCK.defaultBlockState());
        Set<Block> named = namedByData();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (named.contains(block)) continue;
            double relative = SubLevels.massInIronBlocks(block);
            if (Double.isNaN(relative)) continue;
            PhysicsBlockPropertiesDefinitionLoader.applyToBlocks(new PhysicsBlockPropertiesDefinition(
                    new ExtraCodecs.TagOrElementLocation(BuiltInRegistries.BLOCK.getKey(block), false), 0,
                    Map.of(MASS, iron * relative), Optional.empty()));
        }
    }

    /** Every block a loaded Sable data file sets a mass for, by name or by tag. */
    private static Set<Block> namedByData() {
        Set<Block> named = new HashSet<>();
        for (PhysicsBlockPropertiesDefinition definition : PhysicsBlockPropertiesDefinitionLoader.INSTANCE.getDefinitions()) {
            boolean setsMass = definition.properties().containsKey(MASS) || definition.overrides()
                    .map(overrides -> overrides.values().stream().anyMatch(set -> set.containsKey(MASS))).orElse(false);
            if (!setsMass) continue;
            ExtraCodecs.TagOrElementLocation selector = definition.selector();
            if (selector.tag()) {
                BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, selector.id()))
                        .ifPresent(tag -> tag.forEach(holder -> named.add(holder.value())));
            } else {
                BuiltInRegistries.BLOCK.getOptional(selector.id()).ifPresent(named::add);
            }
        }
        return named;
    }
}
