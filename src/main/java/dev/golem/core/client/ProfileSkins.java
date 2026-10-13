package dev.golem.core.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Any player's skin by UUID, with its pixels - legacy {@code SessionService.fillProfileProperties} plus a read of the
 * skin manager's cache file and {@code ImageBufferDownload.parseUserSkin} (1.12.2's {@code MojangSkinLoader}).
 *
 * <p>The vanilla skin manager hands out only a texture id, and the pixels behind it are unreachable on 1.21.1/1.20.1,
 * so this fetches the profile from Mojang's session server itself (the request authlib makes), downloads the skin from
 * the texture host the profile names (only {@code textures.minecraft.net}, authlib's own whitelist), converts a legacy
 * 64x32 skin and makes the base layer opaque exactly as vanilla's {@code processLegacySkin} does, and registers the
 * result as {@code golemcore:profile_skins/<uuid>}. One attempt per UUID per session; a failure is logged once and
 * {@link #get} keeps answering null. Plain Java networking and pixel arithmetic: the only version-specific work is in
 * {@link DynamicTextures}. Client-only.
 */
public final class ProfileSkins {

    private ProfileSkins() {}

    private static final Logger LOG = LoggerFactory.getLogger("Golem Core");
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Map<UUID, Object> STATE = new ConcurrentHashMap<>();
    private static final Object PENDING = new Object();
    private static final Object FAILED = new Object();
    private static volatile @Nullable HttpClient client;

    /** A loaded skin: 64x64 {@code 0xAARRGGBB} pixels (already processed) and the registered texture. */
    public record Skin(
            int[] argb,
            //? if >=26.1 {
            Identifier texture
            //?} else {
            /*ResourceLocation texture
            *///?}
    ) {}

    /** Starts fetching {@code id}'s skin in the background, if it was not asked for before. */
    public static void preload(UUID id) {
        if (STATE.putIfAbsent(id, PENDING) != null) return;
        // a thread of its own, as 1.12.2's "BobbleSkinFetch-<uuid>": the shared pool can be held by loader work
        Thread thread = new Thread(() -> {
            int[] pixels;
            try {
                pixels = fetch(id);
            } catch (Throwable error) {
                STATE.put(id, FAILED);
                LOG.warn("Could not load the skin of {}: {}", id, error.toString());
                return;
            }
            if (pixels == null) {
                STATE.put(id, FAILED);
                LOG.warn("Could not load the skin of {}: the profile has no skin", id);
                return;
            }
            // the texture is registered by the first get() on the render thread: a fetch begun while the mod is
            // constructed (NeoForge) can finish before Minecraft itself exists
            STATE.put(id, new Downloaded(pixels));
        }, "ProfileSkinFetch-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    private record Downloaded(int[] pixels) {}

    /** The skin, once loaded; null while loading, after a failure, or if never asked for. Render thread only. */
    public static @Nullable Skin get(UUID id) {
        Object state = STATE.get(id);
        if (state instanceof Downloaded downloaded) {
            var texture = dev.golem.core.Ids.of(dev.golem.core.GolemCore.MOD_ID, "profile_skins/" + id);
            DynamicTextures.register(texture, 64, 64, downloaded.pixels());
            Skin skin = new Skin(downloaded.pixels(), texture);
            STATE.put(id, skin);
            LOG.info("Loaded the skin of {}", id);
            return skin;
        }
        return state instanceof Skin skin ? skin : null;
    }

    private static int @Nullable [] fetch(UUID id) {
        try {
            String profile = request(PROFILE_URL + id.toString().replace("-", ""));
            String url = skinUrl(JsonParser.parseString(profile).getAsJsonObject());
            if (url == null) return null;
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !(host.equals("textures.minecraft.net") || host.endsWith(".minecraft.net"))) {
                throw new IllegalStateException("skin host not allowed: " + host);
            }
            if ("http".equals(uri.getScheme())) uri = URI.create("https" + url.substring(4));
            byte[] png = http().send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).body();
            int[] size = new int[2];
            int[] pixels = DynamicTextures.decodePng(png, size);
            return processLegacySkin(pixels, size[0], size[1]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String request(String url) throws Exception {
        HttpResponse<String> response = http().send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        return response.body();
    }

    private static @Nullable String skinUrl(JsonObject profile) {
        if (!profile.has("properties")) return null;
        for (JsonElement element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) continue;
            String json = new String(Base64.getDecoder().decode(property.get("value").getAsString()), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) return null;
            return textures.getAsJsonObject("SKIN").get("url").getAsString();
        }
        return null;
    }

    private static HttpClient http() {
        HttpClient http = client;
        if (http == null) {
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            client = http;
        }
        return http;
    }

    /**
     * Vanilla {@code processLegacySkin} on ARGB pixels: a 64x32 skin gains the 64x64 limb copies, the base layer is
     * made opaque, and a legacy hat layer that is entirely opaque is cleared ({@code doNotchTransparencyHack}).
     */
    static int[] processLegacySkin(int[] pixels, int width, int height) {
        if (width != 64 || (height != 32 && height != 64)) {
            throw new IllegalStateException("incorrectly sized skin " + width + "x" + height);
        }
        boolean legacy = height == 32;
        int[] image = new int[64 * 64];
        System.arraycopy(pixels, 0, image, 0, 64 * height);
        if (legacy) {
            copyRect(image, 4, 16, 16, 32, 4, 4);
            copyRect(image, 8, 16, 16, 32, 4, 4);
            copyRect(image, 0, 20, 24, 32, 4, 12);
            copyRect(image, 4, 20, 16, 32, 4, 12);
            copyRect(image, 8, 20, 8, 32, 4, 12);
            copyRect(image, 12, 20, 16, 32, 4, 12);
            copyRect(image, 44, 16, -8, 32, 4, 4);
            copyRect(image, 48, 16, -8, 32, 4, 4);
            copyRect(image, 40, 20, 0, 32, 4, 12);
            copyRect(image, 44, 20, -8, 32, 4, 12);
            copyRect(image, 48, 20, -16, 32, 4, 12);
            copyRect(image, 52, 20, -8, 32, 4, 12);
        }
        setNoAlpha(image, 0, 0, 32, 16);
        if (legacy) doNotchTransparencyHack(image, 32, 0, 64, 32);
        setNoAlpha(image, 0, 16, 64, 32);
        setNoAlpha(image, 16, 48, 48, 64);
        return image;
    }

    /** {@code NativeImage.copyRect(x, y, dx, dy, w, h, true, false)}: mirrored left to right. */
    private static void copyRect(int[] image, int x, int y, int dx, int dy, int w, int h) {
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                image[(y + dy + row) * 64 + (x + dx + col)] = image[(y + row) * 64 + (x + w - 1 - col)];
            }
        }
    }

    private static void doNotchTransparencyHack(int[] image, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                if ((image[y * 64 + x] >>> 24) < 128) return;
        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                image[y * 64 + x] &= 0xFFFFFF;
    }

    private static void setNoAlpha(int[] image, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                image[y * 64 + x] |= 0xFF000000;
    }
}
