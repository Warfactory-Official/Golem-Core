package com.wf.gemrender.spike;

import com.wf.gemrender.Ids;
import com.wf.gemrender.particle.GemRenderParticleTypes;
import com.wf.gemrender.particle.ParticleEmitter;
import com.wf.gemrender.particle.ParticleInstance;
import com.wf.gemrender.particle.ParticleModels;
import com.wf.gemrender.particle.ParticlePool;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import net.minecraft.world.level.Level;

public final class ParticleKindsVisual extends AbstractVisual implements EffectVisual<ParticleKindsEffect> {
	private final ParticlePool[] pools;

	public ParticleKindsVisual(VisualizationContext ctx, ParticleKindsEffect effect, float partialTick) {
		super(ctx, (Level) effect.level(), partialTick);
		ParticleEmitter[] emitters = effect.emitters();
		pools = new ParticlePool[emitters.length];
		for (int i = 0; i < emitters.length; i++) {
			ParticleKindsEffect.Kind kind = effect.kindList()
					.get(i);
			InstanceType<ParticleInstance> type = switch (kind) {
				case DECAL -> GemRenderParticleTypes.DECAL;
				case CASING -> GemRenderParticleTypes.BODY;
				case SPARK -> GemRenderParticleTypes.STREAK;
				default -> GemRenderParticleTypes.BILLBOARD;
			};
			Model model = switch (kind) {
				case DECAL -> ParticleModels.decal(ParticleKindsEffect.HOLE);
				case CASING -> ParticleModels.rigid(ParticleKindsEffect.casing());
				case DUST -> ParticleModels.translucent(Ids.of("textures/particle/dust_puff.png"));
				case FLASH -> ParticleModels.additive(Ids.of("textures/particle/muzzle_flash.png"));
				case SMOKE -> ParticleModels.translucent(Ids.vanilla("textures/particle/big_smoke_4.png"));
				case SPARK -> ParticleModels.additive(Ids.of("textures/particle/spark_streak.png"));
			};
			pools[i] = new ParticlePool(ctx, emitters[i], type, model);
		}
	}

	@Override
	protected void _delete() {
		for (ParticlePool pool : pools) {
			pool.delete();
		}
	}
}
