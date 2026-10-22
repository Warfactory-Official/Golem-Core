package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.blaze3d.platform.NativeImage;
import com.wf.gemrender.texture.Pixels;
import java.util.function.ToIntBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleLookTest {
	@Test
	@DisplayName("every packed look is exact in the float slot it rides in")
	void everyLookIsExactInAFloat() {
		int max = ParticleLook.of(0xFFFFFF, 15, 15);
		assertThat(max).isEqualTo(1 << 24);
		assertThat((int) (float) max).isEqualTo(max);
		assertThat((int) (float) (max - 1)).isEqualTo(max - 1);
	}

	@Test
	@DisplayName("no look is 0, and no colour and light packs to 0")
	void noneIsDistinct() {
		assertThat(ParticleLook.NONE).isZero();
		assertThat(ParticleLook.of(0x000000, 0, 0)).isEqualTo(1);
	}

	@Test
	@DisplayName("colour survives at 565 precision and light exactly")
	void roundTrips() {
		int look = ParticleLook.of(0x8A5C2E, 11, 3);
		assertThat(ParticleLook.red(look)).isBetween(0x8A - 8, 0x8A + 8);
		assertThat(ParticleLook.green(look)).isBetween(0x5C - 4, 0x5C + 4);
		assertThat(ParticleLook.blue(look)).isBetween(0x2E - 8, 0x2E + 8);
		assertThat(ParticleLook.blockLight(look)).isEqualTo(11);
		assertThat(ParticleLook.skyLight(look)).isEqualTo(3);
	}

	@Test
	@DisplayName("light out of range clamps rather than bleeding into the colour bits")
	void lightClamps() {
		int look = ParticleLook.of(0x000000, 99, -4);
		assertThat(ParticleLook.blockLight(look)).isEqualTo(15);
		assertThat(ParticleLook.skyLight(look)).isZero();
		assertThat(ParticleLook.red(look)).isZero();
		assertThat(ParticleLook.blue(look)).isZero();
	}

	@Test
	@DisplayName("sprite mean weights by alpha; a fully transparent pixel counts for nothing")
	void meanIsAlphaWeighted() {
		try (NativeImage image = new NativeImage(3, 1, false)) {
			Pixels.set(image, 0, 0, 0xFF0000FF);
			Pixels.set(image, 1, 0, 0x80FF0000);
			Pixels.set(image, 2, 0, 0x0000FF00);
			assertThat(ParticleLook.mean(image)).isEqualTo((255 * 255 / 383) << 16 | (255 * 128 / 383));
		}
		try (NativeImage clear = new NativeImage(1, 1, true)) {
			assertThat(ParticleLook.mean(clear)).isEqualTo(ParticleLook.WHITE);
		}
	}

	@Test
	@DisplayName("terrain colour is vanilla's 0.6 grey times the tint; -1 is untinted")
	void terrainShadeAndTint() {
		assertThat(ParticleLook.terrain(0xFFFFFF, -1)).isEqualTo(0x999999);
		assertThat(ParticleLook.terrain(0x8A5C2E, -1)).isEqualTo(
				Math.round(0x8A * 0.6f) << 16 | Math.round(0x5C * 0.6f) << 8 | Math.round(0x2E * 0.6f));
		assertThat(ParticleLook.terrain(0xFFFFFF, 0x7FB238)).isEqualTo(
				Math.round(0x7F * 0.6f) << 16 | Math.round(0xB2 * 0.6f) << 8 | Math.round(0x38 * 0.6f));
		assertThat(ParticleLook.terrain(0x80C040, 0x00FF80)).isEqualTo(
				0 | Math.round(0xC0 * 0.6f) << 8 | Math.round(0x40 * 0.6f * 0x80 / 255.0f));
	}

	@Test
	@DisplayName("lit reads block and sky light of the block containing the point, each into its own field")
	void litReadsTheContainingBlock() {
		BlockPos expected = new BlockPos(3, 64, -1);
		ToIntBiFunction<LightLayer, BlockPos> level = (layer, pos) -> !expected.equals(pos) ? 0
				: layer == LightLayer.BLOCK ? 11 : 4;
		int look = ParticleLook.lit(level, 3.7, 64.2, -0.5, 0x8A5C2E);
		assertThat(ParticleLook.blockLight(look)).isEqualTo(11);
		assertThat(ParticleLook.skyLight(look)).isEqualTo(4);
		assertThat(look).isEqualTo(ParticleLook.of(0x8A5C2E, 11, 4));
	}
}
