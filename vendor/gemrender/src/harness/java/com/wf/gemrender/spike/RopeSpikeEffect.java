package com.wf.gemrender.spike;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * A row of ropes at increasing slack, and a row hung vertically — the mooring-chain case, which is
 * where the frame across the rope has no obvious answer and a catenary has no answer at all.
 */
public final class RopeSpikeEffect implements Effect {
	public static final float SPAN = Float.parseFloat(System.getProperty("gemrender.ropespan", "6"));

	public static final float RADIUS = Float.parseFloat(System.getProperty("gemrender.roperadius", "0.09"));

	public static final float SWAY = Float.parseFloat(System.getProperty("gemrender.ropesway", "0.25"));

	public static final int RINGS = Integer.getInteger("gemrender.roperings", 16);

	public static final int SIDES = Integer.getInteger("gemrender.ropesides", 4);

	private final Level level;

	private final BlockPos origin;

	private final int count;

	public RopeSpikeEffect(Level level, BlockPos origin, int count) {
		this.level = level;
		this.origin = origin;
		this.count = Math.max(1, count);
	}

	public BlockPos origin() {
		return origin;
	}

	public int count() {
		return count;
	}

	@Override
	public LevelAccessor level() {
		return level;
	}

	@Override
	public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
		return new RopeSpikeVisual(ctx, this, partialTick);
	}
}
