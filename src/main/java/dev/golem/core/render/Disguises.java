package dev.golem.core.render;

import dev.golem.core.Ids;
import dev.golem.core.render.gem.Geometry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Blocks whose look depends on where they stand, drawn into the chunk mesh like any block model and so lit by the world
 * (1.7's paintable blocks and its world-rendered pipes): the {@link Look}'s disguise or the block's own model, face
 * overlays just outside the cube, and a mesh picked by a key. A disguise takes the block's own tints, as 1.7's second
 * pass did; overlays and meshes are cut out. Hooked per loader as {@link ConnectedTextures} is.
 */
public final class Disguises {

    private Disguises() {}

    /** How a registered block looks at one position. Asked while a chunk section is meshed, off the main thread. */
    public interface Look {
        /** The block drawn in this block's place, or null to draw its own model. */
        default @Nullable BlockState disguise(BlockGetter level, BlockPos pos, BlockState state) {
            return null;
        }

        /** Overlays drawn over the result, in order. */
        default void overlays(BlockGetter level, BlockPos pos, BlockState state, Overlays out) {}

        /** What decides this position's mesh, or null for none; positions with equal keys share one built mesh. */
        default @Nullable Object meshKey(BlockGetter level, BlockPos pos, BlockState state) {
            return null;
        }

        /** The mesh for a key, in block space; built once per key and model bake. */
        default void mesh(Object key, Meshes out) {}

        /** False to light the block flat, as 1.7's own block renderers did. */
        default boolean ambientOcclusion() {
            return true;
        }
    }

    @FunctionalInterface
    public interface Overlays {
        /** A full-face overlay on {@code face}: a block-atlas sprite id and the block's tint index for it (-1 for none). */
        void face(Direction face, String sprite, int tintIndex);
    }

    @FunctionalInterface
    public interface Meshes {
        /** The triangles of {@code geometry} moved by {@code transform}, on a block-atlas sprite (UVs 0..1 of it). */
        void add(Geometry geometry, String sprite, int tintIndex, Matrix4fc transform);

        default void add(Geometry geometry, String sprite, int tintIndex) {
            add(geometry, sprite, tintIndex, IDENTITY);
        }
    }

    /** One quad in block space, before it becomes a loader's quad; {@code cull} is the face that hides it, if any. */
    record Quad(Direction direction, @Nullable Direction cull, float[][] positions, float[][] uvs,
                TextureAtlasSprite sprite, int tintIndex) {}

    private record Pending(Supplier<? extends Block> block, Look look) {}

    private record OverlayKey(Direction face, String sprite, int tintIndex) {}

    private record MeshKey(Look look, Object key) {}

    private record BuiltKey(Quad quad, boolean ao) {}

    private static final Matrix4fc IDENTITY = new Matrix4f();
    /** How far an overlay sits outside the cube: the 0.02 pixel of HBM's own overlay models. */
    private static final float OUTSET = 0.02F / 16F;
    private static final float EDGE = 1.0E-4F;

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<Block, Look> LOOKS = new ConcurrentHashMap<>();
    private static final Map<Object, Object> BUILT = new ConcurrentHashMap<>();
    private static boolean installed;
    private static volatile int wrapped;

    /**
     * Gives a block a look. Call from client initialisation, before the first resource load; the block is resolved at
     * the first model bake. The block must draw as a model ({@code RenderShape.MODEL}). After a change the look reads
     * (a block entity's data), the block's section has to be re-meshed, as for any block-entity-driven model.
     */
    public static synchronized void register(Supplier<? extends Block> block, Look look) {
        PENDING.add(new Pending(block, look));
        install();
    }

    public static int wrapped() {
        return wrapped;
    }

    /** A bake follows a re-stitch, whose sprites are new: built quads are dropped. */
    private static synchronized void resolve() {
        for (Pending pending : PENDING) LOOKS.put(pending.block().get(), pending.look());
        PENDING.clear();
        BUILT.clear();
    }

    private static @Nullable Look look(BlockState state) {
        return LOOKS.get(state.getBlock());
    }

    private static boolean ambientOcclusion(BlockState state) {
        Look look = look(state);
        return look == null || look.ambientOcclusion();
    }

    /** The overlay and mesh quads of one position. */
    static List<Quad> extras(Look look, BlockGetter level, BlockPos pos, BlockState state) {
        List<Quad> quads = new ArrayList<>();
        look.overlays(level, pos, state, (face, sprite, tint) -> quads.add((Quad) BUILT.computeIfAbsent(
                new OverlayKey(face, sprite, tint), k -> overlay(face, sprite(sprite), tint))));
        Object key = look.meshKey(level, pos, state);
        if (key != null) {
            @SuppressWarnings("unchecked")
            List<Quad> mesh = (List<Quad>) BUILT.computeIfAbsent(new MeshKey(look, key), k -> mesh(look, key));
            quads.addAll(mesh);
        }
        return quads;
    }

    /** A full face just outside the cube in vanilla's vertex order, with the default cube-face UVs of its sprite. */
    private static Quad overlay(Direction face, TextureAtlasSprite sprite, int tintIndex) {
        boolean[][] order = ConnectedTextures.VANILLA_ORDER[face.get3DDataValue()];
        float[][] positions = new float[4][];
        float[][] uvs = new float[4][];
        for (int i = 0; i < 4; i++) {
            float x = order[i][0] ? 0F : 1F, y = order[i][1] ? 0F : 1F, z = order[i][2] ? 0F : 1F;
            float[] uv = switch (face) {
                case DOWN -> new float[]{x, 1F - z};
                case UP -> new float[]{x, z};
                case NORTH -> new float[]{1F - x, 1F - y};
                case SOUTH -> new float[]{x, 1F - y};
                case WEST -> new float[]{z, 1F - y};
                case EAST -> new float[]{1F - z, 1F - y};
            };
            positions[i] = new float[]{x + face.getStepX() * OUTSET, y + face.getStepY() * OUTSET,
                    z + face.getStepZ() * OUTSET};
            uvs[i] = atlasUv(sprite, uv[0], uv[1]);
        }
        return new Quad(face, face, positions, uvs, sprite, tintIndex);
    }

    /** A look's mesh as quads: each triangle pair sharing an edge becomes one quad, a lone triangle a degenerate one. */
    private static List<Quad> mesh(Look look, Object key) {
        List<Quad> quads = new ArrayList<>();
        look.mesh(key, (geometry, spriteId, tint, transform) -> {
            TextureAtlasSprite sprite = sprite(spriteId);
            float[] v = geometry.vertices();
            int[] index = geometry.indices();
            for (int t = 0; t + 2 < index.length; t += 3) {
                int[] corners;
                if (t + 5 < index.length && index[t + 3] == index[t] && index[t + 4] == index[t + 2]) {
                    corners = new int[]{index[t], index[t + 1], index[t + 2], index[t + 5]};
                    t += 3;
                } else {
                    corners = new int[]{index[t], index[t + 1], index[t + 2], index[t + 2]};
                }
                quads.add(meshQuad(v, corners, sprite, tint, transform));
            }
        });
        return List.copyOf(quads);
    }

    private static Quad meshQuad(float[] v, int[] corners, TextureAtlasSprite sprite, int tint, Matrix4fc transform) {
        float[][] positions = new float[4][];
        float[][] uvs = new float[4][];
        for (int i = 0; i < 4; i++) {
            int at = corners[i] * Geometry.FLOATS_PER_VERTEX;
            Vector3f p = transform.transformPosition(new Vector3f(v[at], v[at + 1], v[at + 2]));
            positions[i] = new float[]{p.x, p.y, p.z};
            uvs[i] = atlasUv(sprite, v[at + 3], v[at + 4]);
        }
        Vector3f a = new Vector3f(positions[1][0] - positions[0][0], positions[1][1] - positions[0][1], positions[1][2] - positions[0][2]);
        Vector3f b = new Vector3f(positions[2][0] - positions[0][0], positions[2][1] - positions[0][1], positions[2][2] - positions[0][2]);
        Vector3f normal = a.cross(b);
        Direction direction = nearest(normal);
        return new Quad(direction, onFace(positions, direction) ? direction : null, positions, uvs, sprite, tint);
    }

    private static Direction nearest(Vector3f normal) {
        float x = Math.abs(normal.x), y = Math.abs(normal.y), z = Math.abs(normal.z);
        if (x >= y && x >= z) return normal.x >= 0 ? Direction.EAST : Direction.WEST;
        if (y >= z) return normal.y >= 0 ? Direction.UP : Direction.DOWN;
        return normal.z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Whether every corner lies in the block's own face toward {@code direction}, so that face's neighbour hides it. */
    private static boolean onFace(float[][] positions, Direction direction) {
        int axis = direction.getAxis().ordinal();
        float plane = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1F : 0F;
        for (float[] p : positions) if (Math.abs(p[axis] - plane) > EDGE) return false;
        return true;
    }

    private static float[] atlasUv(TextureAtlasSprite sprite, float u, float v) {
        return new float[]{sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u,
                sprite.getV0() + (sprite.getV1() - sprite.getV0()) * v};
    }

    private static TextureAtlasSprite sprite(String id) {
        return Sprites.sprite(Sprites.Atlas.BLOCKS, Ids.parse(id));
    }

    //? if >=26.1 {
    private static net.minecraft.client.renderer.block.dispatch.BlockStateModel model26(BlockState state) {
        return net.minecraft.client.Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }

    /** The overlays and mesh of one position as one part; null for none. */
    static net.minecraft.client.renderer.block.dispatch.@Nullable BlockStateModelPart extraPart26(
            net.minecraft.client.renderer.block.dispatch.BlockStateModel own, Look look, BlockGetter level, BlockPos pos,
            BlockState state) {
        List<Quad> extras = extras(look, level, pos, state);
        if (extras.isEmpty()) return null;
        @SuppressWarnings("unchecked")
        List<net.minecraft.client.resources.model.geometry.BakedQuad>[] byCull = new List[7];
        for (int i = 0; i < 7; i++) byCull[i] = new ArrayList<>();
        int flags = 0;
        for (Quad quad : extras) {
            net.minecraft.client.resources.model.geometry.BakedQuad baked = quad26(quad, look.ambientOcclusion());
            byCull[quad.cull() == null ? 6 : quad.cull().get3DDataValue()].add(baked);
            flags |= baked.materialInfo().flags();
        }
        return new ExtraPart26(own.particleMaterial(), byCull, look.ambientOcclusion(), flags);
    }

    private static net.minecraft.client.resources.model.geometry.BakedQuad quad26(Quad quad, boolean ao) {
        return (net.minecraft.client.resources.model.geometry.BakedQuad) BUILT.computeIfAbsent(new BuiltKey(quad, ao), k -> {
            float[][] p = quad.positions();
            float[][] uv = quad.uvs();
            net.minecraft.client.renderer.chunk.ChunkSectionLayer layer =
                    net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT;
            net.minecraft.client.renderer.rendertype.RenderType item = net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet();
            //? if neoforge {
            net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo material =
                    new net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo(quad.sprite(), layer, item,
                            quad.tintIndex(), true, 0, ao);
            //?} else {
            /*net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo material =
                    new net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo(quad.sprite(), layer, item,
                            quad.tintIndex(), true, 0);
            *///?}
            return new net.minecraft.client.resources.model.geometry.BakedQuad(
                    new Vector3f(p[0][0], p[0][1], p[0][2]), new Vector3f(p[1][0], p[1][1], p[1][2]),
                    new Vector3f(p[2][0], p[2][1], p[2][2]), new Vector3f(p[3][0], p[3][1], p[3][2]),
                    net.minecraft.client.model.geom.builders.UVPair.pack(uv[0][0], uv[0][1]),
                    net.minecraft.client.model.geom.builders.UVPair.pack(uv[1][0], uv[1][1]),
                    net.minecraft.client.model.geom.builders.UVPair.pack(uv[2][0], uv[2][1]),
                    net.minecraft.client.model.geom.builders.UVPair.pack(uv[3][0], uv[3][1]),
                    quad.direction(), material);
        });
    }

    static final class ExtraPart26 implements net.minecraft.client.renderer.block.dispatch.BlockStateModelPart {
        private final net.minecraft.client.resources.model.sprite.Material.Baked particle;
        private final List<net.minecraft.client.resources.model.geometry.BakedQuad>[] byCull;
        private final boolean ao;
        private final int flags;

        ExtraPart26(net.minecraft.client.resources.model.sprite.Material.Baked particle,
                    List<net.minecraft.client.resources.model.geometry.BakedQuad>[] byCull, boolean ao, int flags) {
            this.particle = particle;
            this.byCull = byCull;
            this.ao = ao;
            this.flags = flags;
        }

        @Override
        public List<net.minecraft.client.resources.model.geometry.BakedQuad> getQuads(@Nullable Direction direction) {
            return byCull[direction == null ? 6 : direction.get3DDataValue()];
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean useAmbientOcclusion() {
            return ao;
        }

        @Override
        public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial() {
            return particle;
        }

        @Override
        public int materialFlags() {
            return flags;
        }
    }
    //?}

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
            Look look = look(state);
            BlockState disguise = look == null ? null : look.disguise(level, pos, state);
            if (disguise != null) model26(disguise).collectParts(level, pos, disguise, random, parts);
            else delegate.collectParts(level, pos, state, random, parts);
            if (look == null) return;
            net.minecraft.client.renderer.block.dispatch.BlockStateModelPart extras = extraPart26(delegate, look, level, pos, state);
            if (extras != null) parts.add(extras);
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
                        if (LOOKS.containsKey(entry.getKey().getBlock())) {
                            entry.setValue(new Model26(entry.getValue()));
                            count++;
                        }
                    }
                    wrapped = count;
                });
    }
    //?}

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
                              java.util.function.Predicate<@Nullable Direction> cullTest) {
            Look look = look(state);
            BlockState disguise = look == null ? null : look.disguise(level, pos, state);
            if (disguise != null) model26(disguise).emitQuads(emitter, level, pos, disguise, random, cullTest);
            else delegate.emitQuads(emitter, level, pos, state, random, cullTest);
            if (look == null) return;
            net.minecraft.client.renderer.block.dispatch.BlockStateModelPart extras = extraPart26(delegate, look, level, pos, state);
            if (extras != null) extras.emitQuads(emitter, cullTest);
        }
    }

    private static void install() {
        if (installed) return;
        installed = true;
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context ->
                context.modifyBlockModelAfterBake().register((model, ctx) -> {
                    resolve();
                    if (!LOOKS.containsKey(ctx.state().getBlock())) return model;
                    wrapped++;
                    return new Model26(model);
                }));
    }
    *///?}

    //? if <26.1 {
    /*private static net.minecraft.client.resources.model.BakedModel legacyModel(BlockState state) {
        return net.minecraft.client.Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    private static net.minecraft.client.renderer.block.model.BakedQuad legacyQuad(Quad quad) {
        return (net.minecraft.client.renderer.block.model.BakedQuad) BUILT.computeIfAbsent(new BuiltKey(quad, true),
                k -> new net.minecraft.client.renderer.block.model.BakedQuad(ConnectedTextures.legacyVertices(
                        new ConnectedTextures.Quarter(quad.direction(), quad.positions(), quad.uvs())),
                        quad.tintIndex(), quad.direction(), quad.sprite(), true));
    }

    private static java.util.Set<Object> modelIds() {
        return ConnectedTextures.modelIds(LOOKS.keySet());
    }
    *///?}

    //? if neoforge && <26.1 {
    /*private record Appearance(@Nullable BlockState disguise, net.neoforged.neoforge.client.model.data.ModelData disguiseData,
                              List<Quad> extras) {}

    private static final net.neoforged.neoforge.client.model.data.ModelProperty<Appearance> APPEARANCE =
            new net.neoforged.neoforge.client.model.data.ModelProperty<>();

    static final class LegacyModel extends net.neoforged.neoforge.client.model.BakedModelWrapper<net.minecraft.client.resources.model.BakedModel> {
        LegacyModel(net.minecraft.client.resources.model.BakedModel original) {
            super(original);
        }

        @Override
        public net.neoforged.neoforge.client.model.data.ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,
                BlockPos pos, BlockState state, net.neoforged.neoforge.client.model.data.ModelData data) {
            Look look = look(state);
            if (look == null) return super.getModelData(level, pos, state, data);
            BlockState disguise = look.disguise(level, pos, state);
            net.neoforged.neoforge.client.model.data.ModelData own = super.getModelData(level, pos, state, data);
            net.neoforged.neoforge.client.model.data.ModelData disguiseData = disguise == null
                    ? net.neoforged.neoforge.client.model.data.ModelData.EMPTY
                    : legacyModel(disguise).getModelData(level, pos, disguise, net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
            return own.derive().with(APPEARANCE, new Appearance(disguise, disguiseData, extras(look, level, pos, state))).build();
        }

        @Override
        public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(BlockState state,
                net.minecraft.util.RandomSource rand, net.neoforged.neoforge.client.model.data.ModelData data) {
            return net.neoforged.neoforge.client.ChunkRenderTypeSet.of(net.minecraft.client.renderer.RenderType.cutout());
        }

        @Override
        public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,
                net.neoforged.neoforge.client.model.data.ModelData data, net.minecraft.client.renderer.RenderType renderType) {
            return ambientOcclusion(state) ? super.useAmbientOcclusion(state, data, renderType)
                    : net.neoforged.neoforge.common.util.TriState.FALSE;
        }

        @Override
        public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(@Nullable BlockState state,
                @Nullable Direction side, net.minecraft.util.RandomSource rand,
                net.neoforged.neoforge.client.model.data.ModelData data,
                net.minecraft.client.renderer.@Nullable RenderType renderType) {
            Appearance appearance = data.get(APPEARANCE);
            if (appearance == null || state == null) return super.getQuads(state, side, rand, data, renderType);
            List<net.minecraft.client.renderer.block.model.BakedQuad> quads = new ArrayList<>(appearance.disguise() == null
                    ? super.getQuads(state, side, rand, data, null)
                    : legacyModel(appearance.disguise()).getQuads(appearance.disguise(), side, rand,
                            appearance.disguiseData(), null));
            for (Quad quad : appearance.extras()) if (quad.cull() == side) quads.add(legacyQuad(quad));
            return quads;
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
    /*private record Appearance(@Nullable BlockState disguise, net.minecraftforge.client.model.data.ModelData disguiseData,
                              List<Quad> extras) {}

    private static final net.minecraftforge.client.model.data.ModelProperty<Appearance> APPEARANCE =
            new net.minecraftforge.client.model.data.ModelProperty<>();

    static final class LegacyModel extends net.minecraftforge.client.model.BakedModelWrapper<net.minecraft.client.resources.model.BakedModel> {
        LegacyModel(net.minecraft.client.resources.model.BakedModel original) {
            super(original);
        }

        @Override
        public net.minecraftforge.client.model.data.ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,
                BlockPos pos, BlockState state, net.minecraftforge.client.model.data.ModelData data) {
            Look look = look(state);
            if (look == null) return super.getModelData(level, pos, state, data);
            BlockState disguise = look.disguise(level, pos, state);
            net.minecraftforge.client.model.data.ModelData own = super.getModelData(level, pos, state, data);
            net.minecraftforge.client.model.data.ModelData disguiseData = disguise == null
                    ? net.minecraftforge.client.model.data.ModelData.EMPTY
                    : legacyModel(disguise).getModelData(level, pos, disguise, net.minecraftforge.client.model.data.ModelData.EMPTY);
            return own.derive().with(APPEARANCE, new Appearance(disguise, disguiseData, extras(look, level, pos, state))).build();
        }

        @Override
        public net.minecraftforge.client.ChunkRenderTypeSet getRenderTypes(BlockState state,
                net.minecraft.util.RandomSource rand, net.minecraftforge.client.model.data.ModelData data) {
            return net.minecraftforge.client.ChunkRenderTypeSet.of(net.minecraft.client.renderer.RenderType.cutout());
        }

        @Override
        public boolean useAmbientOcclusion(BlockState state, net.minecraft.client.renderer.RenderType renderType) {
            return ambientOcclusion(state) && super.useAmbientOcclusion(state, renderType);
        }

        @Override
        public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(@Nullable BlockState state,
                @Nullable Direction side, net.minecraft.util.RandomSource rand,
                net.minecraftforge.client.model.data.ModelData data,
                net.minecraft.client.renderer.@Nullable RenderType renderType) {
            Appearance appearance = data.get(APPEARANCE);
            if (appearance == null || state == null) return super.getQuads(state, side, rand, data, renderType);
            List<net.minecraft.client.renderer.block.model.BakedQuad> quads = new ArrayList<>(appearance.disguise() == null
                    ? super.getQuads(state, side, rand, data, null)
                    : legacyModel(appearance.disguise()).getQuads(appearance.disguise(), side, rand,
                            appearance.disguiseData(), null));
            for (Quad quad : appearance.extras()) if (quad.cull() == side) quads.add(legacyQuad(quad));
            return quads;
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
                                   Supplier<net.minecraft.util.RandomSource> random,
                                   net.fabricmc.fabric.api.renderer.v1.render.RenderContext context) {
            Look look = look(state);
            net.fabricmc.fabric.api.renderer.v1.Renderer renderer = net.fabricmc.fabric.api.renderer.v1.RendererAccess.INSTANCE.getRenderer();
            if (look == null || renderer == null) {
                super.emitBlockQuads(level, state, pos, random, context);
                return;
            }
            BlockState disguise = look.disguise(level, pos, state);
            if (disguise != null) {
                ((net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel) legacyModel(disguise))
                        .emitBlockQuads(level, disguise, pos, random, context);
            } else {
                super.emitBlockQuads(level, state, pos, random, context);
            }
            net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial material = renderer.materialFinder()
                    .blendMode(net.fabricmc.fabric.api.renderer.v1.material.BlendMode.CUTOUT)
                    .ambientOcclusion(look.ambientOcclusion() ? net.fabricmc.fabric.api.util.TriState.DEFAULT
                            : net.fabricmc.fabric.api.util.TriState.FALSE)
                    .find();
            net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter emitter = context.getEmitter();
            for (Quad quad : extras(look, level, pos, state)) {
                emitter.fromVanilla(legacyQuad(quad), material, quad.cull());
                emitter.emit();
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
