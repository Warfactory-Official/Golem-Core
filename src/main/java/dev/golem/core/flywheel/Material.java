package dev.golem.core.flywheel;

//? if >=26.1 {

public enum Material {
    CUTOUT(Stage.OPAQUE, true, true, false, false, false),
    CUTOUT_NO_CULL(Stage.OPAQUE, true, false, false, false, false),
    /** Cutout pipeline with a per-instance two-sided slab clip in fragment shader. Replaces
     *  1.7's {@code glClipPlane} pattern for sliding-door variants whose panels would otherwise
     *  poke through the frame on their far side. Per-instance state lives in the existing
     *  std140 instance struct, no extra slot:
     *  <ul>
     *    <li>{@link dev.golem.core.flywheel.instance.Instance#setRawColor InstanceColor.rgb}:
     *        slide offset applied in OBJ-local space before the model matrix. Vertex shader
     *        composes {@code slidPos = Position + slideOffset}.</li>
     *    <li>{@link dev.golem.core.flywheel.instance.Instance#setUV UVTransform} hijacked as
     *        the slab plane equation {@code (nx, ny, nz, halfExtent)}: fragment discards anywhere
     *        {@code abs(dot((nx,ny,nz), slidPos)) > halfExtent}. Sprite-strip UV animation isn't
     *        usable on clipped variants; the fragment shader uses {@code UV0} as is.</li>
     *  </ul>
     *  Vertex tint forced to white in clip mode (alpha preserved as dead-instance sentinel).
     *  Use for symmetric slides: sliding blast door (z=±2.5), round airlock (z=±1.999),
     *  large vehicle door (x=±3.4375). */
    CUTOUT_CLIP_SLAB(Stage.OPAQUE, true, true, false, false, false),
    /** Cutout pipeline with a per-instance single half-space clip: the fragment shader discards where
     *  {@code dot((nx,ny,nz), slidPos) > distance}. Same per-instance encoding as
     *  {@link #CUTOUT_CLIP_SLAB} (slide in {@code InstanceColor.rgb}, plane in
     *  {@code UVTransform}). Use for one-sided caps: sliding seal (back cap z ≤ 0.5001),
     *  qe_containment (top cap y ≤ 3). */
    CUTOUT_CLIP_HALFSPACE(Stage.OPAQUE, true, true, false, false, false),
    TRANSLUCENT(Stage.TRANSLUCENT, true, true, false, false, false),
    TRANSLUCENT_NO_CULL(Stage.TRANSLUCENT, true, false, false, false, false),
    /** Depth-writing entity translucency whose overlapping instances still require stable back-to-front order. */
    TRANSLUCENT_SORTED_NO_CULL(Stage.TRANSLUCENT, true, false, false, true, false),
    // Translucent without depth-write, for layered geometry (particle clouds, smoke, fog) where
    // many overlapping draws must compose alpha. With depth-write on, a partially transparent quad
    // claims full opacity and hides particles behind it at hard quad edges. Lighting is disabled
    // because the 1.7 particles were fullbright (writeFullbrightBillboard + RenderType.disableLighting).
    //
    // Under Fabulous transparency an untouched layer depth makes the post-chain (transparency.fsh)
    // sort the layer at terrain depth, so clouds and weather composite over the particles.
    // IndirectDrawManager therefore runs a second depth-only multidraw under Fabulous (see
    // drawStage) that writes the closest-particle depth with the colour mask off. Fancy/Fast and
    // shader-pack pipelines (Iris) skip it: vanilla clouds already write main's depth there.
    //
    // requiresDepthSort=true: the indirect backend sorts the visList slices every frame. A single
    // workgroup bitonic shader (golem_sort_bitonic.csh) handles groups that fit one TILE_SIZE
    // workgroup, otherwise a 2-pass byte radix (golem_sort_hist/scan/scatter.csh) over the high 16
    // bits of the packed visList entry (key16 << 16 | instIdx16). Without it the atomicAdd slot
    // allocation in golem_cull.csh is non-deterministic and overlapping quads flicker. Survivors
    // are ordered farthest-first so alpha blends back to front. There is no per-draw cap.
    TRANSLUCENT_NO_DEPTH_WRITE(Stage.TRANSLUCENT, false, true, true, true, true),
    TRANSLUCENT_NO_DEPTH_WRITE_NO_CULL(Stage.TRANSLUCENT, false, false, true, true, true),
    // Additive blending is order-independent (it is commutative), so no sort is needed even
    // though depth-write is off. Used for glow effects (rocket exhaust, plasma, RBMK flame).
    SOURCE_ALPHA_ADDITIVE_NO_CULL(Stage.TRANSLUCENT, false, false, true, false, false),
    ADDITIVE(Stage.TRANSLUCENT, false, true, true, false, false),
    ADDITIVE_NO_CULL(Stage.TRANSLUCENT, false, false, true, false, false),
    // Crumbling overlay material. Not a regular instancer bucket: visuals never request
    // it via instancer(...). Engine.onRenderCrumbling fetches the pipeline directly to draw the
    // destroy_stage_N atlas over visualized BEs being broken.
    CRUMBLING(Stage.TRANSLUCENT, false, true, false, false, false);

    public static final Material[] VALUES = values();

    public final Stage stage;
    public final boolean depthWrite;
    public final boolean cull;
    // Skip directional-light shading in the vertex shader. When true, vertexColor is the raw
    // per-instance InstanceColor instead of `minecraft_mix_light(...)`, as upstream's
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

    public enum Stage {
        OPAQUE,
        TRANSLUCENT
    }
}
//?}
