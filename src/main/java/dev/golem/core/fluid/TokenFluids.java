package dev.golem.core.fluid;

import dev.golem.core.registry.DeferredRegister;
import dev.golem.core.registry.RegistryObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * Registers {@link TokenFluid}s: one vanilla {@code Fluid} registry entry per fluid of a mod's own fluid model,
 * with what each loader needs to name, describe and draw it.
 *
 * <ul>
 *   <li>NeoForge and Forge: a {@code FluidType} per fluid (description id, temperature, density, viscosity)
 *       registered beside it, and its client extension (still/flowing sprite, tint): 26.1 NeoForge through
 *       {@code RegisterFluidModelsEvent}, 1.21.1 through {@code RegisterClientExtensionsEvent}, 1.20.1 Forge
 *       through {@code FluidType.initializeClient}.</li>
 *   <li>Fabric: {@code FluidVariantAttributes} (name, temperature, viscosity, gaseousness) and a render
 *       handler ({@code FluidRenderHandlerRegistry} below 26.1, {@code FluidRenderingRegistry} on 26.1).</li>
 * </ul>
 *
 * <p>Call {@link #register} from common init, before the registries are flushed, and {@link #installClient}
 * from client init. The sprites must be stitched into the block atlas (an atlas source the mod declares).
 */
public final class TokenFluids {

    private TokenFluids() {}

    /**
     * One fluid. {@code sprite} is a block-atlas sprite id ({@code ns:path}) used for both still and flowing;
     * {@code tint} is ARGB, {@code 0xFFFFFFFF} for a sprite that already carries its colour.
     * {@code descriptionId} is a translation key; {@code fallbackName} is shown when it has no translation.
     */
    public record Spec(String path, String descriptionId, String fallbackName, String sprite, int tint,
                       int temperatureKelvin, int density, int viscosity, boolean gaseous) {}

    /** A registered fluid and its spec, for the client half. */
    record Registered(Spec spec, RegistryObject<Fluid> fluid) {}

    private static final List<Registered> REGISTERED = new ArrayList<>();
    private static volatile @Nullable Map<Fluid, Spec> bySpec;

    /**
     * Declares one token fluid per spec under {@code namespace}. Returns each path's handle, resolvable once
     * the registries are flushed.
     */
    public static Map<String, RegistryObject<Fluid>> register(String namespace, List<Spec> specs) {
        DeferredRegister<Fluid> fluids = DeferredRegister.create(() -> Registries.FLUID, namespace);
        //? if neoforge {
        DeferredRegister<net.neoforged.neoforge.fluids.FluidType> types = DeferredRegister.create(
                () -> net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES, namespace);
        //?} elif forge {
        /*DeferredRegister<net.minecraftforge.fluids.FluidType> types = DeferredRegister.create(
                () -> net.minecraftforge.registries.ForgeRegistries.Keys.FLUID_TYPES, namespace);
        *///?}
        Map<String, RegistryObject<Fluid>> out = new LinkedHashMap<>();
        for (Spec spec : specs) {
            //? if neoforge {
            RegistryObject<net.neoforged.neoforge.fluids.FluidType> type =
                    types.register(spec.path(), () -> new TokenFluidType(spec));
            RegistryObject<Fluid> fluid = fluids.register(spec.path(), () -> new TokenFluid(type));
            //?} elif forge {
            /*RegistryObject<net.minecraftforge.fluids.FluidType> type =
                    types.register(spec.path(), () -> new TokenFluidType(spec));
            RegistryObject<Fluid> fluid = fluids.register(spec.path(), () -> new TokenFluid(type));
            *///?} else {
            /*RegistryObject<Fluid> fluid = fluids.register(spec.path(), () -> {
                TokenFluid created = new TokenFluid();
                net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.register(created, attributes(spec));
                return created;
            });
            *///?}
            out.put(spec.path(), fluid);
            synchronized (REGISTERED) {
                REGISTERED.add(new Registered(spec, fluid));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** Whether {@code fluid} is a token fluid registered here. */
    public static boolean isToken(Fluid fluid) {
        return fluid instanceof TokenFluid;
    }

    /** The spec {@code fluid} was registered with, or null. Valid once the registries are flushed. */
    public static @Nullable Spec specOf(Fluid fluid) {
        Map<Fluid, Spec> map = bySpec;
        if (map == null) {
            map = new IdentityHashMap<>();
            for (Registered registered : registered()) map.put(registered.fluid().get(), registered.spec());
            bySpec = map;
        }
        return map.get(fluid);
    }

    static List<Registered> registered() {
        synchronized (REGISTERED) {
            return List.copyOf(REGISTERED);
        }
    }

    /** Client init: the render registration for every token fluid declared so far. */
    public static void installClient() {
        if (!dev.golem.core.platform.Platform.isClient()) return;
        TokenFluidsClient.install();
    }

    //? if fabric {
    /*private static net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler attributes(Spec spec) {
        return new net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler() {
            @Override
            public net.minecraft.network.chat.Component getName(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant) {
                return net.minecraft.network.chat.Component.translatableWithFallback(spec.descriptionId(), spec.fallbackName());
            }

            @Override
            public int getTemperature(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant) {
                return spec.temperatureKelvin();
            }

            @Override
            public int getViscosity(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant,
                                    net.minecraft.world.level.@org.jspecify.annotations.Nullable Level level) {
                return spec.viscosity();
            }

            @Override
            public boolean isLighterThanAir(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant) {
                return spec.gaseous();
            }
        };
    }
    *///?}

    //? if neoforge {
    /** The NeoForge half of a token fluid: name, physical properties; rendering is registered by the client. */
    static final class TokenFluidType extends net.neoforged.neoforge.fluids.FluidType {

        final Spec spec;

        TokenFluidType(Spec spec) {
            super(net.neoforged.neoforge.fluids.FluidType.Properties.create()
                    .descriptionId(spec.descriptionId())
                    .temperature(spec.temperatureKelvin())
                    .density(spec.density())
                    .viscosity(spec.viscosity()));
            this.spec = spec;
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.translatableWithFallback(spec.descriptionId(), spec.fallbackName());
        }

        @Override
        public net.minecraft.network.chat.Component getDescription(net.neoforged.neoforge.fluids.FluidStack stack) {
            return getDescription();
        }
    }
    //?} elif forge {
    /*static final class TokenFluidType extends net.minecraftforge.fluids.FluidType {

        final Spec spec;

        TokenFluidType(Spec spec) {
            super(net.minecraftforge.fluids.FluidType.Properties.create()
                    .descriptionId(spec.descriptionId())
                    .temperature(spec.temperatureKelvin())
                    .density(spec.density())
                    .viscosity(spec.viscosity()));
            this.spec = spec;
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.translatableWithFallback(spec.descriptionId(), spec.fallbackName());
        }

        @Override
        public net.minecraft.network.chat.Component getDescription(net.minecraftforge.fluids.FluidStack stack) {
            return getDescription();
        }

        // Forge 1.20.1 asks the type itself for its client extension, on the client only, and from inside the
        // FluidType constructor: before this.spec is assigned, so the extension reads it when it is drawn.
        @Override
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
            consumer.accept(TokenFluidsClient.extensions(() -> this.spec));
        }
    }
    *///?}
}
