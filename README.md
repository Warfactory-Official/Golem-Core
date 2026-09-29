# Golem-Core

**In-game test harness:** `harness/` holds the mod-neutral harness CLI and scenario API; the in-game listener is
`dev.golem.core.harness` and the launch capture is `gradle/harness-spec.gradle.kts`. Setup and usage, including
first-time setup for new developers: [`harness/README.md`](harness/README.md).

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
timing.

**Declare the ticker there too.** `serverTicking(factory, tick, blocks...)`, `clientTicking(...)` and
`ticking(factory, clientTick, serverTick, blocks...)` bind a ticker to the type they build; the block
then implements `TickingBlock` instead of `EntityBlock` and writes no `getTicker` at all.

```java
BLOCK_ENTITIES.register("machine_diesel", () -> BlockEntities.serverTicking(
        TileEntityMachineDiesel::new, TileEntityMachineDiesel::serverTick,
        ModMachineBlocks.machine_diesel.get()));
```

Vanilla asks the *block* for a ticker and hands it the block entity's type, which leaves every block
re-deriving something the block entity knows: whether it ticks, on which side, through which method.
Per block that is an override, a side test, a type identity check and a private unchecked cast — 142
overrides and 114 copies of the cast in HBM. `TickingBlock`'s default answers from the type it was
handed, so the identity check disappears rather than moving: a block backing more than one type — a
pump that is steam or electric, a base class shared by six radio torches — needs no branch, because
the lookup is keyed on the type vanilla already resolved for the position.

`TickingBlock` is an interface because the blocks needing it rarely share a base class, and a more
specific interface default wins over `EntityBlock`'s. The one trap: a *class* method beats any
interface default, so a `getTicker` left anywhere in the chain silently wins. `ticks(type)`,
`ticks(type, client)` and `tickingTypes()` exist so a test can assert coverage — worth having,
because a block left on plain `EntityBlock` compiles, places, keeps its inventory, and never ticks. `Platform` exposes loader name, mod presence, physical side, and development state; consumers
should not maintain their own loader service for these queries.

Use `Entities.byId(id)` for built-in entity-type lookup. It owns the 26.1 registry `getValue` versus older
`get` spelling so consumers do not add an era guard for validation or catalogue code.

`Inventories.main(inventory)` is the live 36-slot player inventory view. `ItemStacks.carriedItem`,
`carriedInt`, and `carriedBoolean` read block state carried through modern components or legacy item NBT.
Use `Fires.canSpreadAround`, `Entities.createForConversion`, `Entities.createTriggered`, and
`Permissions.gamemasters()` rather than branching on their vanilla signatures in consumer code.

## Carried-Item Scans

`dev.golem.core.player.InventoryScan` does per-slot work on what players are carrying — the shape a
radiation system, a curio-style effect or a set bonus needs — without the inventory sweep such a
system is usually written as.

```java
private static final InventoryScan<HazardEntry[]> CARRIED = InventoryScan
        .of(HazardSystem::carriedHazards, HazardSystem::applyCarried)
        .every(() -> RadiationConfig.hazardRate);

CARRIED.install();   // once, from setup
```

The `Resolver` turns a stack into whatever the consumer acts on and runs **once per slot edit, off
the server thread**; the result is memoised per slot. The `Application` runs on the server thread at
the declared cadence and does nothing but walk that memo. A sweep re-derives every slot's answer
every pass and pays for the derivation each time; this pays for it once per change.

The changes are vanilla's own. `AbstractContainerMenu` already compares each slot against its
last-sent copy every tick, by value — so a component or NBT edit counts, not only a swap — and
notifies its listeners. `InventoryScan.MenuWatch` is that listener and a `ServerPlayer` mixin installs one
on every menu the player opens; only the player's own inventory slots are taken. While a foreign menu
is open the player's own menu is polled, which is the only way an armour or offhand edit is seen.

The contract:

- A resolver may read the stack it is handed and immutable registries, **and nothing else** — it does
  not run on the server thread. The stack it gets is the menu's private copy, replaced and never
  mutated, so it stays valid past the tick that produced it.
- The application is handed the **live** stack, not the resolved copy, so anything count- or
  component-sensitive still reads current values. Only the resolved value is memoised.
- A memo entry records the item it was resolved for and is skipped when the live slot no longer holds
  it. A lagging memo can miss a tick; it can never apply one stack's answer to another.
- `invalidate()` when the data a resolver reads changes. It queues the re-resolve rather than doing
  it, because a batch may be in flight and replacing the memo underneath one loses whatever it writes.
- `scanNow(player)` resolves and applies one player's inventory on the calling thread, for a caller
  that has just rewritten an inventory and for tests. `drainNow()`, `applyNow(player)` and `forget()`
  exist so the delivery path can be asserted without racing the server tick.

## GPU Instancing

`dev.golem.core.flywheel` vendors a GPU instancing engine, relocated from the 26.1 port in NTM-CE.
Engine Room maintains a 26.1 source branch, but this build uses no published 26.1 artifact to bind to. The engine is
written against the blaze3d GPU-buffer and pipeline API that arrived with 26.1, needing an access
transformer over `GlBuffer` that only NeoForge supplies.
It therefore compiles on 26.1 NeoForge and nowhere else; `hasVendoredFlywheel` in the NeoForge
buildscript gates the access transformer and shaders together. Persistent instance records use the
AT-widened `GlBuffer.persistentBuffer` through ordinary absolute `ByteBuffer` writes and array
`VarHandle`s; the backend uses no runtime interface injection, `Unsafe`, generated bytecode or reflective
module access.

Consumers use `render.Instancing`, which is present on every node with inert bodies. Call
`Instancing.init(resolver)` from client init; on 26.1 NeoForge it subscribes the customized engine to
the thirteen events that drive it and installs the crumbling resolver, while every other Core node is a
no-op. Ask `Instancing.isAvailable()` or `isVisualizing(level)` only where the answer changes what a
consumer draws. An available engine still renders nothing until visuals are registered against it:
instancing is an addition to the vanilla block-entity renderer path, never a replacement for it.

`render.Billboards` is the neutral billboard seam. A consumer may register one ordinary depth-writing
translucent source or several atomically owned layers. The layered form supports sorted depth-writing,
ordinary additive, and source-alpha additive rows; material order keeps sorted cloudlets before an additive
flare without exposing engine types. Core publishes all rows synchronously before asynchronous backend
maintenance and reports ownership only after that exact frame is prepared. A zero-row or failed publication
deletes every layer before ownership returns to the normal renderer. HBM's Torex cloudlets and flare are the
first layered consumer, on 26.1 NeoForge only.

Multiblocks need one hook. The crumbling overlay resolves a mined position to the block that owns the
visual, and a mined dummy owns none; CE tests for `BlockDummyable` inline, which this layer cannot
name. Pass an `Instancing.CrumblingCoreResolver` to `Instancing.init` instead. Leaving it unset costs only
the overlay on multiblocks.

See `THIRD_PARTY_NOTICES.md` — the task pipeline and visualizer framework are MIT, by Jozufozu.

## Declared Models

`dev.golem.core.render.gem` is the other half of instancing: the vocabulary a renderer uses to state
its model instead of drawing it. A renderer with its own submit body can only ever feed the vanilla
pass, because its geometry lives for one call and its transforms live in a `PoseStack` that is gone by
the next frame. The instanced backend needs every bucket of (mesh, surface) known before the first
frame and only the moving transforms touched again. One declaration satisfies both; two descriptions of
one machine would not stay in step.

A model is a tree of `Gem.Part`. Each part carries an ordered list of transform operations and,
usually, one `Geometry` drawn with one `Surface`; a part with no geometry is a joint that exists only
to place its children. **Operations apply in declaration order, and that order is a `PoseStack`'s call
order**, so transcribing a hand-written renderer is mechanical and the result is comparable line by
line. `Geometry` is supplied by the consumer — Core parses no meshes — and must describe the same
surface both ways it is asked for: as vertices written into a buffer, and as an indexed resident mesh.

An operation is either fixed or read from the render state, and a part with no state-driven operation
is rigid. Animation is declared four ways because it arrives in four shapes: `angle`, `slide`, `spin`
and `stretch` bind one float from the render state to one axis; `keys` plays a `render.anim` bus whose
playhead the render state drives rather than the clock, which is what lets a keyframed pose describe a
machine whose progress is a number rather than an elapsed time; `pose` takes a transform the state has
already composed, for a renderer that builds its bones as matrices rather than as named steps;
genuinely procedural motion binds its joint angles through `angle` and stays procedural.

A part's *contents* can move too. `mesh` chooses its geometry and `skin` its surface from the render
state — one anvil-shaped slot holding whichever of the twelve anvils this block is, one engine wearing
whichever of three tier textures it has. That is not the same as declaring a part per combination: a
part is one instance either way, and an instance that changes bucket moves; a part per combination
allocates every bucket on every machine and hides all but one. `visibleIf` is still what a part that
sometimes draws nothing uses — whether a part occupies a slot at all is settled when the model is
sealed.

Not everything a machine draws is a model. A beam whose vertices come from where it lands, a fluid
surface whose shape is its fill level, a font's glyph quads — these have no resident form for either
backend to hold, and they are drawn from `submitUndeclared` instead. It runs on every node and in
every frame, including one where the instanced backend owns the declared tree. A model that declares
nothing at all is allowed, for a renderer whose whole appearance is built rather than placed.

Extend `gem.GemBER` for a block entity or `gem.GemEntityRenderer` for an entity. Both implement `submit`
as final and build the model on first use — a part names a loaded mesh, and a subclass field holding one
is still null while a superclass constructor runs. `render.Visuals` binds a block-entity type to the
instanced backend, mirroring `Billboards`: neutral types in, engine types hidden, inert where no engine
exists. Both registries must hold the same renderer instance, so registration takes a memoized supplier.
`Visuals.owns(pos)` is how the two backends stay out of each other's way — the vanilla path asks whether
a healthy visual already claimed this frame and returns without drawing when it has.

## Generated Sprites

`dev.golem.core.render.Sprites` is for the textures a mod computes rather than ships. A declaration
names one greyscale source and a table of colour pairs, and the atlas gains one sprite per pair while
it is stitched:

```java
Sprites.remap(Sprites.Atlas.ITEMS)
        .texture("hbm:items/plate_cast")
        .permutation("iron", 0xFFFFFF, 0x353535)
        .permutation("copper", 0xFDCA88, 0x601E0D)
        .declare();
```

`hbm:items/plate_cast_iron` and `hbm:items/plate_cast_copper` are then sprite ids like any other, named
by a model the ordinary way, and no file for either exists. The remap is per channel and linear —
`ColorRamp` maps the source band (white down to `#505050` by default) onto the pair — so a highlight
lands on the light colour and a shadow on the dark one. **A tint is not a substitute.** A multiply has
no offset term, so it can reach one end of the pair or the other but never both, and shadows collapse
toward hue-times-black; across HBM's material roster that is a mean error of 26.5/255 per channel.

Declaring is the default and needs no resource at all. The same source type is registered for resource
packs as `golemcore:interpolated_remap`, which is what a pack writes in its own `atlases/` file; a mod
that ships only JSON calls `Sprites.install()` once so the type is known before the pack is read.
Declared sources run *before* the atlas's own, so a real PNG at a generated sprite's path still wins and
overriding one recoloured sprite stays a one-file job.

`Atlas.ITEMS` is its own atlas from 1.21.4 and part of the block atlas before it. Everything else this
touches also moved: the source list was renamed between 1.20.1 and 1.21, the registry of source types
became a late-bound id mapper at 26.1, `SpriteSourceType` holds a `Codec` on 1.20.1 and a `MapCodec`
after it, and only NeoForge publishes an event to register one. None of that reaches a consumer.

## Item Display Frames

`dev.golem.core.render.display` is the 1.7 half of an item icon. A ported icon is a **frame** times a
**body**: the body is the mod's own `renderInventory`/`renderCommon` calls, which belong to the item and
are transcribed wherever it is drawn; the frame is what the engine applied around it, which is shared by
every item on the same 1.7 render class. `Pose17` holds the frames — CE 1.12.2's matrices from
`render/item/ItemRenderBase.java` and `ItemRenderBaseLegacy.java`, op for op — one `Matrix4f` per
`DisplaySlot`. Compose a pose as `frame(klass, slot).mul(body)`; nothing here parses or records GL calls,
because joml already is that notation.

`DisplayFrame` turns a pose into the triple a vanilla `display{}` stores, and that conversion is the
point. A display entry is not a matrix: it is a translate/rotate/scale applied as `T . R . S`, and
vanilla applies it whatever it says. **So a chain whose translate comes last has no display form at all**,
and transcribing its numbers field by field silently moves the icon — in the case this was written for,
most of a slot. `DisplayFrame` decomposes in closed form instead, signing all three scales for a mirrored
basis, rounds to the decimals that will actually be written, rebuilds the triple, and throws when the
result misses the pose it came from.

Three more things vanilla does quietly, which are failures here rather than surprises later:
`ItemTransform.Deserializer` clamps translation to ±5 blocks and scale to ±4 without a word; `apply`
negates x, ry and rz for the two left hands regardless of the file, so those ship pre-negated; and a hand
slot left out inherits its partner's transform rather than staying unposed, so half a pair is refused.

`dejson`'s `Models.display(poses, shape)` is the way to wear one — the result is an ordinary `display{}`
block on an ordinary model, so nothing at runtime knows this layer exists.

## Generated JSON

`dev.golem.core.dejson` replaces hand-written JSON with a declaration made beside the registration.
What it produces is ordinary vanilla JSON, so it parses on the same parser, bakes on the same
pipeline, and is seen by connected-texture engines exactly as a file-loaded model is.

```java
Dejson.block("hbm", "crate_steel")
      .models(Models.cubeBottomTop("hbm:blocks/crate_steel_top",
              "hbm:blocks/crate_steel_side", "hbm:blocks/crate_steel_top"))
      .states(Variants.facing())
      .blockItem()
      .register();

Dejson.item("hbm", "ingot_steel")
      .models(Models.generated("hbm:items/ingot_steel"))
      .register();
```

HBM uses this for **4178 declarations covering 6826 files** — 5084 assets and 1742 data files —
which are no longer in the tree.

### Models

`Models` covers the parented, texture-only shapes — `cubeAll`, `cubeBottomTop`, `column`,
`orientable`, `cross`, `slabAll`/`slab`, `stairsAll`/`stairs`, `generated`, `handheld`, `blockItem`
— with three combinators above them and `parented` as the escape hatch below:

- `group()` builds several suffixed models under one declaration (`_off`/`_on`, a numbered pool,
  `""`/`_cracked`), which is what a blockstate selecting by suffix needs.
- `renderType(type, shape)` tags a shape for the cutout or translucent layer. Leaving it off does
  not fail the bake — it draws the block solid, with its transparent pixels black.
- `none()` emits nothing, for a block drawn by a renderer.

Anything carrying hand-built `elements` stays a file, and anything with real geometry is an OBJ and
belongs to the instanced renderer.

### Blockstates

`Variants` covers the blockstate half: `simple`, `facing` (horizontal), `facingAll` and `facingUp`,
`axis`, `slab`, `stairs`, `uniform`/`uniformRange`, `select`, `flag`, `intRange`, `pool`, and
`product` to cross them. A sixteen-variant `facing`/`lit`/`ext` machine is `product(facing(),
select("lit", …), select("ext", …))`. Every rotation table is transcribed from a file the consumer
already ships, because a table that is wrong by one quarter turn is not a crash.

`facingAll` and `facingUp` are the same six directions with different tables, and the difference is
which way the model was drawn — which the blockstate cannot see. Both are in use here.

`Declaration.rendered(sharedModel)` is for a block whose appearance comes from a block-entity
renderer: its blockstate points at a shared placeholder and it emits no models of its own. 150 of
HBM's blockstates are exactly that, and they are the near-identical files an OBJ machine leaves
behind.

Multipart blockstates are **not** covered. They are a different top-level JSON shape, not another
variant table, and the two that exist in practice — a cable and a fluid duct — are the only
genuinely non-trivial blockstates in the corpus. They stay files.

### Items, and the two spellings of one

`blockItem()` and `Dejson.item(...)` both publish two files, because the eras disagree about what an
item model *is*: the model at `models/item/<path>.json` that older targets load directly, and the
*definition* at `items/<path>.json` that 26.1 resolves through. They are different files, not two
eras of one file.

`blockItem()` and `blockItemDirect()` differ only in which model id the definition names — the item
model, or the block model straight away. They bake identically; both spellings are on disk in HBM,
245 direct and 141 not. 244 of the direct-spelling ones ship no `models/item/` file at all, because
below 26.1 HBM's build derives one from the definition; declaring the block item emits it from the
declaration instead, out of the same source of truth as the block model.

### Data files

The assets half has a vocabulary because a model and a blockstate are things you can describe.
Data files have nothing in common but their encoding, so `Data` is a plain path-to-content registry
and the shapes are built on top of it.

`Drops` covers block loot tables. Five hundred of them say one of six sentences — drops itself,
drops itself even out of an explosion, drops nothing, drops double as a double slab, keeps its
inventory, drops its mineral unless silk-touched — and in HBM's corpus those six sentences had
drifted into a dozen spellings, because there was nowhere to say each one once. Each method emits
the shape vanilla's own generator emits. What is deliberately *not* normalised is
`survives_explosion`: whether a blown-up block leaves its item behind is a decision about the block,
so `self` and `selfAlways` are two verbs and the caller picks one.

`Tags` covers tag files, which are the most paternal shape in a data pack — a `replace` flag that is
false in every one of them and a list of ids. Membership is spelled exactly as the file spells it,
including a leading `#`, because the difference is real: an id names one item and must exist, while
a `#` reference names a tag that may legitimately be empty. Paths are the modern singular ones; a
consumer's own build renames them for the pre-1.21 plural directories.

`Damage` and `BiomeModifiers` each exist because the same fact was being written twice:

- A damage type is three fields under `data/<ns>/damage_type/` and its behaviour is a *tag* under
  `data/minecraft/tags/damage_type/`. Nothing connects them, so adding a type and forgetting the tag
  is a one-line omission with no symptom at the point of the mistake — armour quietly starts
  protecting against radiation. `Damage.declare` takes every type at once and writes both halves
  from the one table, because the tag files are the transpose of the types.
- A Forge-family biome modifier is the same file twice under two namespaces, `neoforge:add_features`
  against `forge:add_features`. That is a loader difference, which is what this layer is for; one
  declaration emits both.

`Crafting` covers the three recipe families whose shape carries no decision: a block packing from
nine of something, the same block unpacking, and a furnace turning one item into another. It covers
those three and nothing else, because a shaped recipe with a real pattern *is* its pattern and there
is no boilerplate in it to remove. Recipe ids are passed rather than derived — the recipe book
remembers them and JEI bookmarks them, so a migration that tidied them would reset progress.

HBM's 787 `c:` material tags were the clearest case for the whole idea. 578 of them said only that
`c:ingots/iron` contains `hbm:ingot_iron` — a restatement of the shape table, one file at a time —
and are now one line per material naming the shapes it comes in.

### Delivery

`DejsonWriter` emits the registry as files and, more importantly, checks it against the files it
claims to replace. `DejsonMain` is the build entry point:

```
java dev.golem.core.dejson.DejsonMain verify <out> <authored> com.hbm.blocks.ModBlockModels …
java dev.golem.core.dejson.DejsonMain write  <out> <authored> com.hbm.blocks.ModBlockModels …
```

It runs in a plain JVM — nothing in the declaration vocabulary touches Minecraft — which is what
makes this a build step rather than a game one, and what makes the check cheap enough to be part of
`check`. The order matters: declare, verify against the authored file, *then* delete it. After the
deletion there is nothing left to compare against.

Three rules the layer keeps:

- **File wins.** A path that exists under the authored resource root is left to it, so resource
  packs still override, a hand-authored exception is still possible, and deleting a JSON file *is*
  the migration step.
- **Slots are never pruned.** A texture entry no face references can be the slot carrier a Java
  baker or a connected-texture engine reads; dropping one fails the bake outright. Everything
  declared is emitted.
- **Fail loud, at the declaration.** Malformed ids, duplicate slots, a rotation that is not a
  quarter turn, and a blockstate pointing at a model its shape never emits all throw at
  `register()`.

`BlockStates` is the one class here that names Minecraft types: it reads a `BlockState`'s properties
by name so `Dispatch.covers` can answer, and `uncovered(block, dispatch)` turns "some state has no
model" from an untextured cube in a world into a failing test.

Connected textures keep working by construction for the texture-keyed engines (a `.png.mcmeta`
descriptor sits beside the sprite, not the model) and for the post-bake wrapping ones (they wrap
whatever was baked). The one mode a generated model cannot support is model-keyed — a model JSON
declaring a custom loader — because there is no file to carry the key. Do not adopt that mode.
