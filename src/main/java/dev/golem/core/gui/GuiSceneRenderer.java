package dev.golem.core.gui;

//? if >=26.1 {
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.render.VanillaSubmitSink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jspecify.annotations.Nullable;

/**
 * The picture-in-picture renderer behind {@link GuiScenes} on 26.1.
 *
 * <p>It is vanilla's {@code GuiEntityRenderer} without the entity: the offscreen target, the orthographic
 * projection and the base pose are the superclass's, the geometry goes to the feature dispatcher's node storage
 * and is drawn with {@code renderAllFeatures}, and the texture is blitted back where the screen asked.
 *
 * <p>The superclass hands {@link #renderToTexture} a pose whose origin is the middle of the target, in GUI
 * pixels, with Z mirrored so +Z points <em>away</em> from the viewer (the frame an entity model expects).
 * {@link GuiScenes} promises the legacy GUI frame, so Z is mirrored back once here.
 *
 * <p><b>Several scenes in one frame.</b> Vanilla keeps one renderer per render-state class, each with one
 * offscreen texture whose blit is recorded during prepare and drawn later, so two states of one class in a
 * frame would both show the last render. Scenes therefore take one of {@link #SLOTS} state classes, each with
 * its own renderer and texture, in draw order. Scenes beyond {@link #SLOTS} in a frame reuse slots and show a
 * later scene's picture.
 */
final class GuiSceneRenderer extends PictureInPictureRenderer<GuiSceneRenderer.State> {

    /** Distinct scenes one frame can show; each is one more offscreen texture (sized to its scene). */
    static final int SLOTS = 4;

    static sealed class State implements PictureInPictureRenderState permits Slot0, Slot1, Slot2, Slot3 {
        private final GuiScenes.Scene scene;
        private final int x0, y0, x1, y1;
        private final @Nullable ScreenRectangle bounds;

        State(GuiScenes.Scene scene, int x0, int y0, int x1, int y1) {
            this.scene = scene;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.bounds = PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null);
        }

        GuiScenes.Scene scene() { return scene; }
        @Override public int x0() { return x0; }
        @Override public int y0() { return y0; }
        @Override public int x1() { return x1; }
        @Override public int y1() { return y1; }
        @Override public float scale() { return 1.0F; }
        @Override public @Nullable ScreenRectangle scissorArea() { return null; }
        @Override public @Nullable ScreenRectangle bounds() { return bounds; }
    }

    static final class Slot0 extends State { Slot0(GuiScenes.Scene s, int a, int b, int c, int d) { super(s, a, b, c, d); } }
    static final class Slot1 extends State { Slot1(GuiScenes.Scene s, int a, int b, int c, int d) { super(s, a, b, c, d); } }
    static final class Slot2 extends State { Slot2(GuiScenes.Scene s, int a, int b, int c, int d) { super(s, a, b, c, d); } }
    static final class Slot3 extends State { Slot3(GuiScenes.Scene s, int a, int b, int c, int d) { super(s, a, b, c, d); } }

    private static final java.util.List<Class<? extends State>> SLOT_CLASSES =
            java.util.List.of(Slot0.class, Slot1.class, Slot2.class, Slot3.class);

    /** The extractor the last scene was submitted through: a new one means a new frame, so slots restart at 0. */
    private static java.lang.ref.WeakReference<GuiGraphicsExtractor> frame = new java.lang.ref.WeakReference<>(null);
    private static int nextSlot;

    private final Class<? extends State> stateClass;

    GuiSceneRenderer(MultiBufferSource.BufferSource bufferSource, Class<? extends State> stateClass) {
        super(bufferSource);
        this.stateClass = stateClass;
    }

    /** Adds the loader registration, one renderer per slot; the renderer list is built once, with the game renderer. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void register() {
        //? if neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) -> {
                    for (Class<? extends State> slot : SLOT_CLASSES) {
                        event.register((Class) slot, bufferSource -> new GuiSceneRenderer(bufferSource, slot));
                    }
                });
        //?} elif fabric {
        /*for (Class<? extends State> slot : SLOT_CLASSES) {
            net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(
                    context -> new GuiSceneRenderer(context.bufferSource(), slot));
        }
        *///?}
    }

    /** Queues one scene for this frame's picture-in-picture pass, in the next free slot of this frame. */
    static void submit(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, GuiScenes.Scene scene) {
        if (frame.get() != graphics) {
            frame = new java.lang.ref.WeakReference<>(graphics);
            nextSlot = 0;
        }
        int slot = nextSlot++ % SLOTS;
        State state = switch (slot) {
            case 0 -> new Slot0(scene, x0, y0, x1, y1);
            case 1 -> new Slot1(scene, x0, y0, x1, y1);
            case 2 -> new Slot2(scene, x0, y0, x1, y1);
            default -> new Slot3(scene, x0, y0, x1, y1);
        };
        // NeoForge adds a public submit; on Fabric the render state is private in vanilla and Fabric API
        // does not widen it, so golemcore.accesswidener does.
        //? if neoforge {
        graphics.submitPictureInPictureRenderState(state);
        //?} else {
        /*graphics.guiRenderState.addPicturesInPictureState(state);
        *///?}
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<State> getRenderStateClass() {
        return (Class<State>) (Class<?>) stateClass;
    }

    @Override
    protected void renderToTexture(State state, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        poseStack.scale(1.0F, 1.0F, -1.0F);
        FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
        state.scene().draw(poseStack, new VanillaSubmitSink(features.getSubmitNodeStorage(), new CameraRenderState()),
                GuiScenes.CAMERA);
        features.renderAllFeatures();
    }

    /** Centred vertically as well as horizontally, like the entity preview. */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "golem scene";
    }
}
//?}
