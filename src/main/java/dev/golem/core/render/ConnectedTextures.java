package dev.golem.core.render;

import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connected textures in the manner of HBM 1.7's {@code RenderBlocksCT}, for full-cube blocks, on every node and under
 * Sodium/Embeddium.
 *
 * <p>Each face is cut into four quarters. A quarter's picture is chosen from its two edge neighbours and the corner
 * neighbour between them, all in the face's own plane: unconnected ({@code f}, a quarter of the block's base sprite),
 * or from the {@code _ct} sheet - a 4x4 grid of the same sprite size - connected on both edges and the corner
 * ({@code c}), on both edges but not the corner ({@code j}, an inner corner), or on the horizontal ({@code h}) or
 * vertical ({@code v}) edge only. The neighbour table, the quarter types and the sheet layout are 1.7's
 * {@code CTContext}, {@code CT} and {@code IconCT}, the corner winding its {@code drawFace}.
 *
 * <p>Per loader, each registered block's baked model is wrapped so it can see the world: NeoForge 26.1
 * {@code DynamicBlockStateModel} ({@code ModelEvent.ModifyBakingResult}), Fabric 26.1
 * {@code FabricBlockStateModel.emitQuads}, NeoForge 1.21.1 / Forge 1.20.1 {@code ModelData} on a
 * {@code BakedModelWrapper}, Fabric 1.20.1/1.21.1 {@code FabricBakedModel.emitBlockQuads}. Sodium and Embeddium
 * honour all of them, except Sodium 0.5 on 1.20.1 Fabric without Indium, which has no Fabric renderer and draws the
 * plain base model.
 *
 * <p>Items are untouched: 1.7 drew a CT block's item with the plain base icons too.
 *
 * <p>Coexists with connected-texture mods (Chisel CTM, CTM Lib, Continuity, Fusion): blocks are registered in code and
 * none of their data is read ({@code "ctm"} or {@code "fusion"} texture metadata, a {@code ctm:} model loader, OptiFine
 * {@code .properties}). A block registered here must not carry such data, or Chisel CTM wraps its models a second time.
 *
 * <p>Client-only.
 */
public final class ConnectedTextures {

    private ConnectedTextures() {}

    /** 1.7 {@code IBlockCT.canConnect}: does the block at {@code pos} (a neighbour) join {@code self}? */
    @FunctionalInterface
    public interface Connector {
        boolean connects(BlockGetter level, BlockPos pos, BlockState self, BlockState neighbour);
    }

    /** 1.7 {@code getFragments}: the base sprite and its {@code _ct} sheet for a state, as block-atlas sprite ids. */
    @FunctionalInterface
    public interface Sheets {
        String[] sheets(BlockState state);
    }

    /**
     * 1.7 {@code getFragments(world, x, y, z, side)} in full: the pair may differ per face (the graphite pile draws
     * its top sheet on DOWN/UP and its channel sheets on the sides).
     */
    @FunctionalInterface
    public interface FaceSheets {
        String[] sheets(BlockState state, Direction face);
    }

    private record Spec(FaceSheets sheets, Connector connector) {}

    private record Pending(Supplier<? extends Block> block, Spec spec) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<Block, Spec> SPECS = new ConcurrentHashMap<>();
    private static boolean installed;

    /**
     * Gives the block connected textures. Call from client initialisation, before the first resource load; the block
     * is only resolved at the first model bake, so a registry supplier may be passed before registration.
     */
    public static void register(Supplier<? extends Block> block, Sheets sheets,
                                Connector connector) {
        register(block, (FaceSheets) (state, face) -> sheets.sheets(state), connector);
    }

    /** As {@link #register(java.util.function.Supplier, Sheets, Connector)}, with a sheet pair per face. */
    public static synchronized void register(Supplier<? extends Block> block, FaceSheets sheets,
                                             Connector connector) {
        PENDING.add(new Pending(block, new Spec(sheets, connector)));
        install();
    }

    /**
     * Resolves the registered suppliers (idempotent) and drops the built quarters: a bake follows a re-stitch, which
     * makes new sprites with new atlas UVs. Called at the start of every bake.
     */
    private static synchronized void resolve() {
        for (Pending pending : PENDING) SPECS.put(pending.block().get(), pending.spec());
        PENDING.clear();
        //? if >=26.1 {
        QUADS26.clear();
        //?} else {
        /*LEGACY_QUADS.clear();
        *///?}
    }

    /** How many blocks are registered, and how many block-state models were wrapped at the last bake. */
    public static int registered() {
        return SPECS.size() + PENDING.size();
    }

    public static int wrapped() {
        return wrapped;
    }

    /**
     * For tests: the 24 quarter types the model draws at {@code pos} (face-major in {@link Direction} order, quarters
     * TL, TR, BL, BR; the type is 1.7's {@code CT} constant - 0..3 unconnected, 4.. connected, 8.. inner corner,
     * 12.. horizontal, 16.. vertical, plus 1 right and 2 bottom), or null if the block there is not registered.
     */
    public static int[] fragmentsAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Spec spec = SPECS.get(state.getBlock());
        return spec == null ? null : fragments(level, pos, state, spec);
    }

    /** For tests: the base sprite and sheet ids a registered state draws on {@code face}, or null. */
    public static String[] sheetsOf(BlockState state, Direction face) {
        Spec spec = SPECS.get(state.getBlock());
        return spec == null ? null : spec.sheets.sheets(state, face);
    }

    private static volatile int wrapped;

    // 1.7 CTContext / CT / IconCT

    private static final int T = 0, L = 0, R = 1, B = 2;
    private static final int F = 0, C = 4, J = 8, H = 12, V = 16;

    /** [face][8 neighbours: TL, TC, TR, CL, CR, BL, BC, BR][xyz], 1.7 {@code CTContext.access}. */
    private static final int[][][] ACCESS = {
            lcfs(Direction.SOUTH, Direction.WEST), // DOWN
            lcfs(Direction.NORTH, Direction.WEST), // UP
            lcfs(Direction.UP, Direction.EAST),    // NORTH
            lcfs(Direction.UP, Direction.WEST),    // SOUTH
            lcfs(Direction.UP, Direction.NORTH),   // WEST
            lcfs(Direction.UP, Direction.SOUTH)    // EAST
    };

    private static int[][] lcfs(Direction up, Direction left) {
        Direction down = up.getOpposite();
        Direction right = left.getOpposite();
        return new int[][]{cfs(up, left), cfs(up), cfs(up, right), cfs(left), cfs(right), cfs(down, left), cfs(down),
                cfs(down, right)};
    }

    private static int[] cfs(Direction... dirs) {
        int x = 0, y = 0, z = 0;
        for (Direction dir : dirs) {
            x += dir.getStepX();
            y += dir.getStepY();
            z += dir.getStepZ();
        }
        return new int[]{x, y, z};
    }

    /** 1.7 {@code cornerType}. */
    private static int cornerType(boolean hor, boolean corner, boolean vert) {
        if (vert && hor && corner) return C;
        if (vert && hor) return J;
        if (vert) return V;
        if (hor) return H;
        return F;
    }

    /** The 24 fragment types (face-major, quarters TL, TR, BL, BR), 1.7 {@code loadContext}. */
    static int[] fragments(BlockGetter level, BlockPos pos, BlockState state, Spec spec) {
        int[] out = new int[24];
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        boolean[] cons = new boolean[8];
        for (int face = 0; face < 6; face++) {
            for (int j = 0; j < 8; j++) {
                int[] d = ACCESS[face][j];
                probe.set(pos.getX() + d[0], pos.getY() + d[1], pos.getZ() + d[2]);
                cons[j] = spec.connector.connects(level, probe, state, level.getBlockState(probe));
            }
            out[face * 4] = T | L | cornerType(cons[3], cons[0], cons[1]);
            out[face * 4 + 1] = T | R | cornerType(cons[4], cons[2], cons[1]);
            out[face * 4 + 2] = B | L | cornerType(cons[3], cons[5], cons[6]);
            out[face * 4 + 3] = B | R | cornerType(cons[4], cons[7], cons[6]);
        }
        return out;
    }

    /** 1.7 {@code RenderBlocksCT.renderFace*}: each face's TL, TR, BL, BR corners in block space. */
    private static final float[][][] CORNERS = {
            {{0, 0, 1}, {1, 0, 1}, {0, 0, 0}, {1, 0, 0}}, // DOWN
            {{0, 1, 0}, {1, 1, 0}, {0, 1, 1}, {1, 1, 1}}, // UP
            {{1, 1, 0}, {0, 1, 0}, {1, 0, 0}, {0, 0, 0}}, // NORTH
            {{0, 1, 1}, {1, 1, 1}, {0, 0, 1}, {1, 0, 1}}, // SOUTH
            {{0, 1, 0}, {0, 1, 1}, {0, 0, 0}, {0, 0, 1}}, // WEST
            {{1, 1, 1}, {1, 1, 0}, {1, 0, 1}, {1, 0, 0}}  // EAST
    };

    /**
     * Vanilla's vertex order per face ({@code FaceInfo}): which corner of the quad's box each vertex sits at, as
     * {min?} per axis (true = min). Vanilla's smooth lighting reads vertex i as that corner, so the quarters keep it.
     */
    private static final boolean[][][] VANILLA_ORDER = {
            {{true, true, false}, {true, true, true}, {false, true, true}, {false, true, false}},      // DOWN
            {{true, false, true}, {true, false, false}, {false, false, false}, {false, false, true}},  // UP
            {{false, false, true}, {false, true, true}, {true, true, true}, {true, false, true}},      // NORTH
            {{true, false, false}, {true, true, false}, {false, true, false}, {false, false, false}}, // SOUTH
            {{true, false, true}, {true, true, true}, {true, true, false}, {true, false, false}},      // WEST
            {{false, false, false}, {false, true, false}, {false, true, true}, {false, false, true}}   // EAST
    };

    /** One built quarter: its four vertices in vanilla order, positions and UVs. */
    record Quarter(Direction face, float[][] positions, float[][] uvs) {}

    /** Builds quarter {@code q} (0 TL, 1 TR, 2 BL, 3 BR) of {@code face} showing fragment {@code type}. */
    static Quarter quarter(int face, int q, int type, TextureAtlasSprite base, TextureAtlasSprite ct) {
        float[][] c = CORNERS[face];
        float[] tl = c[0], tr = c[1], bl = c[2], br = c[3];
        float[] tc = avg(tl, tr), bc = avg(bl, br), cl = avg(tl, bl), cr = avg(tr, br), cc = avg(tc, bc);
        // the quarter's own TL, TR, BL, BR (1.7 drawFace)
        float[][] sub = switch (q) {
            case 0 -> new float[][]{tl, tc, cl, cc};
            case 1 -> new float[][]{tc, tr, cc, cr};
            case 2 -> new float[][]{cl, cc, bl, bc};
            default -> new float[][]{cc, cr, bc, br};
        };
        float[] uv = fragmentUv(type, base, ct);
        // 1.7 drawSubFace: TL (minU, minV), TR (maxU, minV), BL (minU, maxV), BR (maxU, maxV)
        float[][] subUv = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[0], uv[3]}, {uv[2], uv[3]}};

        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] p : sub) {
            for (int a = 0; a < 3; a++) {
                min[a] = Math.min(min[a], p[a]);
                max[a] = Math.max(max[a], p[a]);
            }
        }
        float[][] positions = new float[4][];
        float[][] uvs = new float[4][];
        for (int i = 0; i < 4; i++) {
            boolean[] pick = VANILLA_ORDER[face][i];
            float[] p = {pick[0] ? min[0] : max[0], pick[1] ? min[1] : max[1], pick[2] ? min[2] : max[2]};
            positions[i] = p;
            for (int k = 0; k < 4; k++) {
                if (same(sub[k], p)) {
                    uvs[i] = subUv[k];
                    break;
                }
            }
        }
        return new Quarter(Direction.from3DDataValue(face), positions, uvs);
    }

    private static boolean same(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) < 1.0E-4F && Math.abs(a[1] - b[1]) < 1.0E-4F && Math.abs(a[2] - b[2]) < 1.0E-4F;
    }

    private static float[] avg(float[] a, float[] b) {
        return new float[]{(a[0] + b[0]) / 2F, (a[1] + b[1]) / 2F, (a[2] + b[2]) / 2F};
    }

    /** 1.7 {@code IconCT}: {minU, minV, maxU, maxV} of a fragment, in atlas UVs. */
    static float[] fragmentUv(int type, TextureAtlasSprite base, TextureAtlasSprite ct) {
        TextureAtlasSprite parent = type < 4 ? base : ct;
        int sub = type < 4 ? 2 : 4;
        float lenU = (parent.getU1() - parent.getU0()) / sub;
        float lenV = (parent.getV1() - parent.getV0()) / sub;
        float du = parent.getU0();
        float dv = parent.getV0();
        boolean isJ = type >= J && type < J + 4;
        boolean isH = type >= H && type < H + 4;
        boolean isV = type >= V && type < V + 4;
        if (isV || isJ) du += lenU * 2;
        if (isH || isJ) dv += lenV * 2;
        if ((type & R) != 0) du += lenU;
        if ((type & B) != 0) dv += lenV;
        return new float[]{du, dv, du + lenU, dv + lenV};
    }

    /** A face's base sprite and {@code _ct} sheet; sprites are compared by identity (a re-stitch makes new ones). */
    private record Pair(TextureAtlasSprite base, TextureAtlasSprite ct) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Pair p && p.base == base && p.ct == ct;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(base) * 31 + System.identityHashCode(ct);
        }
    }

    /** The sprite pair of one face of a state, resolved from the block atlas. */
    private static Pair sprites(Spec spec, BlockState state, Direction face) {
        String[] ids = spec.sheets.sheets(state, face);
        return new Pair(Sprites.sprite(Sprites.Atlas.BLOCKS, dev.golem.core.Ids.parse(ids[0])),
                Sprites.sprite(Sprites.Atlas.BLOCKS, dev.golem.core.Ids.parse(ids[1])));
    }

    /**
     * Built quarter quads per sprite pair: [face][quarter][fragment type 0..19], built on first use. Cleared at every
     * bake (see {@link #resolve()}). {@code Q} is the version's quad type.
     */
    static final class QuadCache<Q> {
        private final Map<Pair, Object[][][]> byPair = new ConcurrentHashMap<>();
        private final QuadFactory<Q> factory;

        QuadCache(QuadFactory<Q> factory) {
            this.factory = factory;
        }

        void clear() {
            byPair.clear();
        }

        @SuppressWarnings("unchecked")
        List<Q>[] quads(Spec spec, BlockState state, int[] types) {
            List<Q>[] byFace = new List[6];
            for (int face = 0; face < 6; face++) byFace[face] = quads(spec, state, types, face);
            return byFace;
        }

        /** The four quarters of one face. */
        @SuppressWarnings("unchecked")
        List<Q> quads(Spec spec, BlockState state, int[] types, int face) {
            Pair s = sprites(spec, state, Direction.from3DDataValue(face));
            Object[][] table = byPair.computeIfAbsent(s, key -> new Object[6][4][20])[face];
            List<Q> list = new ArrayList<>(4);
            for (int q = 0; q < 4; q++) {
                int type = types[face * 4 + q];
                Object built = table[q][type];
                if (built == null) {
                    built = factory.build(quarter(face, q, type, s.base(), s.ct()), type < 4 ? s.base() : s.ct(), face);
                    table[q][type] = built;
                }
                list.add((Q) built);
            }
            return list;
        }
    }

    @FunctionalInterface
    interface QuadFactory<Q> {
        Q build(Quarter quarter, TextureAtlasSprite sprite, int face);
    }

    /** Legacy {@code DefaultVertexFormat.BLOCK} vertex data: position, colour, uv, light, normal - 8 ints a vertex. */
    static int[] legacyVertices(Quarter quarter) {
        Direction face = quarter.face();
        int normal = (face.getStepX() * 127 & 0xFF) | ((face.getStepY() * 127 & 0xFF) << 8) | ((face.getStepZ() * 127 & 0xFF) << 16);
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            data[o] = Float.floatToRawIntBits(quarter.positions()[i][0]);
            data[o + 1] = Float.floatToRawIntBits(quarter.positions()[i][1]);
            data[o + 2] = Float.floatToRawIntBits(quarter.positions()[i][2]);
            data[o + 3] = -1;
            data[o + 4] = Float.floatToRawIntBits(quarter.uvs()[i][0]);
            data[o + 5] = Float.floatToRawIntBits(quarter.uvs()[i][1]);
            data[o + 6] = 0;
            data[o + 7] = normal;
        }
        return data;
    }

    private static Spec spec(BlockState state) {
        return SPECS.get(state.getBlock());
    }

    // 26.1: shared BlockStateModel part
    //? if >=26.1 {
    private static final QuadCache<net.minecraft.client.resources.model.geometry.BakedQuad> QUADS26 = new QuadCache<>(
            (quarter, sprite, face) -> {
                float[][] p = quarter.positions();
                float[][] uv = quarter.uvs();
                net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo t = ConnectedTextures.TEMPLATE.get();
                net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo material = t == null ? null
                        : withSprite(t, sprite);
                return new net.minecraft.client.resources.model.geometry.BakedQuad(
                        new org.joml.Vector3f(p[0][0], p[0][1], p[0][2]), new org.joml.Vector3f(p[1][0], p[1][1], p[1][2]),
                        new org.joml.Vector3f(p[2][0], p[2][1], p[2][2]), new org.joml.Vector3f(p[3][0], p[3][1], p[3][2]),
                        net.minecraft.client.model.geom.builders.UVPair.pack(uv[0][0], uv[0][1]),
                        net.minecraft.client.model.geom.builders.UVPair.pack(uv[1][0], uv[1][1]),
                        net.minecraft.client.model.geom.builders.UVPair.pack(uv[2][0], uv[2][1]),
                        net.minecraft.client.model.geom.builders.UVPair.pack(uv[3][0], uv[3][1]),
                        quarter.face(), material);
            });

    /** The material of the base model's face, carried into the quarters (layer, tint, shade); set while building. */
    private static final ThreadLocal<net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo> TEMPLATE =
            new ThreadLocal<>();

    private static net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo withSprite(
            net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo t, TextureAtlasSprite sprite) {
        //? if neoforge {
        return new net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo(sprite, t.layer(),
                t.itemRenderType(), t.tintIndex(), t.shade(), t.lightEmission(), t.ambientOcclusion());
        //?} else {
        /*return new net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo(sprite, t.layer(),
                t.itemRenderType(), t.tintIndex(), t.shade(), t.lightEmission());
        *///?}
    }

    /** The world-aware part: the 24 quarters under their faces (culled like the faces they replace). */
    static final class Part26 implements net.minecraft.client.renderer.block.dispatch.BlockStateModelPart {
        private final net.minecraft.client.renderer.block.dispatch.BlockStateModelPart base;
        private final List<net.minecraft.client.resources.model.geometry.BakedQuad>[] byFace;

        Part26(net.minecraft.client.renderer.block.dispatch.BlockStateModelPart base,
               List<net.minecraft.client.resources.model.geometry.BakedQuad>[] byFace) {
            this.base = base;
            this.byFace = byFace;
        }

        @Override
        public List<net.minecraft.client.resources.model.geometry.BakedQuad> getQuads(@org.jspecify.annotations.Nullable Direction direction) {
            return direction == null ? List.of() : byFace[direction.get3DDataValue()];
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean useAmbientOcclusion() {
            return base.useAmbientOcclusion();
        }

        @Override
        public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial() {
            return base.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return base.materialFlags();
        }
    }

    /** The base part of a wrapped model and the world-aware part replacing it, or null if the model is not CT. */
    static net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part26(
            net.minecraft.client.renderer.block.dispatch.BlockStateModel model, BlockGetter level, BlockPos pos,
            BlockState state, net.minecraft.util.RandomSource random) {
        Spec spec = spec(state);
        List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> baseParts = new ArrayList<>();
        model.collectParts(random, baseParts);
        if (spec == null || baseParts.isEmpty()) return null;
        net.minecraft.client.renderer.block.dispatch.BlockStateModelPart base = baseParts.get(0);
        List<net.minecraft.client.resources.model.geometry.BakedQuad> faceQuads = base.getQuads(Direction.UP);
        TEMPLATE.set(faceQuads.isEmpty() ? null : faceQuads.get(0).materialInfo());
        try {
            return new Part26(base, QUADS26.quads(spec, state, fragments(level, pos, state, spec)));
        } finally {
            TEMPLATE.remove();
        }
    }
    //?}

    // 26.1 NeoForge
    //? if >=26.1 && neoforge {
    static final class Model26 extends net.neoforged.neoforge.client.model.DelegateBlockStateModel
            implements net.neoforged.neoforge.client.model.DynamicBlockStateModel {

        Model26(net.minecraft.client.renderer.block.dispatch.BlockStateModel delegate) {
            super(delegate);
        }

        @Override
        public void collectParts(net.minecraft.client.renderer.block.BlockAndTintGetter level, BlockPos pos, BlockState state,
                                 net.minecraft.util.RandomSource random,
                                 List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts) {
            net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part = part26(delegate, level, pos, state, random);
            if (part == null) delegate.collectParts(level, pos, state, random, parts);
            else parts.add(part);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void collectParts(net.minecraft.util.RandomSource random,
                                 List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts) {
            delegate.collectParts(random, parts);
        }
    }

    private static void install() {
        if (installed) return;
        installed = true;
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) -> {
                    resolve();
                    int count = 0;
                    for (Map.Entry<BlockState, net.minecraft.client.renderer.block.dispatch.BlockStateModel> entry
                            : event.getBakingResult().blockStateModels().entrySet()) {
                        if (SPECS.containsKey(entry.getKey().getBlock())) {
                            entry.setValue(new Model26(entry.getValue()));
                            count++;
                        }
                    }
                    wrapped = count;
                });
    }
    //?}

    // 26.1 Fabric
    //? if >=26.1 && fabric {
    /*static final class Model26 implements net.minecraft.client.renderer.block.dispatch.BlockStateModel {
        private final net.minecraft.client.renderer.block.dispatch.BlockStateModel delegate;

        Model26(net.minecraft.client.renderer.block.dispatch.BlockStateModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public void collectParts(net.minecraft.util.RandomSource random,
                                 List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts) {
            delegate.collectParts(random, parts);
        }

        @Override
        public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial() {
            return delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return delegate.materialFlags();
        }

        @Override
        public void emitQuads(net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter emitter,
                              net.minecraft.client.renderer.block.BlockAndTintGetter level, BlockPos pos, BlockState state,
                              net.minecraft.util.RandomSource random,
                              java.util.function.Predicate<@org.jspecify.annotations.Nullable Direction> cullTest) {
            net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part = part26(delegate, level, pos, state, random);
            if (part == null) delegate.emitQuads(emitter, level, pos, state, random, cullTest);
            else part.emitQuads(emitter, cullTest);
        }
    }

    private static void install() {
        if (installed) return;
        installed = true;
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context ->
                context.modifyBlockModelAfterBake().register((model, ctx) -> {
                    resolve();
                    if (!SPECS.containsKey(ctx.state().getBlock())) return model;
                    wrapped++;
                    return new Model26(model);
                }));
    }
    *///?}

    // legacy: shared quads
    //? if <26.1 {
    /*private static final QuadCache<net.minecraft.client.renderer.block.model.BakedQuad> LEGACY_QUADS = new QuadCache<>(
            (quarter, sprite, face) -> new net.minecraft.client.renderer.block.model.BakedQuad(legacyVertices(quarter), -1,
                    quarter.face(), sprite, true));

    private static java.util.Set<Object> modelIds() {
        java.util.Set<Object> ids = new java.util.HashSet<>();
        for (Block block : SPECS.keySet()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ids.add(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state));
            }
        }
        return ids;
    }
    *///?}

    // 1.21.1 NeoForge / 1.20.1 Forge: ModelData
    //? if neoforge && <26.1 {
    /*private static final net.neoforged.neoforge.client.model.data.ModelProperty<int[]> TYPES =
            new net.neoforged.neoforge.client.model.data.ModelProperty<>();

    static final class LegacyModel extends net.neoforged.neoforge.client.model.BakedModelWrapper<net.minecraft.client.resources.model.BakedModel> {
        LegacyModel(net.minecraft.client.resources.model.BakedModel original) {
            super(original);
        }

        @Override
        public net.neoforged.neoforge.client.model.data.ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,
                BlockPos pos, BlockState state, net.neoforged.neoforge.client.model.data.ModelData data) {
            Spec spec = spec(state);
            if (spec == null) return super.getModelData(level, pos, state, data);
            return data.derive().with(TYPES, fragments(level, pos, state, spec)).build();
        }

        @Override
        public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(@org.jspecify.annotations.Nullable BlockState state,
                @org.jspecify.annotations.Nullable Direction side, net.minecraft.util.RandomSource rand,
                net.neoforged.neoforge.client.model.data.ModelData data,
                net.minecraft.client.renderer.@org.jspecify.annotations.Nullable RenderType renderType) {
            int[] types = data.get(TYPES);
            Spec spec = state == null ? null : spec(state);
            if (types == null || spec == null) return super.getQuads(state, side, rand, data, renderType);
            if (side == null) return List.of();
            return LEGACY_QUADS.quads(spec, state, types, side.get3DDataValue());
        }
    }

    private static void install() {
        if (installed) return;
        installed = true;
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) -> {
                    resolve();
                    java.util.Set<Object> ids = modelIds();
                    int count = 0;
                    for (var entry : event.getModels().entrySet()) {
                        if (ids.contains(entry.getKey())) {
                            entry.setValue(new LegacyModel(entry.getValue()));
                            count++;
                        }
                    }
                    wrapped = count;
                });
    }
    *///?}

    //? if forge {
    /*private static final net.minecraftforge.client.model.data.ModelProperty<int[]> TYPES =
            new net.minecraftforge.client.model.data.ModelProperty<>();

    static final class LegacyModel extends net.minecraftforge.client.model.BakedModelWrapper<net.minecraft.client.resources.model.BakedModel> {
        LegacyModel(net.minecraft.client.resources.model.BakedModel original) {
            super(original);
        }

        @Override
        public net.minecraftforge.client.model.data.ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,
                BlockPos pos, BlockState state, net.minecraftforge.client.model.data.ModelData data) {
            Spec spec = spec(state);
            if (spec == null) return super.getModelData(level, pos, state, data);
            return data.derive().with(TYPES, fragments(level, pos, state, spec)).build();
        }

        @Override
        public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(@org.jspecify.annotations.Nullable BlockState state,
                @org.jspecify.annotations.Nullable Direction side, net.minecraft.util.RandomSource rand,
                net.minecraftforge.client.model.data.ModelData data,
                net.minecraft.client.renderer.@org.jspecify.annotations.Nullable RenderType renderType) {
            int[] types = data.get(TYPES);
            Spec spec = state == null ? null : spec(state);
            if (types == null || spec == null) return super.getQuads(state, side, rand, data, renderType);
            if (side == null) return List.of();
            return LEGACY_QUADS.quads(spec, state, types, side.get3DDataValue());
        }
    }

    private static void install() {
        if (installed) return;
        installed = true;
        dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event) -> {
                    resolve();
                    java.util.Set<Object> ids = modelIds();
                    int count = 0;
                    for (var entry : event.getModels().entrySet()) {
                        if (ids.contains(entry.getKey())) {
                            entry.setValue(new LegacyModel(entry.getValue()));
                            count++;
                        }
                    }
                    wrapped = count;
                });
    }
    *///?}

    // 1.20.1 / 1.21.1 Fabric: FabricBakedModel
    //? if fabric && <26.1 {
    /*static final class LegacyModel extends net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel {
        LegacyModel(net.minecraft.client.resources.model.BakedModel original) {
            this.wrapped = original;
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitBlockQuads(net.minecraft.world.level.BlockAndTintGetter level, BlockState state, BlockPos pos,
                                   java.util.function.Supplier<net.minecraft.util.RandomSource> random,
                                   net.fabricmc.fabric.api.renderer.v1.render.RenderContext context) {
            Spec spec = spec(state);
            net.fabricmc.fabric.api.renderer.v1.Renderer renderer = net.fabricmc.fabric.api.renderer.v1.RendererAccess.INSTANCE.getRenderer();
            if (spec == null || renderer == null) {
                super.emitBlockQuads(level, state, pos, random, context);
                return;
            }
            List<net.minecraft.client.renderer.block.model.BakedQuad>[] byFace =
                    LEGACY_QUADS.quads(spec, state, fragments(level, pos, state, spec));
            net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial material = renderer.materialFinder().find();
            net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter emitter = context.getEmitter();
            for (Direction face : Direction.values()) {
                for (net.minecraft.client.renderer.block.model.BakedQuad quad : byFace[face.get3DDataValue()]) {
                    emitter.fromVanilla(quad, material, face);
                    emitter.emit();
                }
            }
        }
    }

    private static void install() {
        if (installed) return;
        installed = true;
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context -> {
            resolve();
            java.util.Set<Object> ids = modelIds();
            context.modifyModelAfterBake().register((model, ctx) -> {
                    if (model == null) return null;
                    //? if >=1.21 {
                    Object id = ctx.topLevelId();
                    //?} else {
                    /^Object id = ctx.id();^/
                    //?}
                    if (id == null || !ids.contains(id)) return model;
                    wrapped++;
                    return new LegacyModel(model);
                });
        });
    }
    *///?}
}
