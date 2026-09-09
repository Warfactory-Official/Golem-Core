# Golem-Core

## Networking

Consumers define a payload and its encoding once. The same `GolemCodec` is used by Fabric's raw
1.20.1 buffer transport and by modern Fabric's `CustomPacketPayload`/`StreamCodec` transport.

```java
public record SetModePayload(BlockPos pos, int mode) implements GolemPayload {
    // Use Identifier on 26.1; use ResourceLocation on 1.20.1/1.21.1.
    public static final ResourceLocation ID = Ids.of("example", "set_mode");
    public static final GolemCodec<SetModePayload> CODEC = GolemCodec.of(SetModePayload.class, ID,
            (payload, buffer) -> { buffer.writeBlockPos(payload.pos); buffer.writeVarInt(payload.mode); },
            buffer -> new SetModePayload(buffer.readBlockPos(), buffer.readVarInt()));

    @Override public ResourceLocation id() { return ID; }
}
```

Register each direction once in the consumer's normal common initializer, before a connection is
opened:

```java
GolemNetwork.registerPlayToClient(SetModePayload.CODEC, ClientPackets::handleSetMode);
GolemNetwork.registerPlayToServer(SetModePayload.CODEC, ServerPackets::handleSetMode);
```

Send on every target with the same calls:

```java
GolemNetwork.sendToPlayer(player, SetModePayload.CODEC, payload);
GolemNetwork.sendToTracking(level, pos, SetModePayload.CODEC, payload);
GolemNetwork.sendToServer(SetModePayload.CODEC, payload);
```

Handlers run on the logical main thread. Decode input defensively: packet fields are network input.
The 1.20.1 Forge `SimpleChannel` backend needs the concrete message class; `GolemCodec` retains it
so the consumer registration call remains identical. NeoForge registration remains event-timed and
must be bridged from `RegisterPayloadHandlersEvent`; it is not part of the 1.20.1 Forge backend.

## Entity and Recipe Compatibility

`CompatEntity` owns the vanilla synchronized-data override. Consumers define values once with
`defineData(SynchedData)`, while `loadData(DataIn)` and `saveData(DataOut)` remain the persistence hooks.

```java
private static final EntityDataAccessor<Boolean> ARMED =
        SynchedEntityData.defineId(MyEntity.class, EntityDataSerializers.BOOLEAN);

@Override
protected void defineData(SynchedData data) {
    data.define(ARMED, false);
}
```

Do not override `defineSynchedData`; its vanilla signature differs by Minecraft version. Continue using
`getEntityData().get(ARMED)` and `getEntityData().set(ARMED, value)` after definition.

Moving entities can call `compatHandlePortal()` and
`compatUpdateInWaterStateAndDoFluidPushing()` from their shared tick. The latter selects 26.1's
`updateFluidInteraction()` or the older water-push hook. Use `Entities.trackDeltas(builder)` when the
entity's velocity should be tracked on loaders that expose that builder option.

Use `Recipes.smeltingResult(level, input)` for furnace lookup. It returns
`Optional<ItemStack>` and hides `RecipeHolder`, `SingleRecipeInput`, and cached-recipe implementation
types. The returned stack is a copy and may be modified by the caller.

For recipe-presence checks, use `Recipes.hasRecipe(level, namespace, path)`. It resolves the namespaced
id and checks the active recipe manager without exposing the version-specific manager shape. For a
rectangular crafting grid, use `Recipes.craftingResult(level, width, height, inputs)`; it performs the
crafting-grid lookup/assembly (including shaped recipes) behind the same facade, validates the input
dimensions, and returns an independent result stack. These operations are also the neutral seam for
guard-free HBM GameTests.

`ComponentIngredient` and `ComponentShapedRecipe` preserve exact component-bearing shaped ingredients
across the component and legacy-NBT eras. Register the retained serializer through `CustomRecipes` and
keep consumer recipe JSON/version guards out of the mod. `DamageEvents` similarly exposes one mutable,
cancellable living-damage event across all loaders; resistance semantics remain consumer behavior.

## Consumer Boundary

Consumer feature code should not override Minecraft methods whose signatures vary by target. Use the
Core hooks instead: `CompatContainerScreen`/`CompatScreen` for GUI rendering and input, `CompatBlock`
and `CompatFireBlock` for block callbacks, `CompatItem` for tooltips/use/ticks, and
`CompatEnchantments` for loot, synthetic tools, and armor-change prevention such as Curse of Binding.
Version guards belong in these Core backends.

Declare payload codecs and handlers once with `GolemNetwork`; loader entrypoints only flush retained
registrations at their lifecycle hook. Declare GameTests once in a `GameTests.Suite`; Forge and NeoForge
entrypoints only forward their registration event. Loader adapters may translate event-bus timing, but
must not duplicate the consumer's screen, renderer, HUD, packet, or test inventories.

## Registry, Platform, and World Facades

Use `BlockEntities.type(factory, blocks...)` inside the consumer's deferred registration supplier. It
owns Fabric's builder and the Forge/NeoForge constructor differences without changing registration
timing. `Platform` exposes loader name, mod presence, physical side, and development state; consumers
should not maintain their own loader service for these queries.

Use `Entities.byId(id)` for built-in entity-type lookup. It owns the 26.1 registry `getValue` versus older
`get` spelling so consumers do not add an era guard for validation or catalogue code.

`Inventories.main(inventory)` is the live 36-slot player inventory view. `ItemStacks.carriedItem`,
`carriedInt`, and `carriedBoolean` read block state carried through modern components or legacy item NBT.
Use `Fires.canSpreadAround`, `Entities.createForConversion`, `Entities.createTriggered`, and
`Permissions.gamemasters()` rather than branching on their vanilla signatures in consumer code.

## GPU Instancing

`dev.golem.core.flywheel` vendors a GPU instancing engine, relocated from the 26.1 port in NTM-CE.
Flywheel proper has no 26.1 build, so on that line there is nothing to bind to — and the engine is
written against the blaze3d GPU-buffer and pipeline API that arrived with 26.1, needing an access
transformer over `GlBuffer` and an interface injection onto `GpuBuffer` that only NeoForge supplies.
It therefore compiles on 26.1 NeoForge and nowhere else; `hasVendoredFlywheel` in the NeoForge
buildscript gates the access transformer, the interface injection and the shaders together.

`Flywheel` itself is present on every node with inert bodies. Call `Flywheel.init()` unconditionally
at client init — it subscribes the engine to the thirteen events that drive it, across both buses,
and does nothing where there is no engine. Ask `Flywheel.isAvailable()` only where the answer changes
what a consumer draws. An available engine still renders nothing until visuals are registered against
it: instancing is an addition to the vanilla block-entity renderer path, never a replacement for it.

Multiblocks need one hook. The crumbling overlay resolves a mined position to the block that owns the
visual, and a mined dummy owns none; CE tests for `BlockDummyable` inline, which this layer cannot
name. Install a `CrumblingCoreResolver` through `Flywheel.setCrumblingCoreResolver` instead. Leaving
it unset costs only the overlay on multiblocks.

See `THIRD_PARTY_NOTICES.md` — the task pipeline and visualizer framework are MIT, by Jozufozu.
