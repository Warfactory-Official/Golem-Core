package com.wf.gemrender.spike;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;

public record BlendEffect(LevelAccessor level, BlockPos origin, ResourceLocation asset) implements Effect {
	@Override
	public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
		return new BlendVisual(ctx, this, partialTick);
	}
}
