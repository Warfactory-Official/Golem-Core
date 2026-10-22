package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleEmitterDecalReachTest {
	@Test
	@DisplayName("a decal inside DECAL_REACH of the origin keeps its lift to 1/16; one beyond is refused")
	void decalReach() {
		Vec3i origin = new Vec3i(100_000, 64, -30_000);
		double edge = ParticleEmitter.DECAL_REACH - 0.3;
		double[] near = {origin.getX() + edge, origin.getY() - edge, origin.getZ() + edge};
		assertThatCode(() -> ParticleEmitter.requireDecalReach(origin, near[0], near[1], near[2]))
				.doesNotThrowAnyException();
		for (int axis = 0; axis < 3; axis++) {
			double offset = near[axis] - (axis == 0 ? origin.getX() : axis == 1 ? origin.getY() : origin.getZ());
			assertThat(Math.abs((float) offset - offset)).as("axis %d rounding", axis)
					.isLessThanOrEqualTo(ParticleShapes.DECAL_LIFT / 16.0);
		}

		double beyond = ParticleEmitter.DECAL_REACH + 0.5;
		assertThatThrownBy(() -> ParticleEmitter.requireDecalReach(origin, origin.getX() + beyond, 64, -30_000))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ParticleEmitter.requireDecalReach(origin, 100_000, 64 - beyond, -30_000))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ParticleEmitter.requireDecalReach(origin, 100_000, 64, -30_000 + beyond))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
