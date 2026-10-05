package dev.golem.core.fluid;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A registered {@link Fluid} that exists only to be named: it has no block, no bucket and no flow, so it can
 * never be placed in the world. What it is for is identity: other mods' tanks, pipes and recipe viewers speak
 * of fluids as registry entries, and a mod whose fluids live in its own data model registers one of these per
 * fluid so they have something to speak of. Registered through {@link TokenFluids}.
 *
 * <p>On NeoForge and Forge a fluid also needs its loader {@code FluidType} (name, temperature, density,
 * rendering), which {@link TokenFluids} registers beside it.
 */
public class TokenFluid extends Fluid {

    //? if neoforge {
    private final Supplier<? extends net.neoforged.neoforge.fluids.FluidType> type;

    TokenFluid(Supplier<? extends net.neoforged.neoforge.fluids.FluidType> type) {
        this.type = type;
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidType getFluidType() {
        return type.get();
    }
    //?} elif forge {
    /*private final Supplier<? extends net.minecraftforge.fluids.FluidType> type;

    TokenFluid(Supplier<? extends net.minecraftforge.fluids.FluidType> type) {
        this.type = type;
    }

    @Override
    public net.minecraftforge.fluids.FluidType getFluidType() {
        return type.get();
    }
    *///?} else {
    /*TokenFluid() {}
    *///?}

    @Override
    public Item getBucket() {
        return Items.AIR;
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
        return true;
    }

    @Override
    protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
        return Vec3.ZERO;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return 0;
    }

    @Override
    protected float getExplosionResistance() {
        return 0.0F;
    }

    @Override
    public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public float getOwnHeight(FluidState state) {
        return 0.0F;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return Blocks.AIR.defaultBlockState();
    }

    /** A source, so recipe viewers that list only source fluids list it. */
    @Override
    public boolean isSource(FluidState state) {
        return true;
    }

    @Override
    public int getAmount(FluidState state) {
        return 8;
    }

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
