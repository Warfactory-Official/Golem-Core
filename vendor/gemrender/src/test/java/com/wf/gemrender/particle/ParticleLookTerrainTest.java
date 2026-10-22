package com.wf.gemrender.particle;

//? if <26.1 {
import static org.assertj.core.api.Assertions.assertThat;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleLookTerrainTest {
	// Bootstrap may throw past the block registry on 1.20.1; see ParticleSweepScaleTest.
	@BeforeAll
	static void bootstrapRegistries() {
		SharedConstants.tryDetectVersion();
		try {
			Bootstrap.bootStrap();
		} catch (Throwable pastWhatTheRegistriesNeeded) {
			// Checked below.
		}
		assertThat(Blocks.STONE.defaultBlockState().isAir()).isFalse();
	}

	@Test
	@DisplayName("grass block dust is untinted, as vanilla's terrain particles; other tinted blocks are not")
	void grassBlockIsUntinted() {
		assertThat(ParticleLook.untintedTerrain(Blocks.GRASS_BLOCK.defaultBlockState())).isTrue();
		assertThat(ParticleLook.untintedTerrain(Blocks.OAK_LEAVES.defaultBlockState())).isFalse();
		assertThat(ParticleLook.untintedTerrain(Blocks.STONE.defaultBlockState())).isFalse();
	}
}
//?}
