package com.wf.gemrender.spike;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.Ids;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.particle.LevelContactProbe;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleCollision;
import com.wf.gemrender.particle.ParticleEmitter;
import com.wf.gemrender.particle.ParticleLook;
import com.wf.gemrender.particle.ParticleStyle;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One column per particle kind in front of a test wall. Smoke: left column style light, right column
 * {@link ParticleLook#lit}. Emitted from the client tick: spawns read the level.
 */
public final class ParticleKindsEffect implements Effect {
	public static final String KINDS = System.getProperty("gemrender.particlekinds", "");

	public static final int COUNT = Integer.getInteger("gemrender.particlekindcount", 300);

	public static final ResourceLocation CASING = Ids.of(GemRender.MOD_ID, "models/casing/casing.gltf");

	public static final ResourceLocation HOLE = Ids.of(GemRender.MOD_ID, "textures/particle/bullet_hole.png");

	public static final int SPACING = 3;

	/** Wall face plane: blocks at {@code origin.z + WALL}, face toward the camera at that z. */
	public static final int WALL = 4;

	/** Wall block per column, cycling. */
	public static final String[] WALL_BLOCKS = {"minecraft:stone", "minecraft:oak_planks", "minecraft:grass_block",
			"minecraft:sandstone", "minecraft:red_concrete", "minecraft:iron_block"};

	private static final ModelCache.Handle<GemRenderGltfModel> CASING_HANDLE = GemRenderModels.handle(CASING);

	public enum Kind {
		DECAL(3.0f), CASING(6.0f), DUST(1.6f), FLASH(0.08f), SMOKE(3.0f), SPARK(0.9f);

		final float life;

		Kind(float life) {
			this.life = life;
		}
	}

	private static int[] styles;

	private final ClientLevel level;

	private final BlockPos origin;

	private final List<Kind> kinds;

	private final ParticleEmitter[] emitters;

	private final float[] budgets;

	private final RandomSource random = RandomSource.create(0x5EEDL);

	private long emitNanos;

	private long emitTicks;

	public ParticleKindsEffect(ClientLevel level, BlockPos origin) {
		this.level = level;
		this.origin = origin;
		this.kinds = kinds();
		this.emitters = new ParticleEmitter[kinds.size()];
		this.budgets = new float[kinds.size()];
		int[] s = styles();
		for (int i = 0; i < emitters.length; i++) {
			Kind kind = kinds.get(i);
			emitters[i] = ParticleEmitter.create(s[kind.ordinal()], capacity(kind), column(i) + 0.5,
					origin.getY(), origin.getZ() + 0.5);
		}
	}

	public static List<Kind> kinds() {
		List<Kind> out = new ArrayList<>();
		if (KINDS.isEmpty() || KINDS.equals("none")) {
			return out;
		}
		for (String name : KINDS.split(",")) {
			if (name.equals("all")) {
				return List.of(Kind.values());
			}
			out.add(Kind.valueOf(name.trim()
					.toUpperCase(Locale.ROOT)));
		}
		return out;
	}

	public static boolean wanted() {
		return !KINDS.isEmpty();
	}

	public static boolean ready() {
		return !kinds().contains(Kind.CASING) || CASING_HANDLE.get() != null;
	}

	public static GemRenderGltfModel casing() {
		return CASING_HANDLE.get();
	}

	private static int capacity(Kind kind) {
		return kind == Kind.FLASH ? Math.max(16, COUNT) : Math.max(16, Math.round(COUNT * 1.15f));
	}

	public int column(int index) {
		return origin.getX() + Math.round((index - (kinds.size() - 1) / 2.0f) * SPACING);
	}

	public BlockPos origin() {
		return origin;
	}

	public List<Kind> kindList() {
		return kinds;
	}

	public ParticleEmitter[] emitters() {
		return emitters;
	}

	public double meanEmitMicros() {
		return emitTicks == 0 ? 0.0 : emitNanos / 1000.0 / emitTicks;
	}

	public void resetRun() {
		emitNanos = 0;
		emitTicks = 0;
	}

	private static synchronized int[] styles() {
		if (styles != null) {
			return styles;
		}
		ParticleBuffer buffer = ParticleBuffer.getInstance();
		styles = new int[Kind.values().length];
		styles[Kind.DECAL.ordinal()] = buffer.registerStyle(ParticleStyle.builder()
				.size(0.14f, 0.0f)
				.alpha(1.0f, 1.0f)
				.fadeOut(0.6f)
				.build());
		styles[Kind.CASING.ordinal()] = buffer.registerStyle(ParticleStyle.builder()
				.gravity(16.0f)
				.spin(28.0f)
				.bouncesOnContact(0.3f, 0.45f)
				.fadeOut(0.85f)
				.build());
		styles[Kind.DUST.ordinal()] = buffer.registerStyle(ParticleStyle.builder()
				.drag(ParticleStyle.dragFromPerTickFactor(0.8f))
				.gravity(0.6f)
				.size(0.25f, 1.1f)
				.alpha(0.85f, 1.3f)
				.fadeIn(0.05f)
				.spin(0.8f)
				.build());
		styles[Kind.FLASH.ordinal()] = buffer.registerStyle(ParticleStyle.builder()
				.size(0.55f, 0.35f)
				.tint(0xFFD27A)
				.alpha(1.0f, 2.0f)
				.build());
		styles[Kind.SMOKE.ordinal()] = buffer.registerStyle(ParticleStyle.builder()
				.drag(ParticleStyle.dragFromPerTickFactor(0.9f))
				.gravity(-0.6f)
				.size(0.3f, 1.6f)
				.tint(0.62f, 0.62f, 0.62f)
				.alpha(0.55f, 1.0f)
				.fadeIn(0.1f)
				.spin(0.5f)
				.build());
		styles[Kind.SPARK.ordinal()] = buffer.registerStyle(ParticleStyle.builder()
				.drag(ParticleStyle.dragFromPerTickFactor(0.97f))
				.gravity(18.0f)
				.size(0.035f, 0.0f)
				.tint(0xFFB040)
				.cool(0.35f, 0.7f)
				.streak(0.035f)
				.bouncesOnContact(0.45f, 0.6f)
				.build());
		return styles;
	}

	public void emit() {
		long start = System.nanoTime();
		ParticleCollision.Probe probe = LevelContactProbe.of(level);
		for (int i = 0; i < emitters.length; i++) {
			Kind kind = kinds.get(i);
			budgets[i] += COUNT / (kind.life * 20.0f);
			while (budgets[i] >= 1.0f) {
				budgets[i] -= 1.0f;
				spawn(kind, emitters[i], i, probe);
			}
		}
		emitNanos += System.nanoTime() - start;
		emitTicks++;
	}

	private void spawn(Kind kind, ParticleEmitter emitter, int index, ParticleCollision.Probe probe) {
		double x = column(index) + 0.5;
		double y = origin.getY();
		double z = origin.getZ() + 0.5;
		double wallZ = origin.getZ() + WALL;
		switch (kind) {
			case DECAL -> {
				double hx = x + (random.nextDouble() - 0.5) * 2.4;
				double hy = y + 0.2 + random.nextDouble() * 2.6;
				emitter.spawnDecal(hx, hy, wallZ, 0.0, 0.0, -1.0, kind.life * (0.4f + random.nextFloat() * 0.6f),
						0.7f + random.nextFloat() * 0.6f, random.nextFloat() * 6.2831855f,
						ParticleLook.lit(level, hx, hy, wallZ - 0.5, ParticleLook.WHITE));
			}
			case CASING -> {
				double angle = (random.nextDouble() - 0.5) * 0.8;
				double speed = 0.8 + random.nextDouble() * 0.8;
				emitter.spawn(probe, x - 0.8, y + 1.3, z, Math.cos(angle) * speed, 2.0 + random.nextDouble() * 1.5,
						Math.sin(angle) * speed, kind.life, 1.0f, random.nextFloat() * 6.2831855f, 1.0f, 0.0048f,
						ParticleLook.lit(level, x, y + 0.5, z, ParticleLook.WHITE));
			}
			case DUST -> {
				double hx = x + (random.nextDouble() - 0.5) * 2.0;
				double hy = y + 0.5 + random.nextDouble() * 2.0;
				BlockPos hit = BlockPos.containing(hx, hy, wallZ + 0.5);
				BlockState state = level.getBlockState(hit);
				int colour = state.isAir() ? ParticleLook.WHITE : ParticleLook.blockColour(level, hit, state);
				emitter.spawn(hx + random.nextGaussian() * 0.05, hy + random.nextGaussian() * 0.05, wallZ - 0.05,
						random.nextGaussian() * 0.8, random.nextGaussian() * 0.8 + 0.4, -1.5 - random.nextDouble() * 2.0,
						kind.life * (0.6f + random.nextFloat() * 0.4f), 0.6f + random.nextFloat() * 0.6f,
						random.nextFloat() * 6.2831855f, 1.0f, ParticleLook.lit(level, hx, hy, wallZ - 0.5, colour));
			}
			case FLASH -> emitter.spawn(x, y + 1.5, z, 0.0, 0.0, 0.0, kind.life * (0.7f + random.nextFloat() * 0.6f),
					0.8f + random.nextFloat() * 0.5f, random.nextFloat() * 6.2831855f, 1.0f);
			case SMOKE -> {
				boolean lit = random.nextBoolean();
				double sx = x + (lit ? 0.9 : -0.9);
				emitter.spawn(sx + random.nextGaussian() * 0.1, y + 0.3, z + random.nextGaussian() * 0.1,
						random.nextGaussian() * 0.3, 0.8 + random.nextDouble() * 0.6, random.nextGaussian() * 0.3,
						kind.life * (0.7f + random.nextFloat() * 0.3f), 0.8f + random.nextFloat() * 0.5f,
						random.nextFloat() * 6.2831855f, 1.0f,
						lit ? ParticleLook.lit(level, sx, y + 1.5, z, ParticleLook.WHITE) : ParticleLook.NONE);
			}
			case SPARK -> {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double speed = 2.0 + random.nextDouble() * 4.0;
				emitter.spawn(probe, x, y + 1.6, z, Math.cos(angle) * speed, 1.0 + random.nextDouble() * 4.0,
						Math.sin(angle) * speed, kind.life * (0.6f + random.nextFloat() * 0.4f), 1.0f, 0.0f, 1.0f,
						0.0f);
			}
		}
	}

	@Override
	public LevelAccessor level() {
		return level;
	}

	@Override
	public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
		return new ParticleKindsVisual(ctx, this, partialTick);
	}
}
