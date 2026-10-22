package com.wf.gemrender.gltf.blend;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Quaternionf;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseChannel;
import com.wf.gemrender.gltf.PoseDriver;
import com.wf.gemrender.vendor.jgltf.model.impl.DefaultNodeModel;
import com.wf.gemrender.vendor.mcgltf.animation.LinearInterpolatedChannel;
import com.wf.gemrender.vendor.mcgltf.animation.SphericalLinearInterpolatedChannel;

/**
 * Synthetic tree: node {@code i}'s parent is {@code (i - 1) / 2}. Clips are keyframed glTF channels
 * (linear T/S, slerp R) on every node, seeded, so the benchmark samples what an import produces.
 */
public final class BlendFixture {
	private BlendFixture() {
	}

	public static NodeTable table(int nodes, long seed) {
		Random random = new Random(seed);
		String[] names = new String[nodes];
		int[] parents = new int[nodes];
		float[] trs = new float[nodes * NodeTable.TRS_STRIDE];
		for (int i = 0; i < nodes; i++) {
			names[i] = "n" + i;
			parents[i] = i == 0 ? -1 : (i - 1) / 2;
			int b = i * NodeTable.TRS_STRIDE;
			trs[b] = random.nextFloat() - 0.5f;
			trs[b + 1] = random.nextFloat();
			trs[b + 2] = random.nextFloat() - 0.5f;
			Quaternionf q = randomRotation(random, 1.0f);
			trs[b + 3] = q.x;
			trs[b + 4] = q.y;
			trs[b + 5] = q.z;
			trs[b + 6] = q.w;
			trs[b + 7] = 1.0f;
			trs[b + 8] = 1.0f;
			trs[b + 9] = 1.0f;
		}
		return NodeTable.ofNodes(names, parents, trs);
	}

	public static GltfAnimation clip(NodeTable table, String name, float duration, int keys, float angle,
			long seed) {
		Random random = new Random(seed);
		List<PoseDriver> drivers = new ArrayList<>();
		for (int n = 0; n < table.nodeCount(); n++) {
			float[] times = new float[keys];
			float[][] t = new float[keys][];
			float[][] r = new float[keys][];
			float[][] s = new float[keys][];
			Quaternionf rest = new Quaternionf();
			table.restRotation(n, rest);
			for (int k = 0; k < keys; k++) {
				times[k] = duration * k / (keys - 1);
				t[k] = new float[] { table.restTranslation(n, 0) + 0.3f * (random.nextFloat() - 0.5f),
						table.restTranslation(n, 1) + 0.3f * (random.nextFloat() - 0.5f),
						table.restTranslation(n, 2) + 0.3f * (random.nextFloat() - 0.5f) };
				Quaternionf q = new Quaternionf(rest).mul(randomRotation(random, angle));
				r[k] = new float[] { q.x, q.y, q.z, q.w };
				float sc = 0.8f + 0.4f * random.nextFloat();
				s[k] = new float[] { sc, sc, sc };
			}
			drivers.add(new PoseChannel(new LinearInterpolatedChannel(times, t), n * NodeTable.TRS_STRIDE, 3));
			drivers.add(new PoseChannel(new SphericalLinearInterpolatedChannel(times, r),
					n * NodeTable.TRS_STRIDE + NodeTable.ROTATION, 4));
			drivers.add(new PoseChannel(new LinearInterpolatedChannel(times, s),
					n * NodeTable.TRS_STRIDE + NodeTable.SCALE, 3));
		}
		return new GltfAnimation(name, drivers, duration);
	}

	/**
	 * As {@link #table}, every node carrying {@code morphs} morph weights.
	 */
	public static NodeTable morphTable(int nodes, int morphs, long seed) {
		NodeTable shape = table(nodes, seed);
		DefaultNodeModel[] models = new DefaultNodeModel[nodes];
		for (int i = 0; i < nodes; i++) {
			models[i] = new DefaultNodeModel();
			models[i].setName("n" + i);
			models[i].setTranslation(new float[] { shape.restTranslation(i, 0), shape.restTranslation(i, 1),
					shape.restTranslation(i, 2) });
			models[i].setWeights(new float[morphs]);
			if (i > 0) {
				models[(i - 1) / 2].addChild(models[i]);
			}
		}
		return NodeTable.of(List.of(models));
	}

	/**
	 * {@link #clip} plus a keyframed weights channel on every node.
	 */
	public static GltfAnimation morphClip(NodeTable table, String name, float duration, int keys, long seed) {
		Random random = new Random(seed);
		List<PoseDriver> drivers = new ArrayList<>(clip(table, name, duration, keys, 0.8f, seed).drivers());
		for (int n = 0; n < table.nodeCount(); n++) {
			int count = table.weightCount(n);
			float[] times = new float[keys];
			float[][] w = new float[keys][count];
			for (int k = 0; k < keys; k++) {
				times[k] = duration * k / (keys - 1);
				for (int m = 0; m < count; m++) {
					w[k][m] = random.nextFloat();
				}
			}
			drivers.add(new PoseChannel(new LinearInterpolatedChannel(times, w), table.weightBase(n), count));
		}
		return new GltfAnimation(name, drivers, duration);
	}

	public static Quaternionf randomRotation(Random random, float maxAngle) {
		float x = random.nextFloat() - 0.5f, y = random.nextFloat() - 0.5f, z = random.nextFloat() - 0.5f;
		float length = (float) Math.sqrt(x * x + y * y + z * z);
		return new Quaternionf().fromAxisAngleRad(x / length, y / length, z / length,
				(random.nextFloat() * 2.0f - 1.0f) * maxAngle);
	}
}
