package com.wf.gemrender.gltf.blend;

import static org.assertj.core.api.Assertions.assertThat;

import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeRotation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;

class NlerpVersusSlerpTest {
	private final NodeTable table = BlendFixture.table(1, 2L);
	private final BlendEvaluator.Scratch scratch = new BlendEvaluator.Scratch();

	/**
	 * Max angle (degrees) between the two-layer blend and {@code slerp} over {@code w in [0, 1]}.
	 */
	private float worstDegrees(float degrees) {
		Quaternionf a = new Quaternionf().rotateXYZ(0.3f, -0.2f, 0.1f);
		Quaternionf b = new Quaternionf(a).rotateAxis((float) Math.toRadians(degrees), 0.6f, 0.0f, 0.8f);
		GltfAnimation pa = pinned(a);
		GltfAnimation pb = pinned(b);
		float worst = 0.0f;
		float[] out = table.newScratch();
		for (int i = 0; i <= 200; i++) {
			float w = i / 200.0f;
			AnimationBlend blend = new AnimationBlend();
			blend.override(pa, 0.0f, 1.0f - w);
			blend.override(pb, 0.0f, w);
			BlendEvaluator.evaluate(table, blend, out, scratch);
			Quaternionf blended = new Quaternionf(out[3], out[4], out[5], out[6]);
			Quaternionf exact = new Quaternionf(a).slerp(b, w);
			float dot = Math.min(1.0f, Math.abs(blended.dot(exact)));
			worst = Math.max(worst, (float) Math.toDegrees(2.0 * Math.acos(dot)));
		}
		return worst;
	}

	private GltfAnimation pinned(Quaternionf q) {
		int r = NodeRotation.offsetOf(table, 0);
		return GltfAnimation.procedural("pin", new PoseDriver() {
			@Override
			public void apply(float time, float[] scratch) {
				scratch[r] = q.x;
				scratch[r + 1] = q.y;
				scratch[r + 2] = q.z;
				scratch[r + 3] = q.w;
			}

			@Override
			public float cycleSeconds() {
				return 0.0f;
			}
		});
	}

	@Test
	@DisplayName("nlerp stays within a degree of slerp for poses up to 90 degrees apart")
	void nlerpIsEnough() {
		assertThat(worstDegrees(30.0f)).isLessThan(0.1f);
		assertThat(worstDegrees(90.0f)).isLessThan(1.0f);
		assertThat(worstDegrees(90.0f)).as("not vacuous").isGreaterThan(0.5f);
		assertThat(worstDegrees(170.0f)).isLessThan(7.5f);
	}
}
