package dev.golem.core.jade;

//? if jade {
import dev.golem.core.Ids;
import dev.golem.core.info.BlockInfo;
import dev.golem.core.info.Gauge;
import dev.golem.core.info.Tank;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
//? if <1.21 {
/*import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
*///?}

/**
 * Jade's side of {@link BlockInfo}. Tanks are Jade's own fluid view. Gauges go to the client as server data and draw
 * as {@link GaugeElement}s, because Jade's energy view has a fixed bar sprite; a block entity with an energy gauge
 * gets an empty energy view, so a machine also exposed as FE shows its energy once. Found by annotation on
 * Forge-likes, the {@code jade} entrypoint on Fabric.
 */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin {

    static final String KEY = "golemcore:gauges";

    //? if >=26.1 {
    static final HideEnergy<EnergyView.Data> ENERGY = new HideEnergy<>();
    static final Tanks<FluidView.Data> TANKS = new Tanks<>();
    //?} else {
    /*static final HideEnergy<CompoundTag> ENERGY = new HideEnergy<>();
    static final Tanks<CompoundTag> TANKS = new Tanks<>();
    *///?}

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Data.INSTANCE, BlockEntity.class);
        registration.registerEnergyStorage(ENERGY, BlockEntity.class);
        registration.registerFluidStorage(TANKS, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        JadeClient.register(registration);
    }

    //? if >=26.1 {
    static Identifier uid(String path) {
    //?} else {
    /*static ResourceLocation uid(String path) {
    *///?}
        return Ids.of("golemcore", path);
    }

    private static final class Data implements IServerDataProvider<BlockAccessor> {
        static final Data INSTANCE = new Data();

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            List<Gauge> gauges = BlockInfo.gaugesOf(accessor.getBlockEntity());
            if (!gauges.isEmpty()) data.put(KEY, Gauge.save(gauges));
        }

        //? if >=1.21 {
        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            return BlockInfo.declaresGauges(accessor.getBlockEntity());
        }
        //?}

        @Override
        //? if >=26.1 {
        public Identifier getUid() {
        //?} else {
        /*public ResourceLocation getUid() {
        *///?}
            return uid("gauges");
        }
    }

    /** An empty energy view for a target with an energy gauge; null lets Jade's next provider answer. */
    static final class HideEnergy<D> implements
            //? if >=1.21 {
            IServerExtensionProvider<D>,
            //?} else {
            /*IServerExtensionProvider<BlockEntity, D>,
            *///?}
            IClientExtensionProvider<D, EnergyView> {

        //? if >=1.21 {
        @Override
        public @Nullable List<ViewGroup<D>> getGroups(Accessor<?> accessor) {
            return hides(accessor.getTarget()) ? List.of() : null;
        }
        //?} else {
        /*@Override
        public @Nullable List<ViewGroup<D>> getGroups(ServerPlayer player, ServerLevel level, BlockEntity target,
                                                      boolean showDetails) {
            return hides(target) ? List.of() : null;
        }
        *///?}

        private static boolean hides(@Nullable Object target) {
            if (!BlockInfo.declaresGauges(target)) return false;
            for (Gauge gauge : BlockInfo.gaugesOf(target)) if (gauge.kind() == Gauge.Kind.ENERGY) return true;
            return false;
        }

        @Override
        public List<ClientViewGroup<EnergyView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<D>> groups) {
            return List.of();
        }

        @Override
        //? if >=26.1 {
        public Identifier getUid() {
        //?} else {
        /*public ResourceLocation getUid() {
        *///?}
            return uid("energy_storage");
        }
    }

    /** Declared tanks as Jade's fluid view, ahead of the loader-capability view. */
    static final class Tanks<D> implements
            //? if >=1.21 {
            IServerExtensionProvider<D>,
            //?} else {
            /*IServerExtensionProvider<BlockEntity, D>,
            *///?}
            IClientExtensionProvider<D, FluidView> {

        //? if >=1.21 {
        @Override
        public @Nullable List<ViewGroup<D>> getGroups(Accessor<?> accessor) {
            return groups(accessor.getTarget());
        }

        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return BlockInfo.declaresTanks(accessor.getTarget());
        }
        //?} else {
        /*@Override
        public @Nullable List<ViewGroup<D>> getGroups(ServerPlayer player, ServerLevel level, BlockEntity target,
                                                      boolean showDetails) {
            return groups(target);
        }
        *///?}

        @SuppressWarnings("unchecked")
        private static <D> @Nullable List<ViewGroup<D>> groups(@Nullable Object target) {
            if (!BlockInfo.declaresTanks(target)) return null;
            List<Tank> tanks = BlockInfo.tanksOf(target);
            if (tanks.isEmpty()) return null;
            List<D> views = new ArrayList<>(tanks.size());
            for (Tank tank : tanks) {
                JadeFluidObject content = tank.fluid() == null ? JadeFluidObject.empty()
                        : JadeFluidObject.of(tank.fluid(), toPlatform(tank.millibuckets()));
                //? if >=26.1 {
                views.add((D) new FluidView.Data(content, toPlatform(tank.capacity())));
                //?} else {
                /*views.add((D) FluidView.writeDefault(content, toPlatform(tank.capacity())));
                *///?}
            }
            return List.of(new ViewGroup<>(views));
        }

        private static long toPlatform(long millibuckets) {
            return millibuckets * JadeFluidObject.bucketVolume() / 1000L;
        }

        @Override
        public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<D>> groups) {
            //? if >=26.1 {
            return ClientViewGroup.map(groups, data -> FluidView.readDefault((FluidView.Data) data), null);
            //?} else {
            /*return ClientViewGroup.map(groups, data -> FluidView.readDefault((CompoundTag) data), null);
            *///?}
        }

        @Override
        //? if >=26.1 {
        public Identifier getUid() {
        //?} else {
        /*public ResourceLocation getUid() {
        *///?}
            return uid("fluid_storage");
        }
    }
}
//?}
