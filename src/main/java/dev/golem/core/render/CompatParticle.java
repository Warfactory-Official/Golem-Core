package dev.golem.core.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if <26.1 {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureAtlas;
*///?}
//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
//?}

/**
 * The base class for a mod's own textured, camera-facing particle.
 *
 * <p>26.1 merged the old {@code TextureSheetParticle} into {@code SingleQuadParticle} and replaced
 * per-particle GL state with an immutable {@code Layer} record; below 26.1 the two classes are
 * separate and the render type still sets GL state by hand. Both eras still give a subclass the same
 * three things — a sprite, a camera-facing quad, and a roll angle — so this shim reconciles the
 * spelling and lets particle subclasses be written once.
 *
 * <p>Two things worth knowing when porting 1.12 particles onto this:
 *
 * <ul>
 *   <li><b>Do not hand-roll the billboard.</b> 1.12 particles overrode {@code renderParticle} and
 *       built the quad themselves (HBM's {@code ParticleFXRotating} does, complete with a
 *       {@code //FIXME: doesnt spin, jitters around}). Modern {@code SingleQuadParticle.extract}
 *       already does camera-facing plus a {@code roll} rotation correctly — set {@link #oRoll}/
 *       {@link #roll} and delete the maths.
 *   <li><b>Fullbright is a lighting override, not a blend mode.</b> The 1.12 idiom of
 *       {@code getBrightnessForRender() -> 0xF000F0} maps onto {@link ParticleLayer#EMISSIVE}, which
 *       is handled here — subclasses should not override the light method themselves.
 * </ul>
 *
 * <p>Client-only.
 */
public abstract class CompatParticle extends
        //? if >=26.1 {
        net.minecraft.client.particle.SingleQuadParticle
        //?} else {
        /*net.minecraft.client.particle.TextureSheetParticle
        *///?}
{

    /** Full sky+block light, the modern spelling of 1.12's {@code 0xF000F0}. */
    private static final int FULLBRIGHT = 0xF000F0;

    private ParticleLayer golemLayer = ParticleLayer.TRANSLUCENT;
    private boolean planeOriented;
    private float planeYaw;
    private float planePitch;

    //? if >=26.1 {
    // PARTICLE_SNIPPET keeps the vanilla particle atlas bindings while this replaces only blend/depth.
    // The loader hooks register this pipeline alongside their normal particle pipeline registration.
    public static final RenderPipeline ADDITIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation("pipeline/golem_particle_additive")
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false)).build();
    private static final net.minecraft.client.particle.SingleQuadParticle.Layer ADDITIVE_LAYER =
            new net.minecraft.client.particle.SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, ADDITIVE_PIPELINE);
    //?}
    //? if <26.1 {
    /*private static final net.minecraft.client.particle.ParticleRenderType ADDITIVE_LAYER =
            new net.minecraft.client.particle.ParticleRenderType() {
                //? if >=1.21 {
                @Override
                public com.mojang.blaze3d.vertex.BufferBuilder begin(
                        com.mojang.blaze3d.vertex.Tesselator tesselator,
                        net.minecraft.client.renderer.texture.TextureManager textureManager) {
                    RenderSystem.depthMask(false);
                    RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    return tesselator.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
                }
                //?} else {
                /^@Override
                public void begin(com.mojang.blaze3d.vertex.BufferBuilder buffer,
                        net.minecraft.client.renderer.texture.TextureManager textureManager) {
                    RenderSystem.depthMask(false);
                    RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
                }
                ^///?}

                //? if <1.21 {
                /^@Override
                public void end(com.mojang.blaze3d.vertex.Tesselator tesselator) {
                    tesselator.end();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.depthMask(true);
                    RenderSystem.disableBlend();
                }
                ^///?}

                @Override
                public String toString() {
                    return "GOLEM_PARTICLE_ADDITIVE";
                }
            };
    *///?}

    protected CompatParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        //? if >=26.1 {
        super(level, x, y, z, sprite);
        //?} else {
        /*super(level, x, y, z);
        this.setSprite(sprite);
        *///?}
    }

    protected CompatParticle(ClientLevel level, double x, double y, double z,
            double xa, double ya, double za, TextureAtlasSprite sprite) {
        //? if >=26.1 {
        super(level, x, y, z, xa, ya, za, sprite);
        //?} else {
        /*super(level, x, y, z, xa, ya, za);
        this.setSprite(sprite);
        *///?}
    }

    /** Chooses how this particle's quad is drawn. Call from the subclass constructor. */
    protected final void setLayer(ParticleLayer layer) {
        this.golemLayer = layer;
    }

    /** Makes the quad lie in a world-oriented plane instead of facing the camera. */
    public final void setPlaneOrientation(float yaw, float pitch) {
        this.planeOriented = true;
        this.planeYaw = yaw;
        this.planePitch = pitch;
    }

    /** The layer this particle draws with. */
    public final ParticleLayer layer() {
        return this.golemLayer;
    }

    /**
     * Sets the quad's edge length. {@code quadSize} is the modern name for 1.12's
     * {@code particleScale}, but it is {@code protected} in a different package, so subclasses ported
     * from 1.12 get a setter rather than a field assignment.
     */
    protected final void setQuadSize(float size) {
        this.quadSize = size;
    }

    /** The current quad edge length. */
    protected final float quadSize() {
        return this.quadSize;
    }

    /**
     * Whether the quad should lie flat on the ground instead of facing the camera.
     *
     * <p>Ash that has settled, scorch decals and similar want a horizontal quad. 1.12 particles did
     * this by writing their own vertices from a hand-built basis; 1.21+ expresses it as a
     * {@code FacingCameraMode} — a rotation the engine applies instead of the camera's, so "flat" is
     * a quarter turn about X, and a subclass's {@code roll} still spins it in place because roll is
     * applied afterwards.
     *
     * <p>The Core-owned plane orientation seam is honoured on both eras. The older flat-particle
     * convenience remains intentionally limited to 1.21+, where vanilla exposes a facing-mode hook.
     */
    protected boolean isFlatOnGround() {
        return false;
    }

    //? if >=1.21 {
    @Override
    public net.minecraft.client.particle.SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        if (this.planeOriented) {
            return (target, camera, partialTick) -> target.identity()
                    .rotateY((float) Math.toRadians(this.planeYaw))
                    .rotateX((float) Math.toRadians(this.planePitch));
        }
        if (!this.isFlatOnGround()) return super.getFacingCameraMode();
        return (target, camera, partialTick) -> target.rotationX((float) (-Math.PI / 2D));
    }
    //?}

    /**
     * Called once per frame, just before the quad is built, with the frame's partial tick.
     *
     * <p>This is the seam for 1.12 particles that animated inside {@code renderParticle} — colour
     * ramps, alpha fades and size curves all lived there because it was the only place with a
     * partial-tick value. Modern particles have no such method: the engine reads {@code rCol}/
     * {@code gCol}/{@code bCol}/{@code alpha} straight off the fields and calls
     * {@code getQuadSize(partialTick)}. So set the colour fields here, and override
     * {@code getQuadSize} for the size curve.
     *
     * <p>Do <b>not</b> put GL state changes here — there is nowhere for them to go on 26.1. A
     * particle that needs a different blend mode wants a {@link ParticleLayer}.
     */
    protected void updateForFrame(float partialTick) {}

    /**
     * Receives one quad of a particle that draws several: {@code d*} offset the particle's own
     * interpolated position in world units, {@code size} is the quad's half-edge.
     */
    @FunctionalInterface
    public interface QuadSink {
        void quad(float dx, float dy, float dz, float size);
    }

    /**
     * Emits this particle's quads. The default emits the single camera-facing quad vanilla draws.
     *
     * <p>Override to draw a <b>cluster</b>. Several 1.12 HBM particles are not one quad at all
     * ({@code ParticleRocketFlame} is ten), which 1.12 allowed because a particle wrote its own
     * vertices; modern {@code SingleQuadParticle} emits exactly one and offers no seam for more.
     *
     * <p>The quads share this particle's position, rotation, sprite and light, but <b>not</b>
     * necessarily its colour: {@code setColor}/{@code setAlpha} may be called between emissions.
     */
    protected void emitQuads(QuadSink sink, float partialTick) {
        sink.quad(0F, 0F, 0F, this.getQuadSize(partialTick));
    }

    // The per-frame entry point: 26.1 extracts into a render state, older versions write vertices
    // directly. Both are called once per particle per frame, which is all updateForFrame needs, and
    // both then route the quads through emitQuads so a cluster particle works on either era.
    //? if >=26.1 {
    @Override
    public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState renderState,
            net.minecraft.client.Camera camera, float partialTick) {
        this.updateForFrame(partialTick);

        org.joml.Quaternionf rotation = new org.joml.Quaternionf();
        this.getFacingCameraMode().setRotation(rotation, camera, partialTick);
        if (this.roll != 0.0F) {
            rotation.rotateZ(net.minecraft.util.Mth.lerp(partialTick, this.oRoll, this.roll));
        }

        net.minecraft.world.phys.Vec3 cameraPos = camera.position();
        float baseX = (float) (net.minecraft.util.Mth.lerp(partialTick, this.xo, this.x) - cameraPos.x());
        float baseY = (float) (net.minecraft.util.Mth.lerp(partialTick, this.yo, this.y) - cameraPos.y());
        float baseZ = (float) (net.minecraft.util.Mth.lerp(partialTick, this.zo, this.z) - cameraPos.z());
        int light = this.getLightCoords(partialTick);

        this.emitQuads((dx, dy, dz, size) -> renderState.add(this.getLayer(),
                baseX + dx, baseY + dy, baseZ + dz,
                rotation.x, rotation.y, rotation.z, rotation.w, size,
                this.getU0(), this.getU1(), this.getV0(), this.getV1(),
                net.minecraft.util.ARGB.colorFromFloat(this.alpha, this.rCol, this.gCol, this.bCol),
                light), partialTick);
    }
    //?} else {
    /*@Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer,
            net.minecraft.client.Camera camera, float partialTick) {
        this.updateForFrame(partialTick);

        org.joml.Quaternionf rotation;
        //? if >=1.21 {
        rotation = new org.joml.Quaternionf();
        this.getFacingCameraMode().setRotation(rotation, camera, partialTick);
        //?} else {
        /^// 1.20.1 has no FacingCameraMode seam; the camera's own rotation is all there is.
        rotation = this.planeOriented
                ? new org.joml.Quaternionf().rotateY((float) Math.toRadians(this.planeYaw))
                        .rotateX((float) Math.toRadians(this.planePitch))
                : new org.joml.Quaternionf(camera.rotation());
        ^///?}
        if (this.roll != 0.0F) {
            rotation.rotateZ(net.minecraft.util.Mth.lerp(partialTick, this.oRoll, this.roll));
        }

        net.minecraft.world.phys.Vec3 cameraPos = camera.getPosition();
        float baseX = (float) (net.minecraft.util.Mth.lerp((double) partialTick, this.xo, this.x) - cameraPos.x());
        float baseY = (float) (net.minecraft.util.Mth.lerp((double) partialTick, this.yo, this.y) - cameraPos.y());
        float baseZ = (float) (net.minecraft.util.Mth.lerp((double) partialTick, this.zo, this.z) - cameraPos.z());
        int light = this.getLightColor(partialTick);

        this.emitQuads((dx, dy, dz, size) -> {
            float x = baseX + dx;
            float y = baseY + dy;
            float z = baseZ + dz;
            golemQuadVertex(buffer, rotation, x, y, z,  1F, -1F, size, this.getU1(), this.getV1(), light);
            golemQuadVertex(buffer, rotation, x, y, z,  1F,  1F, size, this.getU1(), this.getV0(), light);
            golemQuadVertex(buffer, rotation, x, y, z, -1F,  1F, size, this.getU0(), this.getV0(), light);
            golemQuadVertex(buffer, rotation, x, y, z, -1F, -1F, size, this.getU0(), this.getV1(), light);
        }, partialTick);
    }

    /^* One corner of a particle quad. The particle vertex format is position/uv/colour/light only. ^/
    private void golemQuadVertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, org.joml.Quaternionf rotation,
            float x, float y, float z, float cornerX, float cornerY, float size, float u, float v, int light) {
        org.joml.Vector3f corner = new org.joml.Vector3f(cornerX, cornerY, 0.0F)
                .rotate(rotation).mul(size).add(x, y, z);
        //? if >=1.21 {
        buffer.addVertex(corner.x(), corner.y(), corner.z())
                .setUv(u, v)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light);
        //?} else {
        /^buffer.vertex(corner.x(), corner.y(), corner.z())
                .uv(u, v)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light)
                .endVertex();
        ^///?}
    }
    *///?}

    // The light hook is named getLightCoords on 26.1 and getLightColor below it. Both have the same
    // contract (packed sky/block light for this particle at partialTick), so the override is
    // duplicated rather than facaded — there is nothing to translate beyond the name.
    //? if >=26.1 {
    @Override
    protected int getLightCoords(float partialTick) {
        return this.golemLayer.isFullbright() ? FULLBRIGHT : super.getLightCoords(partialTick);
    }
    //?} else {
    /*@Override
    protected int getLightColor(float partialTick) {
        return this.golemLayer.isFullbright() ? FULLBRIGHT : super.getLightColor(partialTick);
    }
    *///?}

    //? if >=26.1 {
    @Override
    protected net.minecraft.client.particle.SingleQuadParticle.Layer getLayer() {
        return this.golemLayer == ParticleLayer.ADDITIVE ? ADDITIVE_LAYER
                : net.minecraft.client.particle.SingleQuadParticle.Layer.TRANSLUCENT;
    }
    //?} else {
    /*@Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return this.golemLayer == ParticleLayer.ADDITIVE ? ADDITIVE_LAYER
                : net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    *///?}
}
