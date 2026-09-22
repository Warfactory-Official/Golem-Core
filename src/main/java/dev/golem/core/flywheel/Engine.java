package dev.golem.core.flywheel;

//? if >=26.1 {

import org.joml.Matrix4fc;

/**
 * Server-safe entry point for the vendored render engine.
 *
 * <p>Fabric's dedicated-server class loader verifies referenced types eagerly. Keeping the actual
 * implementation behind the nested client boundary means loading this class on a server reaches the
 * deliberate physical-side gate before any client-only type is resolved. Client render hooks call the
 * same API and resolve {@link ClientEngine} only when a draw method is actually invoked.
 */
public final class Engine {

    static {
        if (!dev.golem.core.platform.Platform.isClient()) {
            throw new UnsupportedOperationException("Flywheel's vendored engine is client-only");
        }
    }

    private Engine() {
    }

    public static Matrix4fc cameraModelView() {
        return Client.cameraModelView();
    }

    public static void onResourceReload() {
        Client.onResourceReload();
    }

    public static void drawOpaque(Matrix4fc modelView) {
        Client.drawOpaque(modelView);
    }

    public static void drawTranslucent(Matrix4fc modelView) {
        Client.drawTranslucent(modelView);
    }

    public static void drawTranslucentFabulous(Matrix4fc modelView) {
        Client.drawTranslucentFabulous(modelView);
    }

    /** Accepts the loader-specific level render state without naming that client-only type here. */
    public static void drawCrumbling(Matrix4fc modelView, Object levelState) {
        Client.drawCrumbling(modelView, levelState);
    }

    /**
     * This nested class is not initialized while {@link Engine} is being loaded. Its method bodies
     * are the only common entry point that names the client implementation.
     */
    private static final class Client {

        private Client() {
        }

        private static Matrix4fc cameraModelView() {
            return ClientEngine.cameraModelView();
        }

        private static void onResourceReload() {
            ClientEngine.onResourceReload();
        }

        private static void drawOpaque(Matrix4fc modelView) {
            ClientEngine.drawOpaque(modelView);
        }

        private static void drawTranslucent(Matrix4fc modelView) {
            ClientEngine.drawTranslucent(modelView);
        }

        private static void drawTranslucentFabulous(Matrix4fc modelView) {
            ClientEngine.drawTranslucentFabulous(modelView);
        }

        private static void drawCrumbling(Matrix4fc modelView, Object levelState) {
            ClientEngine.drawCrumbling(modelView, levelState);
        }
    }
}
//?}
