package dev.golem.core.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if >=26.1 {
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
//?} elif >=1.21 {
/*import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
*///?} else {
/*import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Any player's skin by UUID, with its pixels (1.12.2's {@code MojangSkinLoader}). Vanilla's session service and skin
 * manager fetch and process the skin; its 64x64 pixels are read back from vanilla's texture and registered again as
 * {@code golemcore:profile_skins/<uuid>}. One attempt per UUID per session; a failure is logged once and {@link #get}
 * keeps answering null. Client-only.
 */
public final class ProfileSkins {

    private ProfileSkins() {}

    private static final Logger LOG = LoggerFactory.getLogger("Golem Core");
    private static final int SIZE = 64;
    private static final Map<UUID, Object> STATE = new ConcurrentHashMap<>();
    private static final Queue<UUID> QUEUED = new ConcurrentLinkedQueue<>();
    private static final Object PENDING = new Object();
    private static final Object FAILED = new Object();
    private static boolean ticking;

    /** A loaded skin: 64x64 {@code 0xAARRGGBB} pixels and the registered texture. */
    public record Skin(
            int[] argb,
            //? if >=26.1 {
            Identifier texture
            //?} else {
            /*ResourceLocation texture
            *///?}
    ) {}

    /** Vanilla has finished this skin's texture. */
    private record Registered(
            //? if >=26.1 {
            Identifier vanilla
            //?} else {
            /*ResourceLocation vanilla
            *///?}
    ) {}

    /** Starts fetching {@code id}'s skin, if it was not asked for before. Safe before Minecraft exists. */
    public static void preload(UUID id) {
        if (STATE.putIfAbsent(id, PENDING) != null) return;
        QUEUED.add(id);
        if (!ticking) {
            ticking = true;
            ClientTicks.onEndTick(ProfileSkins::startQueued);
        }
    }

    private static void startQueued() {
        for (UUID id; (id = QUEUED.poll()) != null; ) {
            UUID player = id;
            // the profile lookup blocks, so a thread of its own, as 1.12.2's "BobbleSkinFetch-<uuid>"
            Thread thread = new Thread(() -> fetch(player), "ProfileSkinFetch-" + player);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private static void fetch(UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        GameProfile profile;
        try {
            profile = profile(minecraft, id);
        } catch (RuntimeException error) {
            fail(id, error.toString());
            return;
        }
        if (profile == null) {
            fail(id, "the profile lookup failed");
            return;
        }
        if (!hasSkin(minecraft, profile)) {
            fail(id, "the profile has no skin");
            return;
        }
        minecraft.execute(() -> requestSkin(minecraft, id, profile));
    }

    private static @Nullable GameProfile profile(Minecraft minecraft, UUID id) {
        //? if >=26.1 {
        ProfileResult result = minecraft.services().sessionService().fetchProfile(id, false);
        return result == null ? null : result.profile();
        //?} elif >=1.21 {
        /*ProfileResult result = minecraft.getMinecraftSessionService().fetchProfile(id, false);
        return result == null ? null : result.profile();
        *///?} else {
        /*return minecraft.getMinecraftSessionService().fillProfileProperties(new GameProfile(id, null), false);
        *///?}
    }

    private static boolean hasSkin(Minecraft minecraft, GameProfile profile) {
        //? if >=26.1 {
        return minecraft.services().sessionService().getPackedTextures(profile) != null;
        //?} elif >=1.21 {
        /*return minecraft.getMinecraftSessionService().getPackedTextures(profile) != null;
        *///?} else {
        /*return profile.getProperties().containsKey("textures");
        *///?}
    }

    private static void requestSkin(Minecraft minecraft, UUID id, GameProfile profile) {
        //? if >=26.1 {
        minecraft.getSkinManager().get(profile).thenAcceptAsync(skin -> {
            if (skin.isEmpty()) fail(id, "the skin did not load");
            else STATE.put(id, new Registered(skin.get().body().texturePath()));
        }, minecraft);
        //?} elif >=1.21 {
        /*minecraft.getSkinManager().getOrLoad(profile).thenAccept(skin -> afterUpload(minecraft, id, skin.texture()));
        *///?} else {
        /*minecraft.getSkinManager().registerSkins(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) afterUpload(minecraft, id, location);
        }, false);
        *///?}
    }

    //? if <26.1 {
    /*// Vanilla reports a skin downloaded and only then queues its upload on the same executor, so a readback queued from
    // the report would see the placeholder; queued from a task that runs after it, it sees the skin.
    private static void afterUpload(Minecraft minecraft, UUID id, ResourceLocation vanilla) {
        minecraft.execute(() -> minecraft.execute(() -> STATE.put(id, new Registered(vanilla))));
    }
    *///?}

    private static void fail(UUID id, String reason) {
        STATE.put(id, FAILED);
        LOG.warn("Could not load the skin of {}: {}", id, reason);
    }

    /** The skin, once loaded; null while loading, after a failure, or if never asked for. Render thread only. */
    public static @Nullable Skin get(UUID id) {
        Object state = STATE.get(id);
        if (state instanceof Registered registered) {
            int[] pixels = pixels(registered.vanilla());
            if (pixels == null) {
                fail(id, "vanilla's texture has no pixels");
                return null;
            }
            var texture = Ids.of(GolemCore.MOD_ID, "profile_skins/" + id);
            DynamicTextures.register(texture, SIZE, SIZE, pixels);
            Skin skin = new Skin(pixels, texture);
            STATE.put(id, skin);
            LOG.debug("Loaded the skin of {}", id);
            return skin;
        }
        return state instanceof Skin skin ? skin : null;
    }

    private static int @Nullable [] pixels(
            //? if >=26.1 {
            Identifier vanilla
            //?} else {
            /*ResourceLocation vanilla
            *///?}
    ) {
        int[] argb = new int[SIZE * SIZE];
        //? if >=26.1 {
        if (!(Minecraft.getInstance().getTextureManager().getTexture(vanilla) instanceof DynamicTexture texture)) return null;
        NativeImage image = texture.getPixels();
        if (image == null || image.getWidth() != SIZE || image.getHeight() != SIZE) return null;
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++)
                argb[y * SIZE + x] = image.getPixel(x, y);
        //?} else {
        /*RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(vanilla).getId());
        try (NativeImage image = new NativeImage(SIZE, SIZE, false)) {
            image.downloadTexture(0, false);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    argb[y * SIZE + x] = abgr & 0xFF00FF00 | (abgr & 0xFF) << 16 | abgr >>> 16 & 0xFF;
                }
            }
        }
        *///?}
        return argb;
    }
}
