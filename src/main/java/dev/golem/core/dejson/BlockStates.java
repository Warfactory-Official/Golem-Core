package dev.golem.core.dejson;

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
 * <p>Reading a state's properties by name is the one thing the declaration vocabulary needs from the
 * game and cannot express itself. It is done here through {@code getProperties} and
 * {@code getValue}, which are spelled the same on every target — {@code StateHolder.getValues}
 * is not, having become a {@code Stream} of property-value pairs on 26.1 where it was a {@code Map}
 * before.
 */
public final class BlockStates {

    private BlockStates() {}

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
     * <p>This is the failure a missing model actually is. Vanilla answers it at bake time on the
     * client, by which point the report is a texture-less cube in a world; answered here it is a
     * failing test that names the block and the states.
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
