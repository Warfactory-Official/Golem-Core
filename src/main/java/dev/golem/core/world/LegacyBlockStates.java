package dev.golem.core.world;

import com.mojang.serialization.Dynamic;
import dev.golem.core.data.Tags;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pre-flattening (1.12 and older) vanilla block states: a numeric block id plus its 4-bit metadata, as old
 * worlds and old structure files store them, upgraded to this version's {@link BlockState}.
 *
 * <p>This is vanilla's own path, not a hand table: {@code BlockStateData} holds Mojang's flattening map from
 * {@code id << 4 | meta} to the 1.13 state, and the data fixer then carries that 1.13 state through every later
 * rename up to the running version (so {@code tallgrass:1} becomes {@code grass} on 1.20.1 and
 * {@code short_grass} on 26.1). The fixer version the flattened tag enters at is 1450, the schema of vanilla's
 * {@code BlockStateStructureTemplateFix}, which is where structure templates go through the same map.
 */
public final class LegacyBlockStates {

    /** The schema whose fixes already produced {@code BlockStateData}'s output. */
    private static final int FLATTENED_VERSION = 1450;

    private LegacyBlockStates() {}

    /** The current state for legacy numeric id {@code id} with metadata {@code meta}; air for unknown pairs. */
    public static BlockState upgrade(int id, int meta) {
        if (id < 0 || id > 255 || meta < 0 || meta > 15) return Blocks.AIR.defaultBlockState();
        BlockState state = upgradeExact(id, meta);
        // A metadata value the block never had: some fixers (1.21.1 among them) mark it as the invalid name
        // "%%FILTER_ME%%", which vanilla then cannot parse. 1.12 read such a value as the block's meta-0 state.
        if (state == null && meta != 0) state = upgradeExact(id, 0);
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    private static BlockState upgradeExact(int id, int meta) {
        Dynamic<?> flattened = BlockStateData.getTag(id << 4 | meta);
        Tag tag = fix(flattened).convert(NbtOps.INSTANCE).getValue();
        if (!(tag instanceof CompoundTag compound)) return null;
        String name = Tags.getStringOr(compound, "Name", "");
        if (!name.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) return null;
        return read(compound);
    }

    private static <T> Dynamic<T> fix(Dynamic<T> input) {
        return DataFixers.getDataFixer().update(References.BLOCK_STATE, input, FLATTENED_VERSION, currentVersion());
    }

    private static int currentVersion() {
        //? if >=1.21.6 {
        return SharedConstants.getCurrentVersion().dataVersion().version();
        //?} else {
        /*return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        *///?}
    }

    private static BlockState read(CompoundTag tag) {
        //? if >=1.21.2 {
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK, tag);
        //?} else {
        /*return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
        *///?}
    }
}
