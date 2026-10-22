package com.wf.gemrender.volume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VolumeNoiseDensityTest {

	@Test
	@DisplayName("density by shape: 0 at shape 0 (noise <= 1 cannot beat the edge term), rising to the interior mean")
	void densityByShapeIsAMonotoneRamp() {
		float[] table = VolumeNoise.densityByShape();
		assertThat(table).hasSize(VolumeNoise.SHAPE_STEPS);
		assertThat(table[0]).isZero();
		for (int i = 1; i < table.length; i++) {
			assertThat(table[i]).isGreaterThanOrEqualTo(table[i - 1]);
		}
		assertThat(table[table.length - 1]).isBetween(0.2f, 1.3f);
	}
}
