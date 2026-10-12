package dev.golem.core.client;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.jspecify.annotations.Nullable;

//? if >=26.1 {
import java.util.function.Supplier;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
//?} elif >=1.21 {
/*import java.util.function.Supplier;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
*///?} else {
/*import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Mod-supplied player capes: legacy {@code AbstractClientPlayer.func_152121_a(Type.CAPE, texture)} (1.7) and
 * {@code NetworkPlayerInfo.playerTextures.put(Type.CAPE, texture)} (1.12). A provider names a cape texture for a
 * player; every client tick the texture is installed on that player's tab-list entry, which is where each version's
 * own cape layer reads it, so the vanilla cape model, physics, elytra fallback and the player's "Cape" skin option
 * all apply unchanged.
 *
 * <p>Where the entry keeps its skin differs per version, and that is the whole of this class:
 * <ul>
 * <li>26.1 and 1.21.1 read a {@code PlayerSkin} record through the entry's {@code skinLookup} supplier. The supplier
 *     is wrapped so the record it returns carries the cape; the wrapper is replaced, never nested, when the cape
 *     changes, and removed when the provider stops naming one. The field is widened (and made non-final on 1.21.1)
 *     by the access widener / transformer.</li>
 * <li>1.20.1 reads {@code textureLocations.get(CAPE)}. The cape is put into that map. The skin manager's own
 *     callback may later overwrite it for a player with a Mojang cape; the next tick puts it back, which is what
 *     1.12's per-frame put did.</li>
 * </ul>
 *
 * <p>Elytra wings. Every version draws a worn elytra with, in order: the player's own elytra skin texture (Mojang
 * profile {@code ELYTRA}); else the cape texture if the player has a cape and shows it; else the default elytra texture
 * (26.1 {@code WingsLayer.getPlayerElytraTexture} + the {@code elytra} equipment asset; 1.21.1 {@code ElytraLayer} with
 * {@code PlayerSkin.elytraTexture()/capeTexture()}; 1.20.1 {@code ElytraLayer} with
 * {@code getElytraTextureLocation()/getCloakTextureLocation()}). The wings sample the cape image's elytra region (the
 * {@code ElytraModel} box at {@code texOffs(22, 0)}, 10x20x2, on a 64x32 sheet: x 22..46, y 0..22; scaled for larger
 * capes). Capes made before the elytra, such as 1.7's, leave that region transparent, so a caped player's wings would be
 * invisible. Unless a cape is declared with {@link #declareElytraArt}, the installed entry therefore also names the default
 * elytra texture as the player's elytra skin (only where the profile has none), which is exactly what vanilla draws for
 * a player without a cape. The cape itself is unchanged.
 *
 * <p>No mixin: the only obstacle on every version is the visibility of one field (AGENTS.md section 7, rung 3).
 * Client-only.
 */
public final class Capes {

    private Capes() {}

    /** Names the cape a player wears, or {@code null} for none. Called once per listed player per client tick. */
    @FunctionalInterface
    public interface Provider {
        //? if >=26.1 {
        @Nullable Identifier cape(UUID id, String name);
        //?} else {
        /*@Nullable ResourceLocation cape(UUID id, String name);
        *///?}
    }

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final dev.golem.core.hook.Hook<Provider> PROVIDERS = dev.golem.core.hook.Hook.notifying();
    private static boolean installed;
    /** Cape textures that paint vanilla's elytra region; their wearers' wings use the cape, as a Mojang cape's do. */
    private static final java.util.Set<Object> ELYTRA_ART = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Declares that a cape texture carries elytra art in the vanilla cape layout (x 22..46, y 0..22 of a 64x32 sheet), so
     * a worn elytra is drawn with the cape as vanilla does for a Mojang cape. Undeclared capes get the default elytra.
     */
    //? if >=26.1 {
    public static void declareElytraArt(Identifier cape) {
        ELYTRA_ART.add(cape);
    }
    //?} else {
    /*public static void declareElytraArt(ResourceLocation cape) {
        ELYTRA_ART.add(cape);
    }
    *///?}

    /** Declares a cape provider. Call during client initialization. */
    public static synchronized void register(Provider provider) {
        PROVIDERS.register(provider);
        if (!installed) {
            installed = true;
            ClientTicks.onEndTick(Capes::apply);
        }
    }

    /** The first cape any provider names for this player. */
    //? if >=26.1 {
    public static @Nullable Identifier capeFor(UUID id, String name) {
        for (Provider provider : PROVIDERS) {
            Identifier cape = provider.cape(id, name);
            if (cape != null) return cape;
        }
        return null;
    }
    //?} else {
    /*public static @Nullable ResourceLocation capeFor(UUID id, String name) {
        for (Provider provider : PROVIDERS) {
            ResourceLocation cape = provider.cape(id, name);
            if (cape != null) return cape;
        }
        return null;
    }
    *///?}

    private static void apply() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            //? if >=26.1 {
            install(info, capeFor(info.getProfile().id(), info.getProfile().name()));
            //?} else {
            /*install(info, capeFor(info.getProfile().getId(), info.getProfile().getName()));
            *///?}
        }
    }

    //? if >=26.1 {
    private static void install(PlayerInfo info, @Nullable Identifier cape) {
        Supplier<PlayerSkin> current = info.skinLookup;
        if (cape == null) {
            if (current instanceof Caped caped) info.skinLookup = caped.base;
            return;
        }
        if (current == null) {
            info.getSkin(); // 26.1 builds the lookup lazily, on the first getSkin
            current = info.skinLookup;
            if (current == null) return;
        }
        if (current instanceof Caped caped && caped.cape.equals(cape)) return;
        info.skinLookup = new Caped(current instanceof Caped caped ? caped.base : current, cape);
    }

    /** The {@code minecraft:elytra} equipment asset's wings texture, what {@code WingsLayer} draws without a cape. */
    private static final ClientAsset.Texture DEFAULT_ELYTRA = new ClientAsset.ResourceTexture(
            Identifier.withDefaultNamespace("elytra"),
            Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png"));

    /** The entry's own lookup, with the cape replaced. The last record is kept so a frame does not allocate. */
    private static final class Caped implements Supplier<PlayerSkin> {
        final Supplier<PlayerSkin> base;
        final Identifier cape;
        private final ClientAsset.Texture asset;
        private final boolean elytraArt;
        private @Nullable PlayerSkin lastBase;
        private @Nullable PlayerSkin lastCaped;

        Caped(Supplier<PlayerSkin> base, Identifier cape) {
            this.base = base;
            this.cape = cape;
            this.asset = new ClientAsset.ResourceTexture(cape, cape);
            this.elytraArt = ELYTRA_ART.contains(cape);
        }

        @Override
        public PlayerSkin get() {
            PlayerSkin skin = base.get();
            if (skin != lastBase || lastCaped == null) {
                lastBase = skin;
                ClientAsset.Texture elytra = skin.elytra() != null || elytraArt ? skin.elytra() : DEFAULT_ELYTRA;
                lastCaped = new PlayerSkin(skin.body(), asset, elytra, skin.model(), skin.secure());
            }
            return lastCaped;
        }
    }
    //?} elif >=1.21 {
    /*private static void install(PlayerInfo info, @Nullable ResourceLocation cape) {
        Supplier<PlayerSkin> current = info.skinLookup;
        if (cape == null) {
            if (current instanceof Caped caped) info.skinLookup = caped.base;
            return;
        }
        if (current instanceof Caped caped && caped.cape.equals(cape)) return;
        info.skinLookup = new Caped(current instanceof Caped caped ? caped.base : current, cape);
    }

    /^* {@code ElytraLayer}'s {@code WINGS_LOCATION}, what it draws without a cape. ^/
    private static final ResourceLocation DEFAULT_ELYTRA = ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");

    /^* The entry's own lookup, with the cape replaced. The last record is kept so a frame does not allocate. ^/
    private static final class Caped implements Supplier<PlayerSkin> {
        final Supplier<PlayerSkin> base;
        final ResourceLocation cape;
        private final boolean elytraArt;
        private @Nullable PlayerSkin lastBase;
        private @Nullable PlayerSkin lastCaped;

        Caped(Supplier<PlayerSkin> base, ResourceLocation cape) {
            this.base = base;
            this.cape = cape;
            this.elytraArt = ELYTRA_ART.contains(cape);
        }

        @Override
        public PlayerSkin get() {
            PlayerSkin skin = base.get();
            if (skin != lastBase || lastCaped == null) {
                lastBase = skin;
                ResourceLocation elytra = skin.elytraTexture() != null || elytraArt ? skin.elytraTexture() : DEFAULT_ELYTRA;
                lastCaped = new PlayerSkin(skin.texture(), skin.textureUrl(), cape, elytra,
                        skin.model(), skin.secure());
            }
            return lastCaped;
        }
    }
    *///?} else {
    /*private static final Map<PlayerInfo, ResourceLocation> INSTALLED = new WeakHashMap<>();
    /^* {@code ElytraLayer}'s {@code WINGS_LOCATION}, what it draws without a cape. ^/
    private static final ResourceLocation DEFAULT_ELYTRA = new ResourceLocation("textures/entity/elytra.png");

    private static void install(PlayerInfo info, @Nullable ResourceLocation cape) {
        Map<MinecraftProfileTexture.Type, ResourceLocation> textures = info.textureLocations;
        if (cape == null) {
            ResourceLocation ours = INSTALLED.remove(info);
            if (ours != null && ours.equals(textures.get(MinecraftProfileTexture.Type.CAPE)))
                textures.remove(MinecraftProfileTexture.Type.CAPE);
            if (ours != null && DEFAULT_ELYTRA.equals(textures.get(MinecraftProfileTexture.Type.ELYTRA)))
                textures.remove(MinecraftProfileTexture.Type.ELYTRA);
            return;
        }
        INSTALLED.put(info, cape);
        if (!cape.equals(textures.get(MinecraftProfileTexture.Type.CAPE)))
            textures.put(MinecraftProfileTexture.Type.CAPE, cape);
        // the profile's own elytra skin wins, as in vanilla; ours is only the default a cape without elytra art needs
        ResourceLocation elytra = textures.get(MinecraftProfileTexture.Type.ELYTRA);
        if (ELYTRA_ART.contains(cape)) {
            if (DEFAULT_ELYTRA.equals(elytra)) textures.remove(MinecraftProfileTexture.Type.ELYTRA);
        } else if (elytra == null) {
            textures.put(MinecraftProfileTexture.Type.ELYTRA, DEFAULT_ELYTRA);
        }
    }
    *///?}
}
