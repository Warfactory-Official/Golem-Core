package com.wf.gemrender.spike;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeSwing;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.blend.AdditiveReference;
import com.wf.gemrender.gltf.blend.AnimationBlend;
import com.wf.gemrender.gltf.blend.BlendMask;
import com.wf.gemrender.gltf.blend.Crossfade;
import com.wf.gemrender.gltf.blend.FadeCurve;
import com.wf.gemrender.render.GemRenderInstance;
import com.wf.gemrender.render.GemRenderInstanceTypes;
import com.wf.gemrender.render.PoseCache;

import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code -PautoBlend=<asset>}: six copies facing the camera, top row {@code A | crossfade A->B | B}, bottom row
 * {@code A + additive swing | inertialized A->B | inertialized from A frozen at the switch}. B is a held frame;
 * A plays at {@code blendSpeed} (0 = held), so the last two differ only by the source velocity.
 * Timeline (seconds; {@code -Pfreeze} pins it): B starts at {@code blendAt}, fades over {@code blendFade};
 * the additive layer's weight ramps over the same window.
 */
public final class BlendVisual extends AbstractVisual implements EffectVisual<BlendEffect>, SimpleDynamicVisual {
	public static final int COPIES = 6;

	private static final float AT = Float.parseFloat(System.getProperty("gemrender.blendat", "1"));
	private static final float FADE = Float.parseFloat(System.getProperty("gemrender.blendfade", "2"));
	private static final float TIME_A = Float.parseFloat(System.getProperty("gemrender.blenda", "0"));
	private static final float TIME_B = Float.parseFloat(System.getProperty("gemrender.blendb", "-1"));
	private static final float SPEED = Float.parseFloat(System.getProperty("gemrender.blendspeed", "0"));
	private static final String NODE = System.getProperty("gemrender.blendnode", "");
	private static final float ANGLE = Float.parseFloat(System.getProperty("gemrender.blendangle", "35"));
	private static final FadeCurve CURVE =
			FadeCurve.valueOf(System.getProperty("gemrender.blendcurve", "SMOOTH").toUpperCase(Locale.ROOT));

	private static final float CELL = Float.parseFloat(System.getProperty("gemrender.blendcell", "0"));

	private static final char AXIS = System.getProperty("gemrender.blendaxis", "z").charAt(0);

	private static final float RAISE = Float.parseFloat(System.getProperty("gemrender.blendraise", "-0.85"));

	private static final float CELL_DISTANCE =
			Float.parseFloat(System.getProperty("gemrender.blenddistance", "1.6"));

	private static volatile String report = "blend=not-drawn";

	@Nullable
	private final GemRenderGltfModel gltf;
	private final GemRenderInstance[] instances = new GemRenderInstance[COPIES];
	private final AnimationBlend blend = new AnimationBlend();
	private final PoseCache.Pose[] poses = new PoseCache.Pose[COPIES];
	@Nullable
	private GltfAnimation clip;
	private float timeB;
	@Nullable
	private GltfAnimation swing;
	@Nullable
	private AdditiveReference rest;
	@Nullable
	private Crossfade fade;
	@Nullable
	private Crossfade inertial;
	@Nullable
	private Crossfade still;
	private boolean inertialStarted;
	private float startSeconds = Float.NaN;

	public BlendVisual(VisualizationContext ctx, BlendEffect effect, float partialTick) {
		super(ctx, (Level) effect.level(), partialTick);
		gltf = SpikeAssets.model(effect.asset());
		if (gltf == null) {
			return;
		}
		NodeTable table = gltf.layout()
				.nodeTable();
		clip = gltf.animationOrAny(System.getProperty("gemrender.autoanimation", ""));
		timeB = TIME_B >= 0.0f ? TIME_B : farthestFrom(table, clip, TIME_A);

		int slot = NODE.isEmpty() ? defaultNode(table) : table.slotOfName(NODE);
		if (slot < 0) {
			throw new IllegalArgumentException("blend spike: no node '" + NODE + "' in " + effect.asset());
		}
		swing = GltfAnimation.procedural("swing", NodeSwing.open(table, slot, AXIS == 'x' ? 1.0f : 0.0f,
				AXIS == 'y' ? 1.0f : 0.0f, AXIS == 'z' ? 1.0f : 0.0f, (float) Math.toRadians(ANGLE)));
		rest = AdditiveReference.rest(table);

		fade = new Crossfade(table);
		fade.play(clip, 0.0f, 0.0f, CURVE, TIME_A, SPEED, true);
		fade.play(clip, AT, FADE, CURVE, timeB, 0.0f, false);
		inertial = new Crossfade(table);
		inertial.play(clip, 0.0f, 0.0f, FadeCurve.LINEAR, TIME_A, SPEED, true);
		still = new Crossfade(table);
		still.play(clip, 0.0f, 0.0f, FadeCurve.LINEAR, clip == null ? 0.0f : clip.loop(TIME_A + SPEED * AT), 0.0f, false);

		Vector4f sphere = new Vector4f();
		gltf.bounds()
				.evaluate(gltf.restPalette(), sphere);
		float cell = CELL > 0.0f ? CELL : 1.5f;
		float distance = CELL_DISTANCE * cell;
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = mc.player.getEyePosition();
		Vec3 forward = Vec3.directionFromRotation(0.0f, mc.player.getYRot());
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3i renderOrigin = renderOrigin();
		var instancer = instancerProvider().instancer(GemRenderInstanceTypes.SKINNED, gltf.model());
		for (int i = 0; i < COPIES; i++) {
			double column = i % 3 - 1.0;
			double row = i / 3 == 0 ? 0.5 : 0.0;
			Vec3 at = eye.add(forward.scale(distance))
					.add(right.scale(column * cell))
					.add(0.0, row * cell + RAISE, 0.0);
			GemRenderInstance instance = instancer.createInstance();
			instance.pose.translation((float) (at.x - renderOrigin.getX()), (float) (at.y - renderOrigin.getY()),
					(float) (at.z - renderOrigin.getZ()))
					.rotateY((float) Math.toRadians(-mc.player.getYRot()))
					.scale(0.45f * cell / sphere.w)
					.translate(-sphere.x, -sphere.y, -sphere.z);
			instance.colorArgb(0xFFFFFFFF);
			instance.light(LightTexture.FULL_BRIGHT);
			instance.setChanged();
			instances[i] = instance;
		}
		GemRender.LOGGER.info("blend spike: clip '{}' ({}s), A={}s x{} B={}s, swing '{}' {} deg, fade {} at {} over {}s, "
						+ "rest bound {}, cell {}", clip == null ? "<none>" : clip.name(),
				clip == null ? 0 : clip.duration(), TIME_A, SPEED, timeB, table.nodeName(slot), ANGLE, CURVE, AT, FADE,
				sphere, cell);
	}

	static int defaultNode(NodeTable table) {
		int weapon = table.slotOfName("tag_weapon");
		return weapon >= 0 ? weapon : table.firstRootSlot();
	}

	/**
	 * The instant of {@code clip} whose pose is farthest (summed rotation angle) from {@code from}.
	 */
	static float farthestFrom(NodeTable table, @Nullable GltfAnimation clip, float from) {
		if (clip == null || clip.duration() <= 0.0f) {
			return 0.0f;
		}
		float[] a = table.newScratch();
		clip.apply(from, a);
		float[] b = table.newScratch();
		Quaternionf qa = new Quaternionf();
		Quaternionf qb = new Quaternionf();
		float best = 0.0f;
		float bestTime = 0.0f;
		for (int i = 0; i < 128; i++) {
			float t = clip.duration() * i / 128.0f;
			table.resetToRest(b);
			clip.apply(t, b);
			float sum = 0.0f;
			for (int n = 0; n < table.nodeCount(); n++) {
				int r = n * NodeTable.TRS_STRIDE + NodeTable.ROTATION;
				qa.set(a[r], a[r + 1], a[r + 2], a[r + 3]);
				qb.set(b[r], b[r + 1], b[r + 2], b[r + 3]);
				sum += 2.0f * (float) Math.acos(Math.min(1.0f, Math.abs(qa.dot(qb))));
			}
			if (sum > best) {
				best = sum;
				bestTime = t;
			}
		}
		return bestTime;
	}

	@Override
	public void beginFrame(Context ctx) {
		if (gltf == null) {
			return;
		}
		float seconds = SpikeClock.seconds(level, ctx.partialTick());
		if (Float.isNaN(startSeconds)) {
			startSeconds = seconds;
		}
		float now = SpikeClock.isFrozen() ? seconds : seconds - startSeconds;
		if (!inertialStarted && now >= AT) {
			inertialStarted = true;
			inertial.play(clip, AT, FADE, FadeCurve.INERTIAL, timeB, 0.0f, false);
			still.play(clip, AT, FADE, FadeCurve.INERTIAL, timeB, 0.0f, false);
		}
		float timeA = clip == null ? 0.0f : clip.loop(TIME_A + SPEED * now);
		float ramp = Math.min(1.0f, Math.max(0.0f, (now - AT) / FADE));

		PoseCache cache = PoseCache.getInstance();
		for (int i = 0; i < COPIES; i++) {
			blend.clear();
			switch (i) {
				case 0 -> blend.override(clip, timeA, 1.0f);
				case 1 -> fade.write(now, blend);
				case 2 -> blend.override(clip, timeB, 1.0f);
				case 3 -> {
					blend.override(clip, timeA, 1.0f);
					blend.additive(swing, 1.0f, ramp, BlendMask.ALL, rest);
				}
				case 4 -> inertial.write(now, blend);
				default -> still.write(now, blend);
			}
			PoseCache.Pose pose = cache.pose(gltf.layout(), gltf.bounds(), gltf.morphs(), blend, 0);
			poses[i] = pose;
			GemRenderInstance instance = instances[i];
			if (instance.boneBase != pose.boneBase() || instance.morphBase != pose.morphBase()
					|| !instance.boneSphere.equals(pose.sphere())) {
				instance.boneBase = pose.boneBase();
				instance.morphBase = pose.morphBase();
				instance.boneSphere.set(pose.sphere());
				instance.setChanged();
			}
		}

		report = String.format(Locale.ROOT,
				"blendNow=%.3f fadeVsA=%.4f fadeVsB=%.4f additiveVsA=%.4f inertialVsA=%.4f inertialVsB=%.4f "
						+ "inertialVsStill=%.4f stillVsB=%.4f blendFading=%s inertialFading=%s",
				now, difference(1, 0), difference(1, 2), difference(3, 0), difference(4, 0), difference(4, 2),
				difference(4, 5), difference(5, 2), fade.fading(now), inertial.fading(now));
	}

	/**
	 * Max matrix-element difference over node slots of two copies' palettes.
	 */
	private float difference(int a, int b) {
		NodeTable table = gltf.layout()
				.nodeTable();
		Matrix4f ma = new Matrix4f();
		Matrix4f mb = new Matrix4f();
		float worst = 0.0f;
		for (int n = 0; n < table.nodeCount(); n++) {
			poses[a].boneMatrix(n, ma);
			poses[b].boneMatrix(n, mb);
			for (int c = 0; c < 4; c++) {
				for (int r = 0; r < 4; r++) {
					worst = Math.max(worst, Math.abs(ma.get(c, r) - mb.get(c, r)));
				}
			}
		}
		return worst;
	}

	public static String verdict() {
		return report;
	}

	@Override
	protected void _delete() {
		for (GemRenderInstance instance : instances) {
			if (instance != null) {
				instance.delete();
			}
		}
	}
}
