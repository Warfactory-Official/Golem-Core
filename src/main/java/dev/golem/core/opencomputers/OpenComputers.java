package dev.golem.core.opencomputers;

//? if opencomputers {
/*import dev.golem.core.Ids;
import dev.golem.core.computer.ComputerComponent;
import dev.golem.core.computer.Computers;
import dev.golem.core.computer.ComputerPorts;
import dev.golem.core.platform.Lifecycle;
import dev.golem.core.platform.NeoForgeEntrypoint;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jspecify.annotations.Nullable;

/^*
 * Serves {@link ComputerComponent}s to OpenComputers: Rebooted. Cables and computer cases reach a block entity through
 * OC's {@code sided_environment} block capability, one node per component, joined to a network of its own so a cable
 * can connect to it. An Adapter reaches any position, folded cells included, through a {@link DriverBlock}, and owns
 * the environment it gets.
 ^/
public final class OpenComputers {

    private static final BlockCapability<SidedEnvironment, Direction> SIDED_ENVIRONMENT =
            BlockCapability.createSided(Ids.of(Computers.OPENCOMPUTERS, "sided_environment"), SidedEnvironment.class);
    // a client cable only asks whether to draw its connector
    private static final SidedEnvironment CLIENT = new SidedEnvironment() {
        @Override
        public @Nullable Node sidedNode(Direction side) {
            return null;
        }

        @Override
        public boolean canConnect(Direction side) {
            return true;
        }
    };
    // server thread only
    private static final Map<ComputerComponent, ComponentEnvironment> CABLED = new IdentityHashMap<>();

    private OpenComputers() {}

    public static void expose(ComputerPorts ports, Predicate<Block> carriers) {
        NeoForgeEntrypoint.modBus().addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> Driver.add(new Adapters(ports, carriers))));
        NeoForgeEntrypoint.modBus().addListener((RegisterCapabilitiesEvent event) -> {
            Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(carriers).toArray(Block[]::new);
            if (blocks.length == 0) return;
            event.registerBlock(SIDED_ENVIRONMENT, (level, pos, state, be, side) -> {
                if (!(level instanceof ServerLevel server)) return be == null ? null : CLIENT;
                return be == null ? null : cabled(ports.at(server, pos, state, be, side));
            }, blocks);
        });
        Lifecycle.onServerTick(server -> dropRemoved());
        Lifecycle.onServerStopping(server -> {
            for (ComponentEnvironment environment : CABLED.values()) environment.node().remove();
            CABLED.clear();
        });
    }

    /^* The component names OC's node at {@code from} (a cable, case or adapter) reaches. For tests and probes. ^/
    public static List<String> reachable(ServerLevel level, BlockPos from) {
        List<String> names = new ArrayList<>();
        if (level.getBlockEntity(from) instanceof Environment host && host.node() != null) {
            for (Node node : host.node().reachableNodes()) if (node instanceof Component c) names.add(c.name());
        }
        return names;
    }

    /^* Calls {@code method} on the component named {@code name} through OC's own callback path. ^/
    public static @Nullable Object[] call(ServerLevel level, BlockPos from, String name, String method,
                                          Object... arguments) throws Exception {
        if (level.getBlockEntity(from) instanceof Environment host && host.node() != null) {
            for (Node node : host.node().reachableNodes()) {
                if (node instanceof Component c && c.name().equals(name)) return c.invoke(method, null, arguments);
            }
        }
        throw new IllegalArgumentException("no component " + name + " reachable from " + from);
    }

    private static @Nullable ComponentEnvironment cabled(@Nullable ComputerComponent component) {
        if (component == null) return null;
        return CABLED.computeIfAbsent(component, c -> {
            ComponentEnvironment environment = new ComponentEnvironment(c);
            Network.joinNewNetwork(environment.node());
            return environment;
        });
    }

    private static void dropRemoved() {
        for (Iterator<ComponentEnvironment> it = CABLED.values().iterator(); it.hasNext(); ) {
            ComponentEnvironment environment = it.next();
            if (environment.component().target() instanceof BlockEntity be && be.isRemoved()) {
                environment.node().remove();
                it.remove();
            }
        }
    }

    // OC's Adapter passes the direction from itself to the block, so the touched face is its opposite
    private record Adapters(ComputerPorts ports, Predicate<Block> carriers) implements DriverBlock {

        @Override
        public boolean worksWith(Level level, BlockPos pos, Direction side) {
            return find(level, pos, side) != null;
        }

        @Override
        public @Nullable ManagedEnvironment createEnvironment(Level level, BlockPos pos, Direction side) {
            ComputerComponent component = find(level, pos, side);
            return component == null ? null : new ComponentEnvironment(component);
        }

        private @Nullable ComputerComponent find(Level level, BlockPos pos, Direction side) {
            if (!(level instanceof ServerLevel server)) return null;
            BlockState state = server.getBlockState(pos);
            if (!carriers.test(state.getBlock())) return null;
            return ports.at(server, pos, state, server.getBlockEntity(pos), side.getOpposite());
        }
    }
}
*///?}
