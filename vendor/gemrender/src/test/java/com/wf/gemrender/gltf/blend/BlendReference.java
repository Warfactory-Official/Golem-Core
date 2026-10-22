package com.wf.gemrender.gltf.blend;

import java.util.List;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;

/**
 * Golden model of {@link BlendEvaluator}: double precision, JOML objects, one node at a time, no scratch
 * reuse, no fast path. Same semantics, independent arithmetic.
 */
final class BlendReference {
	record Layer(GltfAnimation clip, float time, float weight, BlendMask mask, BlendMode mode,
			AdditiveReference reference) {
	}

	private BlendReference() {
	}

	static float[] evaluate(NodeTable table, List<Layer> layers) {
		float[] rest = table.newScratch();
		float[][] samples = new float[layers.size()][];
		for (int i = 0; i < layers.size(); i++) {
			samples[i] = table.newScratch();
			if (layers.get(i).clip() != null) {
				layers.get(i).clip().apply(layers.get(i).time(), samples[i]);
			}
		}
		float[] out = rest.clone();

		for (int n = 0; n < table.nodeCount(); n++) {
			int b = n * NodeTable.TRS_STRIDE;
			double total = 0.0;
			Vector3d t = new Vector3d();
			Vector3d s = new Vector3d();
			Quaterniond q = new Quaterniond(0, 0, 0, 0);
			int wb = table.weightBase(n);
			int wc = table.weightCount(n);
			double[] w = new double[wc];
			for (int i = 0; i < layers.size(); i++) {
				Layer layer = layers.get(i);
				if (layer.mode() != BlendMode.OVERRIDE) {
					continue;
				}
				double e = (double) layer.weight() * layer.mask().weight(n);
				if (e <= 0.0) {
					continue;
				}
				total += e;
				accumulate(samples[i], b, wb, wc, e, t, q, s, w);
			}
			if (total > 0.0) {
				if (total < 1.0) {
					accumulate(rest, b, wb, wc, 1.0 - total, t, q, s, w);
					total = 1.0;
				}
				t.div(total);
				s.div(total);
				q.normalize();
				write(out, b, t, q, s);
				for (int k = 0; k < wc; k++) {
					out[wb + k] = (float) (w[k] / total);
				}
			}

			for (int i = 0; i < layers.size(); i++) {
				Layer layer = layers.get(i);
				if (layer.mode() != BlendMode.ADDITIVE) {
					continue;
				}
				double e = (double) layer.weight() * layer.mask().weight(n);
				if (e <= 0.0) {
					continue;
				}
				float[] ref = layer.reference().state();
				float[] smp = samples[i];
				Vector3d base = new Vector3d(out[b], out[b + 1], out[b + 2]);
				base.add(new Vector3d(smp[b] - ref[b], smp[b + 1] - ref[b + 1], smp[b + 2] - ref[b + 2]).mul(e));
				Vector3d scale = new Vector3d(out[b + 7], out[b + 8], out[b + 9]);
				for (int k = 0; k < 3; k++) {
					double r = ref[b + 7 + k];
					if (Math.abs(r) > 1.0e-6) {
						scale.setComponent(k, scale.get(k) * (1.0 + e * (smp[b + 7 + k] / r - 1.0)));
					}
				}
				Quaterniond d = new Quaterniond(ref[b + 3], ref[b + 4], ref[b + 5], ref[b + 6]).conjugate()
						.mul(new Quaterniond(smp[b + 3], smp[b + 4], smp[b + 5], smp[b + 6]));
				if (d.w < 0.0) {
					d.set(-d.x, -d.y, -d.z, -d.w);
				}
				Quaterniond de = new Quaterniond(e * d.x, e * d.y, e * d.z, 1.0 - e + e * d.w).normalize();
				Quaterniond rotated = new Quaterniond(out[b + 3], out[b + 4], out[b + 5], out[b + 6]).mul(de);
				write(out, b, base, rotated, scale);
				for (int k = 0; k < wc; k++) {
					out[wb + k] += (float) (e * (smp[wb + k] - ref[wb + k]));
				}
			}
		}
		return out;
	}

	private static void accumulate(float[] src, int b, int wb, int wc, double e, Vector3d t, Quaterniond q,
			Vector3d s, double[] w) {
		t.add(e * src[b], e * src[b + 1], e * src[b + 2]);
		s.add(e * src[b + 7], e * src[b + 8], e * src[b + 9]);
		Quaterniond x = new Quaterniond(src[b + 3], src[b + 4], src[b + 5], src[b + 6]);
		double sign = q.dot(x) < 0.0 ? -e : e;
		q.set(q.x + sign * x.x, q.y + sign * x.y, q.z + sign * x.z, q.w + sign * x.w);
		for (int k = 0; k < wc; k++) {
			w[k] += e * src[wb + k];
		}
	}

	private static void write(float[] out, int b, Vector3d t, Quaterniond q, Vector3d s) {
		out[b] = (float) t.x;
		out[b + 1] = (float) t.y;
		out[b + 2] = (float) t.z;
		out[b + 3] = (float) q.x;
		out[b + 4] = (float) q.y;
		out[b + 5] = (float) q.z;
		out[b + 6] = (float) q.w;
		out[b + 7] = (float) s.x;
		out[b + 8] = (float) s.y;
		out[b + 9] = (float) s.z;
	}
}
