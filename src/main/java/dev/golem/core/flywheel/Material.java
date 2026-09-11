package dev.golem.core.flywheel;

//? if neoforge && >=26.1 {

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public enum Material {
    CUTOUT(Stage.OPAQUE, true, true, false, false, false),
    CUTOUT_NO_CULL(Stage.OPAQUE, true, false, false, false, false),
    /** Cutout pipeline with a per-instance two-sided slab clip in fragment shader. Replaces
     *  1.7's {@code glClipPlane} pattern for sliding-door variants whose panels would otherwise
     *  poke through the frame on their far side. Per-instance state lives in the existing
     *  std140 instance struct — no extra slot:
     *  <ul>
     *    <li>{@link dev.golem.core.flywheel.instance.Instance#setRawColor InstanceColor.rgb} —
     *        slide offset applied in OBJ-local space before the model matrix. Vertex shader
     *        composes {@code slidPos = Position + slideOffset}.</li>
     *    <li>{@link dev.golem.core.flywheel.instance.Instance#setUV UVTransform} hijacked as
     *        the slab plane equation {@code (nx, ny, nz, halfExtent)}: fragment discards anywhere
     *        {@code abs(dot((nx,ny,nz), slidPos)) > halfExtent}. Sprite-strip UV animation isn't
     *        usable on clipped variants — fragment shader uses {@code UV0} verbatim.</li>
     *  </ul>
     *  Vertex tint forced to white in clip mode (alpha preserved as dead-instance sentinel).
     *  Use for symmetric-slide cases — sliding blast door (z=±2.5), round airlock (z=±1.999),
     *  large vehicle door (x=±3.4375). */
    CUTOUT_CLIP_SLAB(Stage.OPAQUE, true, true, false, false, false),
    /** Cutout pipeline with a per-instance single half-space clip — fragment discards where
     *  {@code dot((nx,ny,nz), slidPos) > distance}. Same per-instance encoding as
     *  {@link #CUTOUT_CLIP_SLAB} (slide in {@code InstanceColor.rgb}, plane in
     *  {@code UVTransform}). Use for one-sided caps — sliding seal (back cap z ≤ 0.5001),
     *  qe_containment (top cap y ≤ 3). */
    CUTOUT_CLIP_HALFSPACE(Stage.OPAQUE, true, true, false, false, false),
    TRANSLUCENT(Stage.TRANSLUCENT, true, true, false, false, false),
    TRANSLUCENT_NO_CULL(Stage.TRANSLUCENT, true, false, false, false, false),
    /** Depth-writing entity translucency whose overlapping instances still require stable back-to-front order. */
    TRANSLUCENT_SORTED_NO_CULL(Stage.TRANSLUCENT, true, false, false, true, false),
    // Translucent without depth-write. Used for layered translucent geometry (particle clouds,
    // smoke, fog) where many overlapping draws must compose alpha correctly. With depth-write
    // on, partially-transparent quads claim full opacity in the depth buffer — overlapping
    // particles behind a closer one fail LESS_THAN_OR_EQUAL and disappear at hard quad edges,
    // independent of their actual alpha. depth-write off lets every sorted sub-quad blend
    // cumulatively. Lighting is also disabled — translucent particles upstream were rendered
    // fullbright (writeFullbrightBillboard + RenderType.disableLighting) so directional shading
    // would tint colors away from the artist-picked particleRGB.
    //
    // Under Fabulous transparency, leaving the translucent layer's depth untouched means the
    // post-chain (transparency.fsh) sorts the layer at terrain depth — clouds/weather then
    // composite on top of our particles regardless of distance. Fix: IndirectDrawManager runs
    // a SECOND depth-only multidraw under Fabulous (see drawStage), writing closest-particle
    // depth via a depth-only pipeline with color-mask off. Color pass keeps depth-write off
    // for correct intra-cluster blending; depth pass feeds the post-chain. Fancy/Fast and
    // shader-pack pipelines (Iris) skip the depth pass entirely — vanilla clouds already write
    // to main's depth in those modes, and Iris runs its own deferred sort outside our path.
    //
    // requiresDepthSort=true: the indirect backend's per-frame GPU sort runs on visList for
    // these materials. Two paths share the entry-point: a single-workgroup bitonic shader
    // (golem_sort_bitonic.csh) when every draw in the group fits in one TILE_SIZE workgroup,
    // otherwise a 2-pass byte-radix (golem_sort_hist/scan/scatter.csh) over the high 16 bits of
    // the packed visList entry. Without sort, the GPU's atomicAdd-based slot allocation in
    // golem_cull.csh is non-deterministic across frames and overlapping translucent quads
    // flicker. Cull writes packed (key16 << 16 | instIdx16) per survivor; the sort orders
    // survivors farthest-first in [0, instanceCount) so alpha blends back-to-front correctly.
    // No per-draw cap — slices are sized exactly to the live count.
    TRANSLUCENT_NO_DEPTH_WRITE(Stage.TRANSLUCENT, false, true, true, true, true),
    TRANSLUCENT_NO_DEPTH_WRITE_NO_CULL(Stage.TRANSLUCENT, false, false, true, true, true),
    // Additive blending — order-independent (glAdd is commutative), so no sort needed even
    // though depth-write is off. Used for glow effects (rocket exhaust, plasma, RBMK flame).
    SOURCE_ALPHA_ADDITIVE_NO_CULL(Stage.TRANSLUCENT, false, false, true, false, false),
    ADDITIVE(Stage.TRANSLUCENT, false, true, true, false, false),
    ADDITIVE_NO_CULL(Stage.TRANSLUCENT, false, false, true, false, false),
    // Crumbling overlay material. Not used as a regular instancer bucket — visuals never request
    // it via instancer(...). Engine.onRenderCrumbling fetches the pipeline directly to draw the
    // destroy_stage_N atlas over visualized BEs being broken.
    CRUMBLING(Stage.TRANSLUCENT, false, true, false, false, false);

    public static final Material[] VALUES = values();

    public final Stage stage;
    public final boolean depthWrite;
    public final boolean cull;
    // Skip directional-light shading in the vertex shader. When true, vertexColor is the raw
    // per-instance InstanceColor instead of `minecraft_mix_light(...)` — matches upstream's
    // `RenderType.disableLighting=true` for particle render paths.
    public final boolean disableLighting;
    // Run the per-frame depth-sort compute pass on this material's visList slices in the
    // indirect backend. See class-level comment on TRANSLUCENT_NO_DEPTH_WRITE.
    public final boolean requiresDepthSort;
    /** Whether Fabulous needs a second color-masked pass to preserve this no-depth-write layer's depth. */
    public final boolean requiresFabulousDepth;

    Material(Stage stage, boolean depthWrite, boolean cull, boolean disableLighting,
             boolean requiresDepthSort, boolean requiresFabulousDepth) {
        this.stage = stage;
        this.depthWrite = depthWrite;
        this.cull = cull;
        this.disableLighting = disableLighting;
        this.requiresDepthSort = requiresDepthSort;
        this.requiresFabulousDepth = requiresFabulousDepth;
    }

    public boolean isStage(RenderLevelStageEvent event) {
        return stage.matches(event);
    }

    public enum Stage {
        OPAQUE,
        TRANSLUCENT;

        boolean matches(RenderLevelStageEvent event) {
            return switch (this) {
                case OPAQUE -> event instanceof RenderLevelStageEvent.AfterOpaqueFeatures;
                case TRANSLUCENT -> event instanceof RenderLevelStageEvent.AfterWeather
                        || event instanceof RenderLevelStageEvent.AfterTranslucentBlocks;
            };
        }
    }
}
//?}
