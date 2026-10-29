package dev.golem.core.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Menus that carry server-defined opening data to the client.
 *
 * <p>Vanilla's menu type cannot send extra opening data, so every loader has its own: NeoForge has
 * {@code IMenuTypeExtension}; Fabric's {@code ExtendedScreenHandlerType} became {@code ExtendedMenuType}
 * in the 26.1 line, and its data moved from a raw buffer write to a {@code StreamCodec} in 1.20.5.
 *
 * <p>The position is what a machine menu needs: the container re-derives its state from
 * {@code level.getBlockState(pos)} on both sides, which is safer than trusting a client-sent value.
 */
public final class Menus {

    private Menus() {}

    /** Builds a menu from its network id, the player's inventory, and the position it was opened at. */
    @FunctionalInterface
    public interface PosFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory playerInventory, BlockPos pos);
    }

    /** Builds a menu from its network id, the player's inventory, and the hand that opened it. */
    @FunctionalInterface
    public interface HandFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory playerInventory, InteractionHand hand);
    }

    /** Creates a menu type whose menu is opened with a {@link BlockPos} as extra data. */
    /** A menu keyed by an entity id, for menus opened on a living vehicle or mob rather than a block. */
    @FunctionalInterface
    public interface EntityFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory playerInventory, int entityId);
    }

    /** Builds a menu with an integer captured by the server when it opens. */
    @FunctionalInterface
    public interface IntFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory playerInventory, int openingData);
    }

    public static <T extends AbstractContainerMenu> MenuType<T> typeWithPos(PosFactory<T> factory) {
        //? if fabric && >=26.1 {
        /*return new net.fabricmc.fabric.api.menu.v1.ExtendedMenuType<>(factory::create, BlockPos.STREAM_CODEC);
        *///?} elif fabric && >=1.20.5 {
        /*return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                factory::create, BlockPos.STREAM_CODEC);
        *///?} elif fabric {
        /*// Pre-1.20.5 fabric-api has no stream codec here; the position is read straight off the buffer.
        return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                (containerId, inventory, buffer) -> factory.create(containerId, inventory, buffer.readBlockPos()));
        *///?} elif neoforge {
        return net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                (containerId, inventory, buffer) -> factory.create(containerId, inventory, buffer.readBlockPos()));
        //?} elif forge {
        /*return net.minecraftforge.common.extensions.IForgeMenuType.create(
                (containerId, inventory, buffer) -> factory.create(containerId, inventory, buffer.readBlockPos()));
        *///?}
    }

    /** Creates a menu type whose menu is opened with the player's main/off hand as extra data. */
    public static <T extends AbstractContainerMenu> MenuType<T> typeWithHand(HandFactory<T> factory) {
        //? if fabric && >=26.1 {
        /*return new net.fabricmc.fabric.api.menu.v1.ExtendedMenuType<>(factory::create,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT.map(
                        value -> value == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                        hand -> hand == InteractionHand.MAIN_HAND ? 0 : 1));
        *///?} elif fabric && >=1.20.5 {
        /*return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(factory::create,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT.map(
                        value -> value == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                        hand -> hand == InteractionHand.MAIN_HAND ? 0 : 1));
        *///?} elif fabric {
        /*return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                (containerId, inventory, buffer) -> factory.create(containerId, inventory,
                        buffer.readVarInt() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND));
        *///?} elif neoforge {
        return net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                (containerId, inventory, buffer) -> factory.create(containerId, inventory,
                        buffer.readVarInt() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND));
        //?} elif forge {
        /*return net.minecraftforge.common.extensions.IForgeMenuType.create(
                (containerId, inventory, buffer) -> factory.create(containerId, inventory,
                        buffer.readVarInt() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND));
        *///?}
    }

    /** As {@link #typeWithHand} with an entity id instead of a hand. */
    public static <T extends AbstractContainerMenu> MenuType<T> typeWithEntity(EntityFactory<T> factory) {
        return typeWithInt(factory::create);
    }

    /**
     * Creates a menu with mandatory integer opening data. Variable layouts must use the server's opening data,
     * rather than infer their size from a client inventory that can change before the open packet arrives.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> typeWithInt(IntFactory<T> factory) {
        //? if fabric && >=26.1 {
        /*return new net.fabricmc.fabric.api.menu.v1.ExtendedMenuType<>(factory::create,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT);
        *///?} elif fabric && >=1.20.5 {
        /*return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(factory::create,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT);
        *///?} elif fabric {
        /*return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                (containerId, inventory, buffer) -> factory.create(containerId, inventory, buffer.readVarInt()));
        *///?} elif neoforge {
        return net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                (containerId, inventory, buffer) -> factory.create(containerId, inventory, buffer.readVarInt()));
        //?} elif forge {
        /*return net.minecraftforge.common.extensions.IForgeMenuType.create(
                (containerId, inventory, buffer) -> factory.create(containerId, inventory, buffer.readVarInt()));
        *///?}
    }

    /** Opens a provider for the player, sending {@code pos} to the client alongside it. */
    public static void openWithPos(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        //? if fabric && >=26.1 {
        /*player.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<BlockPos>() {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer p) {
                return pos;
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
        *///?} elif fabric && >=1.20.5 {
        /*player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<BlockPos>() {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer p) {
                return pos;
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
        *///?} elif fabric {
        /*player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer p, net.minecraft.network.FriendlyByteBuf buffer) {
                buffer.writeBlockPos(pos);
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
        *///?} elif neoforge {
        player.openMenu(provider, pos);
        //?} elif forge {
        /*net.minecraftforge.network.NetworkHooks.openScreen(player, provider, pos);
        *///?}
    }

    /** Opens a provider for the player, sending the hand that owns a stack-backed menu. */
    public static void openWithHand(ServerPlayer player, MenuProvider provider, InteractionHand hand) {
        int value = hand == InteractionHand.MAIN_HAND ? 0 : 1;
        //? if fabric && >=26.1 {
        /*player.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<InteractionHand>() {
            // The menu type's codec (typeWithHand) is a StreamCodec<InteractionHand>: handing it the Integer
            // threw a ClassCastException in the open-screen payload and no hand-opened menu ever opened on Fabric.
            @Override
            public InteractionHand getScreenOpeningData(ServerPlayer p) { return hand; }

            @Override
            public Component getDisplayName() { return provider.getDisplayName(); }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
        *///?} elif fabric && >=1.20.5 {
        /*player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<InteractionHand>() {
            // As above: the type's codec encodes an InteractionHand, not the Integer.
            @Override
            public InteractionHand getScreenOpeningData(ServerPlayer p) { return hand; }

            @Override
            public Component getDisplayName() { return provider.getDisplayName(); }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
        *///?} elif fabric {
        /*player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer p, net.minecraft.network.FriendlyByteBuf buffer) {
                buffer.writeVarInt(value);
            }

            @Override
            public Component getDisplayName() { return provider.getDisplayName(); }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
        *///?} elif neoforge {
        player.openMenu(provider, buffer -> buffer.writeVarInt(value));
        //?} elif forge {
        /*net.minecraftforge.network.NetworkHooks.openScreen(player, provider, buffer -> buffer.writeVarInt(value));
        *///?}
    }

    /** As {@link #openWithHand} with an entity id instead of a hand. */
    public static void openWithEntity(ServerPlayer player, MenuProvider provider, int entityId) {
        openWithInt(player, provider, entityId);
    }

    /** Opens a provider with the same mandatory integer consumed by {@link #typeWithInt}. */
    public static void openWithInt(ServerPlayer player, MenuProvider provider, int value) {
        //? if fabric && >=26.1 {
        /*player.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<Integer>() {
            @Override
            public Integer getScreenOpeningData(ServerPlayer p) { return value; }

            @Override
            public Component getDisplayName() { return provider.getDisplayName(); }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
        *///?} elif fabric && >=1.20.5 {
        /*player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<Integer>() {
            @Override
            public Integer getScreenOpeningData(ServerPlayer p) { return value; }

            @Override
            public Component getDisplayName() { return provider.getDisplayName(); }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
        *///?} elif fabric {
        /*player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer p, net.minecraft.network.FriendlyByteBuf buffer) {
                buffer.writeVarInt(value);
            }

            @Override
            public Component getDisplayName() { return provider.getDisplayName(); }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
        *///?} elif neoforge {
        player.openMenu(provider, buffer -> buffer.writeVarInt(value));
        //?} elif forge {
        /*net.minecraftforge.network.NetworkHooks.openScreen(player, provider, buffer -> buffer.writeVarInt(value));
        *///?}
    }

    /** Dispatches a menu click without exposing the era-specific click enum to common callers. */
    public static void click(AbstractContainerMenu menu, int slotId, int button,
                             CompatContainerMenu.ClickAction action, Player player) {
        //? if >=26.1 {
        menu.clicked(slotId, button, net.minecraft.world.inventory.ContainerInput.valueOf(action.name()), player);
        //?} else {
        /*menu.clicked(slotId, button, net.minecraft.world.inventory.ClickType.valueOf(action.name()), player);
        *///?}
    }
}
