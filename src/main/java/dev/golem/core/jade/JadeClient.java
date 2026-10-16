package dev.golem.core.jade;

//? if jade {
import dev.golem.core.info.BlockInfo;
import dev.golem.core.info.Gauge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/** The client half of {@link JadePlugin}, kept apart so a dedicated server never loads a client type. */
final class JadeClient {

    private JadeClient() {}

    static void register(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Tooltip.INSTANCE, Block.class);
        registration.registerEnergyStorageClient(JadePlugin.ENERGY);
        registration.registerFluidStorageClient(JadePlugin.TANKS);
        registration.addRayTraceCallback((hit, accessor, original) -> redirect(registration, accessor));
    }

    private static Accessor<?> redirect(IWailaClientRegistration registration, Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor cell)) return accessor;
        Level level = cell.getLevel();
        BlockPos target = BlockInfo.redirectOf(level, cell.getPosition());
        if (target == null) return accessor;
        return registration.blockAccessor().from(cell)
                .blockState(level.getBlockState(target))
                .blockEntity(() -> level.getBlockEntity(target))
                .hit(cell.getHitResult().withPosition(target))
                .build();
    }

    private static final class Tooltip implements IBlockComponentProvider {
        static final Tooltip INSTANCE = new Tooltip();

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            for (Gauge gauge : Gauge.load(accessor.getServerData(), JadePlugin.KEY)) {
                Component text = GaugeElement.text(gauge);
                if (gauge.max() > 0) tooltip.add(new GaugeElement(gauge, text));
                else tooltip.add(text);
            }
        }

        @Override
        //? if >=26.1 {
        public Identifier getUid() {
        //?} else {
        /*public ResourceLocation getUid() {
        *///?}
            return JadePlugin.uid("gauges");
        }
    }
}
//?}
