package dev.golem.core.computer;

import dev.golem.core.platform.Platform;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Serves a mod's {@link ComputerComponent}s to every computer mod present: CC: Tweaked peripherals
 * ({@link PeripheralExposure}) and OpenComputers: Rebooted components (1.21.1 NeoForge). Neither is required.
 */
public final class Computers {

    public static final String OPENCOMPUTERS = "opencomputers";

    private Computers() {}

    /** Whether a computer mod this layer serves is loaded. */
    public static boolean available() {
        return Platform.isModLoaded("computercraft") || openComputers();
    }

    /** Whether OpenComputers: Rebooted is loaded on a node this layer serves it on. */
    public static boolean openComputers() {
        //? if opencomputers {
        /*return Platform.isModLoaded(OPENCOMPUTERS);
        *///?} else {
        return false;
        //?}
    }

    /** Asks {@code ports} for a component on the blocks {@code carriers} accepts. Call once from common init. */
    public static void expose(ComputerPorts ports, Predicate<Block> carriers) {
        //? if computercraft {
        PeripheralExposure.exposeBlocks(ports, carriers);
        //?}
        //? if opencomputers {
        /*if (openComputers()) dev.golem.core.opencomputers.OpenComputers.expose(ports, carriers);
        *///?}
    }

    /** The component names OpenComputers' node at {@code from} (a cable, case or adapter) reaches; for tests. */
    public static List<String> openComputersReach(ServerLevel level, BlockPos from) {
        //? if opencomputers {
        /*if (openComputers()) return dev.golem.core.opencomputers.OpenComputers.reachable(level, from);
        *///?}
        return List.of();
    }

    /** Calls a component's method through OpenComputers' own callback path, from the node at {@code from}; for tests. */
    public static @Nullable Object[] openComputersCall(ServerLevel level, BlockPos from, String component,
                                                       String method, Object... arguments) throws Exception {
        //? if opencomputers {
        /*if (openComputers()) {
            return dev.golem.core.opencomputers.OpenComputers.call(level, from, component, method, arguments);
        }
        *///?}
        throw new IllegalStateException("OpenComputers is not loaded");
    }
}
