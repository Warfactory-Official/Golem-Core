package dev.golem.core.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

//? if >=1.21 {
import net.minecraft.server.network.CommonListenerCookie;
//?}

//? if <26.1 {
/*import net.minecraft.gametest.framework.GameTestAssertException;
*///?}

/**
 * {@code GameTestHelper} calls whose shape changed between the two test frameworks.
 *
 * <p>Most of the helper is identical across the eras, which is why the test bodies port unchanged; this
 * covers the handful that are not.
 */
public final class GameTestHelpers {

    private GameTestHelpers() {}

    /**
     * The block entity at a relative position, asserted to be of the given type.
     *
     * <p>26.1 takes the expected class and asserts itself. Older versions return a bare {@code BlockEntity}, so the
     * check is done here and fails the test instead of throwing a {@code ClassCastException}, which the framework
     * would report as an error.
     */
    public static <T extends BlockEntity> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        //? if >=26.1 {
        return helper.getBlockEntity(pos, type);
        //?} else {
        /*BlockEntity be = helper.getBlockEntity(pos);
        if (!type.isInstance(be)) {
            throw new GameTestAssertException("Expected " + type.getSimpleName() + " at " + pos
                    + " but found " + (be == null ? "nothing" : be.getClass().getSimpleName()));
        }
        return type.cast(be);
        *///?}
    }

    /** Creates the test player despite the GameTest helper's era-specific overload. */
    public static Player mockPlayer(GameTestHelper helper, GameType gameType) {
        //? if >=1.21 {
        return helper.makeMockPlayer(gameType);
        //?} else {
        /*// This era's helper has no game-type overload and always answers creative; the requested mode
        // is what 1.21's makeMockPlayer(GameType) reports, so it is reproduced here.
        boolean creative = gameType == GameType.CREATIVE;
        boolean spectator = gameType == GameType.SPECTATOR;
        // A named class, not an anonymous one: javac derives an anonymous constructor's parameters from
        // the superclass's, and some generated Forge 1.20.1 jars name them all alike ("variable o is
        // already defined").
        final class MockPlayer extends Player {
            MockPlayer() {
                super(helper.getLevel(), BlockPos.ZERO, 0.0F,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"));
            }

            @Override public boolean isSpectator() { return spectator; }
            @Override public boolean isCreative() { return creative; }
            @Override public boolean isLocalPlayer() { return true; }
            //? if fabric {
            // Vanilla's Player.drop only builds the item entity; ServerPlayer is what puts it in the
            // level (Forge's toss hook does it for every player, so only Fabric needs this).
            @Override public net.minecraft.world.entity.item.ItemEntity drop(net.minecraft.world.item.ItemStack stack, boolean dropAround,
                                                                            boolean includeThrower) {
                net.minecraft.world.entity.item.ItemEntity dropped = super.drop(stack, dropAround, includeThrower);
                if (dropped != null && !level().isClientSide()) level().addFreshEntity(dropped);
                return dropped;
            }
            //?}
        }
        Player player = new MockPlayer();
        player.getAbilities().instabuild = creative;
        player.getAbilities().invulnerable = creative;
        return player;
        *///?}
    }

    /** Copies the complete persisted block-entity payload without exposing era-specific save/load methods. */
    public static void copyPersistentData(BlockEntity source, BlockEntity target) {
        if (!(source instanceof dev.golem.core.data.CompatBlockEntity from)
                || !(target instanceof dev.golem.core.data.CompatBlockEntity to)) {
            throw new IllegalArgumentException("Persistence copy requires CompatBlockEntity instances");
        }
        from.copyPersistentDataTo(to);
    }

    /** Creates an owned server-player fixture with a real listener and an in-memory outbound connection. */
    /**
     * How many of these packets are Golem play payloads on the named channel.
     *
     * <p>Where the vanilla payload registry exists the packet names the payload's own id, so this counts exactly
     * that payload. Forge 1.20.1 has no such registry and every Golem payload rides one {@code SimpleChannel},
     * so there the count is of Golem payloads in general: a test that must tell two apart provokes one at a time.
     */
    public static int golemPayloads(List<Packet<?>> packets, String channelId) {
        int count = 0;
        for (Packet<?> packet : packets) {
            if (isGolemPayload(packet, channelId)) count++;
        }
        return count;
    }

    /**
     * How many payloads on the named channel reached this player, whichever transport carried them.
     *
     * <p>A test that asserts behaviour must not be able to tell the backends apart, so this counts an
     * encoded packet and a finished wire buffer as the same thing.
     */
    public static int golemPayloads(Outbound outbound, String channelId) {
        int count = golemPayloads(outbound.packets(), channelId);
        for (byte[] wire : outbound.wires()) {
            if (readChannel(wire) != null && readChannel(wire).equals(channelId)) count++;
        }
        return count;
    }

    /**
     * The channel a finished wire buffer names, or null if it is not a custom payload at all. Reads
     * the same prefix the client's decoder reads: the packet id, then the channel.
     */
    public static String readChannel(byte[] wire) {
        io.netty.buffer.ByteBuf buffer = Unpooled.wrappedBuffer(wire);
        try {
            if (readVarInt(buffer) != dev.golem.core.net.GolemWire.packetId()) return null;
            //? if >=26.1 {
            return new FriendlyByteBuf(buffer).readIdentifier().toString();
            //?} elif forge {
            /*FriendlyByteBuf reader = new FriendlyByteBuf(buffer);
            net.minecraft.resources.ResourceLocation channel = reader.readResourceLocation();
            // Forge carries every payload on one SimpleChannel; the index byte after it names the payload
            if (!channel.equals(dev.golem.core.net.GolemNetwork.forgeChannel())) return channel.toString();
            return dev.golem.core.net.GolemNetwork.forgePayloadId(reader.readByte() & 0xff);
            *///?} else {
            /*return new FriendlyByteBuf(buffer).readResourceLocation().toString();
            *///?}
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /**
     * The body of a finished wire buffer, decoded by the same codec that wrote it. The client does the same,
     * so a frame that decodes here is one the client can read.
     */
    public static <P extends dev.golem.core.net.GolemPayload> P decodeWire(byte[] wire,
            dev.golem.core.net.GolemCodec<P> codec) {
        io.netty.buffer.ByteBuf buffer = Unpooled.wrappedBuffer(wire);
        readVarInt(buffer);
        FriendlyByteBuf reader = new FriendlyByteBuf(buffer);
        //? if >=26.1 {
        reader.readIdentifier();
        //?} else {
        /*reader.readResourceLocation();
        *///?}
        //? if forge && <1.20.5 {
        /*reader.readByte();
        *///?}
        return codec.decode(reader);
    }

    private static int readVarInt(io.netty.buffer.ByteBuf buffer) {
        int value = 0;
        int shift = 0;
        byte piece;
        do {
            piece = buffer.readByte();
            value |= (piece & 0x7F) << shift;
            shift += 7;
        } while ((piece & 0x80) != 0);
        return value;
    }

    private static boolean isGolemPayload(Packet<?> packet, String channelId) {
        //? if >=1.20.5 {
        return packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                && custom.payload().type().id().toString().equals(channelId);
        //?} elif fabric {
        /*return packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket custom
                && custom.getIdentifier().toString().equals(channelId);
        *///?} else {
        /*return packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket custom
                && custom.getIdentifier().getNamespace().equals(dev.golem.core.GolemCore.MOD_ID);
        *///?}
    }

    /** What a mock player's channel received: encoded packets, and finished wire buffers. */
    public record Outbound(List<Packet<?>> packets, List<byte[]> wires) {}

    /**
     * Whether a packed resource exists, spelled the 26.1 way. Below 1.21 the datapack folders have plural names
     * ({@code recipes}, {@code loot_tables}, {@code advancements}, {@code structures}, {@code tags/items}), which
     * {@code legacy-recipes.gradle.kts} renames at build time; this maps the path the same way so a test states
     * one path for every node.
     */
    public static boolean resourceExists(Class<?> owner, String path) {
        String resolved = path;
        //? if <1.21 {
        /*resolved = resolved
                .replaceFirst("^(data/\\w+/)recipe/", "$1recipes/")
                .replaceFirst("^(data/\\w+/)loot_table/", "$1loot_tables/")
                .replaceFirst("^(data/\\w+/)advancement/", "$1advancements/")
                .replaceFirst("^(data/\\w+/)structure/", "$1structures/")
                .replaceFirst("^(data/\\w+/tags/)block/", "$1blocks/")
                .replaceFirst("^(data/\\w+/tags/)item/", "$1items/")
                .replaceFirst("^(data/\\w+/tags/)fluid/", "$1fluids/")
                .replaceFirst("^(data/\\w+/tags/)entity_type/", "$1entity_types/")
                .replaceFirst("^(data/\\w+/tags/)game_event/", "$1game_events/");
        *///?}
        // The owner's loader: on Forge each mod is its own module, and this class cannot see the mod's data.
        return owner.getClassLoader().getResource(resolved) != null;
    }

    public static MockServerPlayer mockServerPlayer(GameTestHelper helper) {
        return new MockServerPlayer(helper);
    }

    /**
     * A server player placed in the level, in survival mode. Vanilla's mock answers creative, which skips block
     * drops, tool wear, hunger and damage, so assertions on those pass without testing anything.
     */
    public static ServerPlayer mockServerPlayerInLevel(GameTestHelper helper) {
        return survivalServerPlayerInLevel(helper);
    }

    /** A survival-mode server player placed in the level. Works on Forge 1.20.1, whose login hooks need a channel. */
    public static ServerPlayer survivalServerPlayerInLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        //? if >=1.21 {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"), false);
        final class SurvivalPlayer extends ServerPlayer {
            SurvivalPlayer() {
                super(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            }

            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        }
        ServerPlayer player = new SurvivalPlayer();
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        //?} else {
        /*final class SurvivalPlayer extends ServerPlayer {
            SurvivalPlayer() {
                super(level.getServer(), level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"));
            }

            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        }
        ServerPlayer player = new SurvivalPlayer();
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        *///?}
        player.setGameMode(GameType.SURVIVAL);
        long[] seen = {level.getGameTime()};
        synchronized (SURVIVAL_PLAYERS) {
            SURVIVAL_PLAYERS.put(player, seen);
        }
        helper.onEachTick(() -> {
            seen[0] = level.getGameTime();
            removeStaleSurvivalPlayers(level);
        });
        return player;
    }

    /** A mock that has not been seen ticking this long belongs to a test that is over. */
    private static final int STALE_TICKS = 40;
    private static final Map<ServerPlayer, long[]> SURVIVAL_PLAYERS = new IdentityHashMap<>();

    /** Removes the survival mocks of finished tests, so they stop counting as mob targets. */
    private static void removeStaleSurvivalPlayers(ServerLevel level) {
        List<ServerPlayer> stale = new ArrayList<>();
        synchronized (SURVIVAL_PLAYERS) {
            SURVIVAL_PLAYERS.entrySet().removeIf(entry -> {
                boolean old = level.getGameTime() - entry.getValue()[0] > STALE_TICKS;
                if (old) stale.add(entry.getKey());
                return old;
            });
        }
        for (ServerPlayer player : stale) {
            level.getServer().getPlayerList().remove(player);
        }
    }

    /** The common fields carried by a loader's menu-opening packet. */
    public record MenuOpening(MenuType<?> menuType, int containerId, BlockPos position) {}

    /** Finds and decodes exactly one loader-specific menu-opening packet. */
    public static MenuOpening menuOpening(GameTestHelper helper, List<Packet<?>> packets) {
        List<MenuOpening> openings = new ArrayList<>();
        for (Packet<?> packet : packets) {
            MenuOpening opening = decodeMenuOpening(packet);
            if (opening != null) openings.add(opening);
        }
        helper.assertTrue(openings.size() == 1,
                "expected exactly one recognized menu-opening packet, found " + openings.size());
        return openings.get(0);
    }

    private static MenuOpening decodeMenuOpening(Packet<?> packet) {
        //? if neoforge {
        if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload opening) {
            BlockPos position = decodeBlockPos(opening.additionalData());
            return position == null ? null : new MenuOpening(opening.menuType(), opening.windowId(), position);
        }
        //?} elif fabric && >=26.1 {
        /*if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof net.fabricmc.fabric.impl.menu.Networking.OpenScreenPayload<?> opening) {
            if (!(opening.data() instanceof BlockPos position)) return null;
            MenuType<?> menuType = net.minecraft.core.registries.BuiltInRegistries.MENU.getValue(opening.identifier());
            return menuType == null ? null : new MenuOpening(menuType, opening.containerId(), position);
        }
        *///?} elif fabric && >=1.20.5 {
        /*if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof net.fabricmc.fabric.impl.screenhandler.Networking.OpenScreenPayload<?> opening) {
            if (!(opening.data() instanceof BlockPos position)) return null;
            MenuType<?> menuType = net.minecraft.core.registries.BuiltInRegistries.MENU.get(opening.identifier());
            return menuType == null ? null : new MenuOpening(menuType, opening.syncId(), position);
        }
        *///?} elif fabric {
        /*if (packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket custom
                && custom.getIdentifier().equals(net.fabricmc.fabric.impl.screenhandler.Networking.OPEN_ID)) {
            FriendlyByteBuf data = custom.getData();
            int readerIndex = data.readerIndex();
            try {
                MenuType<?> menuType = net.minecraft.core.registries.BuiltInRegistries.MENU.get(data.readResourceLocation());
                int containerId = data.readVarInt();
                data.readComponent();
                if (menuType == null || data.readableBytes() != 8) return null;
                return new MenuOpening(menuType, containerId, data.readBlockPos());
            } catch (RuntimeException ignored) {
                return null;
            } finally {
                data.readerIndex(readerIndex);
            }
        }
        *///?} elif forge {
        /*if (packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket custom
                && custom.getIdentifier().equals(new net.minecraft.resources.ResourceLocation("fml", "play"))) {
            FriendlyByteBuf data = custom.getData();
            int readerIndex = data.readerIndex();
            try {
                if (data.readUnsignedByte() != 1) return null;
                MenuType<?> menuType = net.minecraft.core.registries.BuiltInRegistries.MENU.byId(data.readVarInt());
                int containerId = data.readVarInt();
                data.readComponent();
                BlockPos position = decodeBlockPos(data.readByteArray(32600));
                return menuType == null || position == null || data.isReadable()
                        ? null : new MenuOpening(menuType, containerId, position);
            } catch (RuntimeException ignored) {
                return null;
            } finally {
                data.readerIndex(readerIndex);
            }
        }
        *///?}
        return null;
    }

    private static BlockPos decodeBlockPos(byte[] bytes) {
        if (bytes == null || bytes.length != 8) return null;
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            return data.readBlockPos();
        } finally {
            data.release();
        }
    }

    /**
     * A server-side mock only: it has no remote client, but its connection retains packets sent to that
     * client so tests can inspect the server's wire contract.
     */
    public static final class MockServerPlayer implements AutoCloseable {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final Connection connection;
        private final EmbeddedChannel channel;
        private boolean closed;

        private MockServerPlayer(GameTestHelper helper) {
            level = helper.getLevel();
            player = mockServerPlayerInLevel(helper);
            connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            //? if neoforge {
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            //?}
            // No send override: Connection writes the actual outbound packet to
            // EmbeddedChannel, including NeoForge's advanced_open_screen payload.
            //? if >=1.21 {
            player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
            //?} else {
            /*player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
            *///?}
        }

        public ServerPlayer player() {
            return player;
        }

        /** Drains packets sent since the previous call, dropping raw wire buffers. */
        public List<Packet<?>> outboundPackets() {
            return drainOutbound().packets();
        }

        /**
         * Drains everything sent since the previous call.
         *
         * <p>The safe transport writes an encoded {@code Packet}, the accelerated one the finished bytes; both are
         * collected. Raw buffers are copied out and released so a test never holds a Netty reference.
         */
        public Outbound drainOutbound() {
            // A live connection's event loop runs continuously; an EmbeddedChannel's runs only when
            // asked. The accelerated transport batches its writes onto that loop, so without this the
            // queued bytes are real but never leave.
            channel.runPendingTasks();
            List<Packet<?>> packets = new ArrayList<>();
            List<byte[]> wires = new ArrayList<>();
            Object outbound;
            while ((outbound = channel.readOutbound()) != null) {
                if (outbound instanceof Packet<?> packet) {
                    packets.add(packet);
                } else if (outbound instanceof io.netty.buffer.ByteBuf wire) {
                    try {
                        byte[] bytes = new byte[wire.readableBytes()];
                        wire.getBytes(wire.readerIndex(), bytes);
                        wires.add(bytes);
                    } finally {
                        ReferenceCountUtil.release(wire);
                    }
                } else {
                    ReferenceCountUtil.release(outbound);
                }
            }
            return new Outbound(packets, wires);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                player.closeContainer();
            } finally {
                try {
                    level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                } finally {
                    try {
                        connection.disconnect(Component.literal("GameTest mock player closed"));
                    } finally {
                        channel.finishAndReleaseAll();
                    }
                }
            }
        }
    }
}
