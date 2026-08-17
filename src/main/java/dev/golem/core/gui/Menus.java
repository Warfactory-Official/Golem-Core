package dev.golem.core.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Menus that carry a {@link BlockPos} to the client when they open.
 *
 * <p>Vanilla's own menu type cannot send extra opening data, so every loader invented its own — and
 * then renamed it. NeoForge has {@code IMenuTypeExtension}; Fabric had {@code ExtendedScreenHandlerType}
 * and renamed it to {@code ExtendedMenuType} in the 26.1 line, having already changed how the data is
 * written (a {@code StreamCodec} from 1.20.5, a raw buffer write before that). Four spellings of one
 * idea, none of them Minecraft's.
 *
 * <p>The position is what a machine menu needs in practice: the container re-derives its state from
 * {@code level.getBlockState(pos)} on both sides, which is safer than trusting a client-sent value.
 */
public final class Menus {

    private Menus() {}

    /** Builds a menu from its network id, the player's inventory, and the position it was opened at. */
    @FunctionalInterface
    public interface PosFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory playerInventory, BlockPos pos);
    }

    /** Creates a menu type whose menu is opened with a {@link BlockPos} as extra data. */
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
}
