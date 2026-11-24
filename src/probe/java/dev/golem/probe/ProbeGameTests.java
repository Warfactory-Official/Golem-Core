package dev.golem.probe;

import dev.golem.core.data.Tags;
import dev.golem.core.gametest.GameTests;
import dev.golem.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** The probe's GameTests: each one checks a Core facade against what vanilla does with its own content. */
public final class ProbeGameTests {

    private ProbeGameTests() {}

    public static final GameTests.Suite TESTS = GameTests.suite(ProbeEntry.MOD_ID);
    public static final GameTests.Function FUEL_FAST_FURNACES =
            TESTS.function("fuel_halved_in_fast_furnaces", 40, () -> ProbeGameTests::fuelHalvedInFastFurnaces);

    public static void init() {}

    /** A {@code Fuels} fuel burns half as long in a smoker or blast furnace, as vanilla's own fuels do. */
    public static void fuelHalvedInFastFurnaces(GameTestHelper helper) {
        BlockPos furnace = light(helper, new BlockPos(1, 1, 1), Blocks.FURNACE);
        BlockPos smoker = light(helper, new BlockPos(3, 1, 1), Blocks.SMOKER);
        BlockPos blast = light(helper, new BlockPos(5, 1, 1), Blocks.BLAST_FURNACE);
        helper.runAfterDelay(5, () -> {
            int full = burnLeft(helper, furnace);
            int smoking = burnLeft(helper, smoker);
            int blasting = burnLeft(helper, blast);
            int ticks = ProbeEntry.FUEL_TICKS;
            helper.assertTrue(full > ticks * 3 / 4 && full <= ticks, "furnace burn left " + full + " of " + ticks);
            helper.assertTrue(smoking > ticks / 4 && smoking <= ticks / 2, "smoker burn left " + smoking + " of " + ticks / 2);
            helper.assertTrue(blasting > ticks / 4 && blasting <= ticks / 2, "blast furnace burn left " + blasting + " of " + ticks / 2);
            helper.succeed();
        });
    }

    private static BlockPos light(GameTestHelper helper, BlockPos relative, Block block) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
        // raw iron cooks in a furnace and a blast furnace, raw porkchop in a furnace and a smoker
        furnace.setItem(0, new ItemStack(block == Blocks.SMOKER ? Items.PORKCHOP : Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.PAPER));
        return pos;
    }

    private static int burnLeft(GameTestHelper helper, BlockPos pos) {
        CompoundTag tag = BlockEntities.saveWithoutMetadata(helper.getLevel().getBlockEntity(pos),
                helper.getLevel().registryAccess());
        return Tags.contains(tag, "lit_time_remaining") ? Tags.getIntOr(tag, "lit_time_remaining", -1)
                : Tags.getIntOr(tag, "BurnTime", -1);
    }
}
