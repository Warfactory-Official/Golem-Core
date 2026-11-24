package dev.golem.probe;

import dev.golem.core.client.Precipitation;
import dev.golem.core.render.Sky;
import dev.golem.core.render.Skyboxes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A skybox and overworld weather for screenshots: while the player holds a debug stick, a magenta quad replaces the sun
 * and moon overhead, a cyan quad follows the sky, and red weather streaks hang three blocks north of the camera.
 */
public final class ProbeClient {

    private ProbeClient() {}

    /** Full block and sky light, packed as {@code Frame.quads} takes it. */
    private static final float FULL_LIGHT = 15 << 4 | 15 << 20;

    public static void init() {
        Skyboxes.register(new Skyboxes.Skybox() {
            @Override
            public boolean replacesCelestial(ClientLevel level) {
                return active();
            }

            @Override
            public void celestial(Sky sky) {
                sky.quads(Sky.Blend.ALPHA, 1.0F, 0.0F, 1.0F, 1.0F,
                        -20, 100, -20, 20, 100, -20, 20, 100, 20, -20, 100, 20);
            }

            @Override
            public void afterSky(Sky sky) {
                if (!active()) return;
                sky.quads(Sky.Blend.ALPHA, 0.0F, 1.0F, 1.0F, 1.0F,
                        30, 100, -10, 50, 100, -10, 50, 100, 10, 30, 100, 10);
            }
        });
        Precipitation.register(Level.OVERWORLD, frame -> {
            if (!active()) return;
            float[] v = new float[40];
            float[][] corners = {{-1, 0, -3, 0, 1}, {1, 0, -3, 1, 1}, {1, 2, -3, 1, 0}, {-1, 2, -3, 0, 0}};
            for (int i = 0; i < 4; i++) {
                float[] c = corners[i];
                System.arraycopy(new float[] {c[0], c[1], c[2], c[3], c[4], 1.0F, 0.0F, 0.0F, 1.0F, FULL_LIGHT}, 0, v, i * 10, 10);
            }
            frame.quads("minecraft:textures/environment/rain.png", v);
        });
    }

    private static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getMainHandItem().is(Items.DEBUG_STICK);
    }
}
