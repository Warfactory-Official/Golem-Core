package dev.golem.core.fluid;

import dev.golem.core.registry.DeferredRegister;
import dev.golem.core.registry.RegistryObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * Placeable world fluids: a mod's liquid that flows, has a bucket and renders like water. The behaviour is Forge
 * 1.7.10's {@code BlockFluidClassic} / {@code BlockFluidFinite}; the vanilla parts (a {@code FlowingFluid}
 * source and flowing pair, fluid states, heights, the renderer, buckets) are what every node draws and carries.
 *
 * <ul>
 *   <li>{@link #declare} registers {@code <ns>:<name>} (source) and {@code <ns>:flowing_<name>}, and on NeoForge
 *       and Forge the loader {@code FluidType}. The block is the mod's own {@link FluidBlock} subclass, the
 *       bucket a vanilla {@link BucketItem} from {@link #bucket}.</li>
 *   <li>Spreading, decay and displacement are the 1.7 algorithms, in {@link FluidBlock}: a classic fluid reaches
 *       {@code quanta - 1} blocks on flat ground, never creates sources, and a finite fluid conserves its
 *       volume. Ticks are block ticks every {@link Spec#tickDelay} game ticks, as 1.7 scheduled them.</li>
 *   <li>{@link Physics} says how entities move inside it. {@code WATER} and {@code LAVA} put the fluid in the
 *       vanilla {@code minecraft:water} / {@code minecraft:lava} fluid tag (1.7's {@code Material.water} /
 *       {@code Material.lava}: swimming, drowning, extinguishing, burning, lava drag), which is all Fabric
 *       needs. NeoForge and Forge move entities by {@code FluidType}, which gets the same behaviour.
 *       {@code NONE} is 1.7's custom liquid material: no swimming, no current, the entity falls and walks
 *       through as through air.</li>
 * </ul>
 *
 * <p>Call {@link #declare} while the registration classes initialise (it is safe in Dejson's plain JVM: it
 * names no registry until the flush, and the fluid tag membership it states is emitted at build time), and
 * {@link #installClient} from client init.
 */
public final class WorldFluids {

    private WorldFluids() {}

    /** How an entity moves inside the fluid; see the class comment. */
    public enum Physics { WATER, LAVA, NONE }

    /**
     * One world fluid, with 1.7 Forge's defaults: density 1000, viscosity 1000, temperature 295 K, no light,
     * 8 quanta, a tick delay of viscosity / 200, translucent (render pass 1), classic (not finite), no physics.
     */
    public static final class Spec {
        final String name;
        String descriptionId;
        String fallbackName;
        String still;
        String flowing;
        int tint = 0xFFFFFFFF;
        boolean translucent = true;
        int quanta = 8;
        boolean finite;
        int tickDelay = -1;
        int density = 1000;
        int viscosity = 1000;
        int temperature = 295;
        int luminosity;
        Physics physics = Physics.NONE;

        private Spec(String name) {
            this.name = name;
        }

        /** The fluid's registry path (the source); the flowing fluid is {@code flowing_<name>}. */
        public static Spec of(String name) {
            return new Spec(name);
        }

        /** Translation key and the name shown without one. Defaults to {@code fluid.<ns>.<name>} / the path. */
        public Spec name(String descriptionId, String fallbackName) {
            this.descriptionId = descriptionId;
            this.fallbackName = fallbackName;
            return this;
        }

        /** Block-atlas sprite ids ({@code ns:path}) for the still and the flowing surface. */
        public Spec sprites(String still, String flowing) {
            this.still = still;
            this.flowing = flowing;
            return this;
        }

        /** ARGB multiplied into the sprites; {@code 0xFFFFFFFF} (the default) for sprites that carry their colour. */
        public Spec tint(int argb) {
            this.tint = argb;
            return this;
        }

        /** 1.7 render pass 1 (the default) draws on the translucent layer; pass 0 on the solid one. */
        public Spec translucent(boolean translucent) {
            this.translucent = translucent;
            return this;
        }

        /** 1.7 {@code setQuantaPerBlock}: a classic fluid flows {@code quanta - 1} blocks on flat ground. */
        public Spec quanta(int quanta) {
            if (quanta < 1 || quanta > 16) throw new IllegalArgumentException("quanta " + quanta + " outside 1..16");
            this.quanta = quanta;
            return this;
        }

        /** 1.7 {@code BlockFluidFinite}: volume-conserving, levels spread evenly, never a source. */
        public Spec finite() {
            this.finite = true;
            return this;
        }

        /** 1.7 {@code tickRate}, in game ticks; without it, viscosity / 200 as 1.7's {@code BlockFluidBase}. */
        public Spec tickDelay(int ticks) {
            this.tickDelay = ticks;
            return this;
        }

        /** 1.7 {@code Fluid.setDensity}: which of two fluids sinks; negative rises (a gas). */
        public Spec density(int density) {
            this.density = density;
            return this;
        }

        public Spec viscosity(int viscosity) {
            this.viscosity = viscosity;
            return this;
        }

        public Spec temperature(int kelvin) {
            this.temperature = kelvin;
            return this;
        }

        /**
         * 1.7 {@code Fluid.setLuminosity}: the light a full block gives, scaled down with its level the way 1.7's
         * {@code BlockFluidBase.getLightValue} scales it (see {@link FluidBlock#properties}).
         */
        public Spec luminosity(int light) {
            this.luminosity = light;
            return this;
        }

        public Spec physics(Physics physics) {
            this.physics = physics;
            return this;
        }

        public String path() { return name; }
        public String descriptionId() { return descriptionId; }
        public String fallbackName() { return fallbackName; }
        public String stillSprite() { return still; }
        public String flowingSprite() { return flowing; }
        public int tint() { return tint; }
        public boolean translucent() { return translucent; }
        public int quanta() { return quanta; }
        public boolean isFinite() { return finite; }
        public int tickDelay() { return tickDelay > 0 ? tickDelay : Math.max(1, viscosity / 200); }
        public int density() { return density; }
        public int viscosity() { return viscosity; }
        public int temperature() { return temperature; }
        public int luminosity() { return luminosity; }
        public Physics physics() { return physics; }
    }

    /** A declared fluid: its two vanilla fluids, its block and bucket (as the mod registered them). */
    public static final class Handle {
        final String namespace;
        final Spec spec;
        final RegistryObject<WorldFluid> source;
        final RegistryObject<WorldFluid> flowing;
        final Supplier<? extends Block> block;
        final @Nullable Supplier<? extends Item> bucket;
        //? if neoforge {
        RegistryObject<net.neoforged.neoforge.fluids.FluidType> type;
        //?} elif forge {
        /*RegistryObject<net.minecraftforge.fluids.FluidType> type;
        *///?}

        private Handle(String namespace, Spec spec, Supplier<? extends Block> block,
                       @Nullable Supplier<? extends Item> bucket, RegistryObject<WorldFluid> source,
                       RegistryObject<WorldFluid> flowing) {
            this.namespace = namespace;
            this.spec = spec;
            this.block = block;
            this.bucket = bucket;
            this.source = source;
            this.flowing = flowing;
        }

        public String namespace() { return namespace; }
        public Spec spec() { return spec; }
        public WorldFluid source() { return source.get(); }
        public WorldFluid flowing() { return flowing.get(); }
        public FluidBlock block() { return (FluidBlock) block.get(); }
        public @Nullable Item bucketItem() { return bucket == null ? null : bucket.get(); }
    }

    private static final List<Handle> DECLARED = new ArrayList<>();
    private static final Map<String, DeferredRegister<Fluid>> FLUIDS = new HashMap<>();
    //? if neoforge {
    private static final Map<String, DeferredRegister<net.neoforged.neoforge.fluids.FluidType>> TYPES = new HashMap<>();
    //?} elif forge {
    /*private static final Map<String, DeferredRegister<net.minecraftforge.fluids.FluidType>> TYPES = new HashMap<>();
    *///?}

    /**
     * Declares {@code spec} under {@code namespace}. {@code block} is the mod's {@link FluidBlock} for it and
     * {@code bucket} its bucket ({@code null} for none); both are only read once registration has run.
     */
    public static synchronized Handle declare(String namespace, Spec spec, Supplier<? extends Block> block,
                                              @Nullable Supplier<? extends Item> bucket) {
        if (spec.still == null || spec.flowing == null) throw new IllegalArgumentException(spec.name + " has no sprites");
        if (spec.descriptionId == null) spec.name("fluid." + namespace + "." + spec.name, spec.name);
        DeferredRegister<Fluid> fluids = FLUIDS.computeIfAbsent(namespace,
                ns -> DeferredRegister.create(() -> Registries.FLUID, ns));
        Handle[] self = new Handle[1];
        RegistryObject<WorldFluid> source = fluids.register(spec.name, () -> new WorldFluid.Source(self[0]));
        RegistryObject<WorldFluid> flowing = fluids.register("flowing_" + spec.name, () -> new WorldFluid.Flowing(self[0]));
        Handle handle = new Handle(namespace, spec, block, bucket, source, flowing);
        self[0] = handle;
        //? if neoforge {
        handle.type = TYPES.computeIfAbsent(namespace, ns -> DeferredRegister.create(
                () -> net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES, ns))
                .register(spec.name, () -> new WorldFluidType(handle));
        //?} elif forge {
        /*handle.type = TYPES.computeIfAbsent(namespace, ns -> DeferredRegister.create(
                () -> net.minecraftforge.registries.ForgeRegistries.Keys.FLUID_TYPES, ns))
                .register(spec.name, () -> new WorldFluidType(handle));
        *///?}
        // 1.7's Material.water / Material.lava, as the vanilla tag every loader reads (Fabric moves entities by it).
        String tag = switch (spec.physics) {
            case WATER -> "water";
            case LAVA -> "lava";
            case NONE -> null;
        };
        if (tag != null) {
            dev.golem.core.dejson.Tags.member("fluid", "minecraft", tag, namespace + ":" + spec.name);
            dev.golem.core.dejson.Tags.member("fluid", "minecraft", tag, namespace + ":flowing_" + spec.name);
        }
        DECLARED.add(handle);
        return handle;
    }

    /** A vanilla bucket of {@code fluid}: places a source, and an empty bucket picks a source up again. */
    public static BucketItem bucket(Handle fluid, Item.Properties properties) {
        // Forge 1.20.1 registers items before fluids: there the bucket has to take its fluid lazily.
        //? if forge {
        /*return new BucketItem(fluid::source, properties);
        *///?} else {
        return new BucketItem(fluid.source(), properties);
        //?}
    }

    static synchronized List<Handle> declared() {
        return List.copyOf(DECLARED);
    }

    /** Client init: how each loader draws every world fluid declared so far. */
    public static void installClient() {
        if (!dev.golem.core.platform.Platform.isClient()) return;
        WorldFluidsClient.install();
    }
}
