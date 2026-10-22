package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleOpticsTest {

	@Test
	@DisplayName("constant sprite: n w^2 (-ln(1 - a))")
	void constantSpriteIsClosedForm() {
		ParticleStyle style = ParticleStyle.builder()
				.size(1.5f, 0.0f)
				.alpha(0.3f, 0.0f)
				.build();
		float[] opaque = new float[16];
		Arrays.fill(opaque, 1.0f);
		float want = 4.0f * 3.0f * 3.0f * (float) -Math.log(1.0 - 0.3);
		assertThat(ParticleOptics.extinction(style, 2.0f, 4.0f, opaque)).isCloseTo(want, within(want * 1e-5f));
	}

	/** Monte Carlo: random sprites in a slab, ray down the middle; optical depth / length -> extinction. */
	@Test
	@DisplayName("matches the optical depth a ray collects through randomly placed sprites")
	void matchesRandomCloud() {
		ParticleStyle style = ParticleStyle.builder()
				.size(0.75f, 1.5f)
				.alpha(0.22f, 0.35f)
				.fadeIn(0.2f)
				.build();
		int side = 8;
		float[] texel = new float[side * side];
		for (int y = 0; y < side; y++) {
			for (int x = 0; x < side; x++) {
				double r = Math.hypot((x + 0.5) / side - 0.5, (y + 0.5) / side - 0.5) * 2.0;
				texel[y * side + x] = (float) Math.max(0.0, 1.0 - r);
			}
		}
		float density = 5.0f;
		float sizeScale = 1.5f;
		float want = ParticleOptics.extinction(style, sizeScale, density, texel);

		Random random = new Random(7);
		double half = 4.0;
		double length = 400.0;
		int rays = 64;
		double tau = 0.0;
		long sprites = Math.round(density * (2 * half) * (2 * half) * length);
		for (long i = 0; i < sprites; i++) {
			float u = random.nextFloat();
			float w = ParticleMotion.size(style, sizeScale, u);
			float a = ParticleMotion.alpha(style, u);
			double cx = (random.nextDouble() * 2 - 1) * half;
			double cy = (random.nextDouble() * 2 - 1) * half;
			for (int ray = 0; ray < rays; ray++) {
				double rx = ((ray % 8) + 0.5) / 8 - 0.5;
				double ry = ((ray / 8) + 0.5) / 8 - 0.5;
				double sx = (rx - cx) / w + 0.5;
				double sy = (ry - cy) / w + 0.5;
				if (sx < 0 || sx >= 1 || sy < 0 || sy >= 1) {
					continue;
				}
				float at = a * texel[(int) (sy * side) * side + (int) (sx * side)];
				if (at >= 1e-3f) {
					tau -= Math.log(Math.max(1.0f - at, 1e-4f));
				}
			}
		}
		double got = tau / rays / length;
		assertThat(got).isCloseTo(want, within(want * 0.05));
	}
}
