package com.wf.gemrender.gltf.blend;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPaletteLayout;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.morph.GltfMorphLayout;

/**
 * {@code ./gradlew :1.21.1:test --tests '*BlendBench' -PblendBench=<file>}: ns per palette evaluation
 * (state + compose), every node keyframed on T/R/S. Skipped without the property.
 */
class BlendBench {
	private static final String OUT = System.getProperty("gemrender.bench.blend", "");
	private static final int MORPHS = 4;

	private interface Case {
		void run(float t);
	}

	@Test
	void bench() throws IOException {
		assumeTrue(!OUT.isEmpty(), "set -PblendBench=<file>");
		StringBuilder report = new StringBuilder("| nodes | case | ns/eval | x baseline |\n|---:|---|---:|---:|\n");
		for (int nodes : new int[] { 50, 200 }) {
			NodeTable table = BlendFixture.table(nodes, 1L);
			GltfPaletteLayout layout = GltfPaletteLayout.ofNodes(table);
			GltfAnimation a = BlendFixture.clip(table, "a", 1.0f, 8, 0.8f, 2L);
			GltfAnimation b = BlendFixture.clip(table, "b", 0.7f, 6, 0.8f, 3L);
			GltfAnimation c = BlendFixture.clip(table, "c", 1.3f, 5, 0.4f, 4L);
			BlendMask upper = BlendMask.subtree(table, "n1");
			AdditiveReference rest = AdditiveReference.rest(table);
			Matrix4f[] palette = new Matrix4f[layout.size()];
			for (int i = 0; i < palette.length; i++) {
				palette[i] = new Matrix4f();
			}
			GltfPose.Scratch scratch = new GltfPose.Scratch();
			AnimationBlend blend = new AnimationBlend();

			Case baseline = t -> GltfPose.evaluate(layout, a, t, palette, GltfMorphLayout.NONE, null, scratch);
			Case one = t -> {
				blend.clear().override(a, t, 0.6f);
				GltfPose.evaluate(layout, blend, palette, GltfMorphLayout.NONE, null, scratch);
			};
			Case two = t -> {
				blend.clear().override(a, t, 0.6f);
				blend.override(b, t, 0.4f);
				GltfPose.evaluate(layout, blend, palette, GltfMorphLayout.NONE, null, scratch);
			};
			Case four = t -> {
				blend.clear().override(a, t, 0.6f);
				blend.override(b, t, 0.4f);
				blend.override(c, t, 1.0f, upper);
				blend.additive(b, t * 0.5f, 0.5f, BlendMask.ALL, rest);
				GltfPose.evaluate(layout, blend, palette, GltfMorphLayout.NONE, null, scratch);
			};
			Case fast = t -> {
				blend.clear().override(a, t, 1.0f);
				GltfPose.evaluate(layout, blend, palette, GltfMorphLayout.NONE, null, scratch);
			};

			float[] state = table.newScratch();
			Case sampleOnly = t -> {
				table.resetToRest(state);
				a.apply(t, state);
				palette[palette.length - 1].m30(state[0]);
			};
			Case composeOnly = t -> {
				state[0] = t;
				GltfPose.evaluate(layout, state, palette, GltfMorphLayout.NONE, null, scratch);
			};

			double base = measure(baseline, palette);
			row(report, nodes, "single clip (legacy API)", base, base);
			row(report, nodes, "  of which: sample one clip", measure(sampleOnly, palette), base);
			row(report, nodes, "  of which: compose palette", measure(composeOnly, palette), base);
			row(report, nodes, "blend, 1 full-weight layer (fast path)", measure(fast, palette), base);
			row(report, nodes, "blend, 1 layer w=0.6", measure(one, palette), base);
			row(report, nodes, "blend, 2 layers", measure(two, palette), base);
			Case masked = t -> {
				blend.clear().override(a, t, 1.0f, upper.complement(table));
				blend.override(c, t, 1.0f, upper);
				GltfPose.evaluate(layout, blend, palette, GltfMorphLayout.NONE, null, scratch);
			};
			row(report, nodes, "blend, 2 layers split by a subtree mask", measure(masked, palette), base);
			row(report, nodes, "blend, 4 layers (3 override + 1 additive)", measure(four, palette), base);

			NodeTable morphTable = BlendFixture.morphTable(nodes, MORPHS, 1L);
			GltfPaletteLayout morphLayout = GltfPaletteLayout.ofNodes(morphTable);
			GltfAnimation ma = BlendFixture.morphClip(morphTable, "ma", 1.0f, 8, 2L);
			GltfAnimation mc = BlendFixture.morphClip(morphTable, "mc", 1.3f, 5, 4L);
			BlendMask morphUpper = BlendMask.subtree(morphTable, "n1");
			BlendMask morphLower = morphUpper.complement(morphTable);
			Case morphBase = t -> GltfPose.evaluate(morphLayout, ma, t, palette, GltfMorphLayout.NONE, null, scratch);
			Case morphMasked = t -> {
				blend.clear().override(ma, t, 1.0f, morphLower);
				blend.override(mc, t, 1.0f, morphUpper);
				GltfPose.evaluate(morphLayout, blend, palette, GltfMorphLayout.NONE, null, scratch);
			};
			double mb = measure(morphBase, palette);
			row(report, nodes, "morphs x" + MORPHS + ": single clip", mb, mb);
			row(report, nodes, "morphs x" + MORPHS + ": 2 layers split by a subtree mask", measure(morphMasked, palette),
					mb);
		}
		Files.writeString(Path.of(OUT), report.toString());
	}

	private static void row(StringBuilder report, int nodes, String name, double ns, double base) {
		report.append(String.format(Locale.ROOT, "| %d | %s | %.0f | %.2f |%n", nodes, name, ns, ns / base));
	}

	private static float sink;

	private static double measure(Case c, Matrix4f[] palette) {
		long iterations = 0;
		long end = System.nanoTime() + 1_500_000_000L;
		while (System.nanoTime() < end) {
			c.run((iterations++ % 997) * 0.001f);
		}
		double best = Double.MAX_VALUE;
		for (int run = 0; run < 7; run++) {
			int n = 20_000;
			long start = System.nanoTime();
			for (int i = 0; i < n; i++) {
				c.run((i % 997) * 0.001f);
			}
			best = Math.min(best, (System.nanoTime() - start) / (double) n);
			sink += palette[palette.length - 1].m30();
		}
		return best;
	}
}
