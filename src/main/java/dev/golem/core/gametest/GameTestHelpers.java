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
import java.util.List;

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
     * <p>26.1 takes the expected class and does the assertion itself. Older versions return a bare
     * {@code BlockEntity}, so the check is done here — and it must fail the test rather than throw a
     * {@code ClassCastException}, which the framework would report as an error instead of a failure.
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
        /*return helper.makeMockPlayer();
        *///?}
    }

    /** Creates an owned server-player fixture with a real listener and an in-memory outbound connection. */
    public static MockServerPlayer mockServerPlayer(GameTestHelper helper) {
        return new MockServerPlayer(helper);
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
            player = helper.makeMockServerPlayerInLevel();
            connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            //? if neoforge {
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            //?}
            // There is deliberately no send override: Connection writes the actual outbound packet to
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

        /** Drains packets sent since the previous call, releasing any non-packet Netty message. */
        public List<Packet<?>> outboundPackets() {
            List<Packet<?>> packets = new ArrayList<>();
            Object outbound;
            while ((outbound = channel.readOutbound()) != null) {
                if (outbound instanceof Packet<?> packet) packets.add(packet);
                else ReferenceCountUtil.release(outbound);
            }
            return packets;
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
