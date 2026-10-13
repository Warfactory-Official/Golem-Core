package dev.golem.core.dejson;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bridge between a real block and the Minecraft-free half of {@link Dejson}.
 *
 * <p>Reads a state's properties by name through {@code getProperties} and {@code getValue}, which are spelled the
 * same on every target. {@code StateHolder.getValues} is not: on 26.1 it is a {@code Stream} of property-value
 * pairs, before that a {@code Map}.
 */
public final class BlockStates {

    private BlockStates() {}

    /**
     * The registered block a declaration's id names, or {@code null} when nothing is registered
     * under it.
     *
     * <p>26.1 spells the lookup {@code getValue}, older targets {@code get}, and the defaulted block registry
     * answers an unregistered id with air there. Both are normalised so a missing block reads as null on every
     * node. {@code minecraft:air} itself cannot be looked up.
     */
    /**
     * Every registered block whose id sits in {@code namespace}, in registry order.
     *
     * <p>The block id type is renamed at 26.1, so callers sweeping their own blocks use this instead of an era guard.
     */
    public static List<Block> blocksIn(String namespace) {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            //? if >=26.1 {
            net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            //?} else {
            /*net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            *///?}
            if (id != null && id.getNamespace().equals(namespace)) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    public static Block block(String id) {
        //? if >=26.1 {
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(dev.golem.core.Ids.parse(id));
        //?} else {
        /*Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(dev.golem.core.Ids.parse(id));
        *///?}
        return block == null || block == net.minecraft.world.level.block.Blocks.AIR ? null : block;
    }

    /** A state as property name to value name, the form {@link Dispatch#covers} takes. */
    public static Map<String, String> properties(BlockState state) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            values.put(property.getName(), valueName(property, state));
        }
        return values;
    }

    /**
     * The states of {@code block} that no variant key selects.
     *
     * <p>A missing model otherwise shows up at client bake time as a texture-less cube; this names the block and
     * states in a failing test.
     */
    public static List<String> uncovered(Block block, Dispatch dispatch) {
        List<String> uncovered = new ArrayList<>();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            Map<String, String> properties = properties(state);
            if (!dispatch.covers(properties)) {
                uncovered.add(describe(properties));
            }
        }
        return uncovered;
    }

    /** The properties a dispatch names that the block does not have; a declaration that drifted. */
    public static List<String> unknownProperties(Block block, Dispatch dispatch) {
        List<String> unknown = new ArrayList<>();
        for (String property : dispatch.properties()) {
            if (block.getStateDefinition().getProperty(property) == null) {
                unknown.add(property);
            }
        }
        return unknown;
    }

    /** Every state of {@code block}, in the property-map form the checks take. */
    public static List<Map<String, String>> allStates(Block block) {
        List<Map<String, String>> states = new ArrayList<>();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            states.add(properties(state));
        }
        return states;
    }

    private static String describe(Map<String, String> properties) {
        StringBuilder description = new StringBuilder();
        properties.forEach((name, value) -> {
            if (description.length() > 0) {
                description.append(',');
            }
            description.append(name).append('=').append(value);
        });
        return description.length() == 0 ? "<no properties>" : description.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(Property<?> property, BlockState state) {
        return ((Property) property).getName(state.getValue(property));
    }
}
