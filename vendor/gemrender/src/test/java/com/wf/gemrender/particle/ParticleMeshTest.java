package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.texture.SpriteUv;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleMeshTest {
	private static MeshGeometry triangle(int slot) {
		return MeshGeometry.of(new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}, new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1},
				new float[]{0, 0, 1, 0, 0, 1}, new int[]{0, 1, 2}, VertexSkinning.rigid(3, slot), SpriteUv.IDENTITY, 0);
	}

	@Test
	@DisplayName("a rigid bake applies the vertex's own rest bone, exactly, and then the bake transform")
	void bakesTheRestBoneThenTheBake() {
		Matrix4f[] palette = {new Matrix4f().translation(5, 0, 0), new Matrix4f().translation(0, 2, 0).scale(2)};
		ParticleMesh mesh = ParticleMesh.bake(triangle(1), palette, new Matrix4f().translation(0, 0, -1));

		assertThat(mesh.position(1, 0)).isCloseTo(2.0f, within(1e-6f));
		assertThat(mesh.position(1, 1)).isCloseTo(2.0f, within(1e-6f));
		assertThat(mesh.position(1, 2)).isCloseTo(-1.0f, within(1e-6f));
		assertThat(mesh.position(2, 1)).isCloseTo(4.0f, within(1e-6f));
	}
}
