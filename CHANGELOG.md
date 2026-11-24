# Changelog

Each release has a `## <version>` section; the release workflow uploads only the section matching
`mod.version` in gradle.properties.

## 1.0.7

### Added
- Six new nodes: 1.21.11, 26.2 and 26.3, each on NeoForge and Fabric (twelve in all). The stale 26.3 snapshot node is
  gone. JEI, REI, Jade and CC: Tweaked are wired on all six, AE2 on NeoForge 26.2 and 26.3; EMI, KubeJS, GemRender,
  Sable and the Mo' Bends port Core adapts have no builds for these versions. 26.2 and 26.3 run on OpenGL and Vulkan.
- On 26.3 a registered skybox replaces vanilla's sun, moon and stars, `Precipitation` weather also draws under
  order-independent transparency, and `Fuels` fuels burn half as long in smokers and blast furnaces, as on every
  earlier node.
- GameTests on Fabric 1.21.11 (the bridge into Fabric's registry hook now covers it).
- NeoForge 1.21.11: `Precipitation` weather replaces vanilla's again. NeoForge 21.11 never calls its custom weather
  renderers, so Core hooks vanilla's weather there as it does on Fabric.
- `Passes`, `CompatTesselator`, `ImmediateBuffers`, `WorldTargets`: immediate draws, the shared tesselator, a buffer
  source and the Fabulous targets with one spelling on every node (26.2 removed `MultiBufferSource` and `Tesselator`;
  26.3 removed output targets and Fabulous).
- `PipelineStates.vertices`, `sampler`, `uniform`, `blendFunction(Factor...)` and pipeline inspection helpers, for
  26.2's bind group layouts and split vertex topology.
- `Poses.rotate(PoseStack, Quaternionfc)`: `PoseStack.mulPose(Quaternion)`, which 26.3 renamed `rotate`.
- Development checks `-Dgolem.mixinAudit=true` (applies every mixin at the first client tick) and
  `-Dgolem.pipelineAudit=true` (compiles every static Core pipeline once a world is loaded).

### Fixed
- `chiseledBuild` built 26.2 and 26.3 with the access widener and transformers of whichever node was active; both are
  now rewritten for the node being built.

## 1.0.6

GemRender 0.1.21 or newer (older versions are refused). On 26.1, GemRender 0.1.22 needs NeoForge 26.1.2.126 or newer.
The 26.1 NeoForge jar accepts NeoForge 26.1.2.114 again (KubeJS 8.0.8 runs there).

### Added
- `/golem profiler [seconds]` (default 30, also `stop`, `status`, `open`): captures the client, and in singleplayer the
  integrated server, and writes `<game dir>/golem/profile-*.html` (an offline viewer with frame, tick and render-pass
  timings, a timeline, flame graphs, spikes, memory, threads and mods) plus a `.jfr`. `/golem profiler server` makes a
  dedicated server profile itself (op 4). The seconds left show in the action bar.
- Gem machines instance under a shader pack when Colorwheel or Iris & Oculus Flywheel Compat is installed, shadows
  included; without one they draw on the CPU as before.
- 26.1 runs on upstream Flywheel (GemRender 0.1.22) as well as GemRender 0.1.21's own Flywheel.
- `SubLevels.prepareArea`: makes sure a Sable ship's plot holds a box before blocks are written into it.
- `LivingLooks`: the hurt flash and death roll for mobs drawn through a plain `CompatEntityRenderer`.
- `Particles.noPhysics` and `render.GeometryPoints` (records a model's vertices without drawing).

### Changed
- `Spawns.onPotentialSpawns` fires once per spawn group pick, before placement checks, as 1.7's potential-spawns event
  did; it used to fire for every spawn attempt.
- Forge: a block can be exposed as an energy carrier more than once (a cable and a machine on the same block).

### Fixed
- The glider and other resident models could turn the whole world white with GemRender on strict GPU drivers
  (NTM - Continuum issue 209).
- On 1.21.1, helmet and scope overlays hid HUD mods drawn after them, such as Jade (NTM - Continuum issue 210).
- See-through line boxes (ore markers) were drawn in the fog colour far away on 1.21.1 and 1.20.1.
- On 1.20.1 Fabric, straight rails in rotated structures turned the wrong way on a half turn.
- On 1.20.1, a drained item whose colour was removed kept an empty tag and no longer stacked.
- EMI printed the mod name twice on fluids.
- The profiler sometimes wrote only the `.jfr` and no report; failures now reach chat and the log, and allocation
  rates are no longer inflated by each thread's start-up.

## 1.0.5

Use GemRender 0.1.20.

### Added
- EMI support on 1.21.1 and 1.20.1: every viewer category a mod declares becomes a native EMI category, with recipe ids,
  favourites, the recipe tree and recipe filling (EMI has no 26.1 build).
- `viewer.FluidIcons`: a slot that shows a fluid as an item stands for that fluid in EMI, JEI and REI, so looking up a
  fluid finds the mod's recipes.
- `Fov.registerMultiplier`: zoom through the eased per-tick FOV multiplier, which keeps other mods' FOV modifiers and
  leaves the hand's FOV alone.
- `Harvests.afterBreak`: ore experience through each loader's own rule for tools that break blocks themselves.
- `HeldItems`: components whose change alone does not re-equip the held item.
- `Poses.copyOf`, `CameraHooks` pitch and yaw offsets, `EntityInterpolation` for interpolated projectiles.
- `Surface.flat`: alpha-tested, unlit, depth-writing Gem parts; fog-free particle and additive triangle types.
- `Crafting.smoke` for smoker recipes, `TagSource` for tags generated from the running game, and
  `DeferredRegister.Blocks.defaultMapColors`.
- `Precipitation.weatherModLoaded()`: weather renderers stand aside for Particle Rain and Pretty Rain.
- `Platform.conventionNamespace()`: `c` or `forge`.

### Fixed
- Stereo sounds were heard at full volume at any distance; they fade out linearly like 1.7's sound system.
- Particles whose fade dipped below zero flashed at full opacity for a frame.
- A 26.1 NeoForge world could fail to load with an `ArrayIndexOutOfBoundsException` in biome modifiers; exclusions get
  their own tags.
- Additive effects drew as plain alpha blending on 1.20.1.
- Gun aiming changed the hand's FOV on NeoForge and Forge.
- Plane particles faced the wrong way and drew one face only.
- After a respawn, block entities synced through `GolemSync` (casts, battery sockets) showed empty until rejoining.
- Non-uniformly scaled models were dark on 1.21.1 and 26.1.
- Old-AI mobs used the new-AI melee reach; `CompatMeleeAttackGoal.inReach` gives them 1.7's.
- Dynamic lights stood down only for GemRender's own shader-pack check; Oculus and 26.1 Iris now count too.
- Blocks had no map colour, so vanilla and Xaero's maps showed the ground under them.
- On Sable ships, smoke and steam particles without physics stuck inside blocks.
- Tints registered their reload listener more than once.
- Player-rider poses turned about the camera instead of the viewer's eye, throwing riders across the sky in third
  person.
- Under a shader pack on 1.20.1 and 1.21.1, textured sky parts (suns, planets, skyboxes) were hidden by the pack's sky.
- JEI click-area hints printed over a screen's own tooltip.

## 1.0.4

### Added
- `Protection`: land-protection mods register a provider that can refuse block changes and entity harm from explosions,
  spreading effects and hazards, per chunk or per position; `Explosions` and mob griefing ask it on every node.
- GameTests: `GameTestHelpers.detachedServerPlayer` (a player that never joined, so other mods' login handlers stay
  quiet) and `setOp`.
- Computer components: `ComputerComponent`, `@ComputerMethod`, `ComputerPorts` and `Computers.expose` describe one
  component per block without naming a computer mod. They are served as CC: Tweaked peripherals on every node and as
  OpenComputers: Rebooted components on 1.21.1 NeoForge (cables, cases and Adapters).
- `Hud.addCameraOverlay`: fullscreen helmet and camera overlays drawn under the hotbar and crosshair on every node.
- `Damage.NO_KNOCKBACK`: damage types with no attacker that must not knock the victim in a random direction (1.20.5+).

### Fixed
- A 1.20.1 Forge server with Canary, or any mod that overwrites `processPendingLoads` privately, failed at "Preparing
  level".

### Changed
- `PeripheralPorts` is now `ComputerPorts`.

## 1.0.3

Use GemRender 0.1.20.

### Added
- World facades for dimension mods: teleports that keep rotation (`Teleports`), a level's own weather (`Weathers`) and
  time of day (`TimeOfDay`), live bed, respawn and burning rules (`WorldAttributes`, `Respawns`), portal creation
  (`Portals`), water evaporation by position (`Evaporation`), fishing loot categories (`FishingLoot`) and light queries
  (`LightLevels`).
- Entity and death hooks: callbacks after each server entity tick (`EntityTicks`), death vetoes that run before drops
  (`Deaths`), entity removal and the initial state sent to a new tracker (`Tracking`).
- Integer opening data for menus whose layout depends on server state.
- `Wearable`: a non-armour item worn in an armour slot, taken by shift-click, mobs and the armour slot.
- `Remainders`: crafting leftovers for another mod's items.
- Grouped and resource-backed item models on the legacy nodes (`GroupedModels`, `UnbakedModels`), item-model aliases
  (`ItemModels`) and particle, HUD and sky registration seams.
- `GuiDraw.aboveSlots` and `GuiDraw.tooltipStratum`: overlays and hand-drawn tooltip panels sit above a menu's slot items
  on every node.
- `gui.TextSearch`: search bars match through Just Enough Characters (pinyin) when it is installed.
- `Subtypes.listed`: variants shown apart in JEI and REI that all match one recipe, like a 1.7 wildcard output.
- Development harness: matching client and integrated-server tick rates, and screenshots that wait for a finished frame.

### Fixed
- 1.21.1 hung on world exit while terrain was still generating.
- Held weapons left camera space under an Iris shader pack on 26.1.
- Mirrored Gem parts were culled, so offhand gun models were inside out.
- Wind charges ran block removal hooks meant for real explosions.
- Loot `set_count` on 1.20.1 clamped to the stack size (`golemcore:set_count`); integer count ranges keep their counts.
- Chest loot rolls that came out empty were kept on Fabric.
- GemRender lighting sampled the wrong lightmap texel when instance and embedded light were combined.
- Loaded-chunk queries blocked while generation was pending.
- Recipe-table replacements were lost before the recipe manager reloaded.
- Recipe-viewer queries ignored hidden categories.
- `CompatFallingEntity` lost the carried block, fall time and drop flags on save.
- Water-physics fluids did not put out burning entities on Fabric; creative-tab insertions lost their position on older
  nodes; armour model selection, before-chat overlays and container dragging differed between nodes.

### Changed
- Dev runs use GemRender 0.1.20.
- `BlockChanges` no longer needs a mixin.

## 1.0.2

### Added
- Item data on scripted recipe outputs: `ItemPatch` (components from 1.20.5, NBT on 1.20.1) and
  `RecipeFields.ItemOut.data`, filled by both KubeJS adapters, so a scripted output such as a vehicle crate keeps its data.
- Facades for configurable data: the `golemcore:looting` loot condition and `LootFunctions`, dynamic data maps
  (`DataMaps.dynamic`), `BlockChanges`, and item use and click hooks (`ItemUses`, `ItemClicks`) that also reach vanilla items.
- Item automation on faces (`ItemExposure.exposeAutomation`): a face query gets a hopper's access, so Refined Storage
  importers, AE2 buses and item pipes can reach a mod's machines on NeoForge and Forge.
- `ItemExposure.FaceView`: a multiblock part lists every slot but moves only its own, so Holo Inventory does not count
  items twice.
- `ItemStacks.fitsInContainerItem` (nothing that carries items goes into a container item) and carried-data helpers
  (`setCarriedLong`, `carriedLong`, `removeCarried`, `isPlain`) for 1.7-style stack locks.
- `world.OrderedShape`: a voxel shape that picks its first box in declaration order, with no access widening.
- Recipe viewer tab-order queries for JEI and REI.

### Fixed
- Gem machines were invisible under a shader pack on 26.1 with GemRender; `GemBER` draws them while a pack is in use.
- World fluid blocks (`FluidBlock`) are `LiquidBlock`s, so Voxy can bake them instead of crashing; vanilla fluid ticks
  and lava mixing stay off.
- EMI hid every `CompatCustomRecipe`; it is a plain `CraftingRecipe` now and `ComponentShapedRecipe` reports its grid size
  below 26.1.
- First-person items under an Iris shader pack below 26.1 were drawn off screen (`HandSpace`).
- Legacy item models keep a gun's base model below 1.21.4; player skin layers on `HumanoidPose`.
- GameTest helpers count Golem payloads exactly on Forge 1.20.1.

### Changed
- GemRender comes from CurseForge on every node; the 0.1.14 refmap reflection is gone (0.1.14 is refused).
- Release jars are named without the version prefix. GitHub releases also carry `<name>-dev.jar` (with the harness) and
  `golem-harness.jar` for addon developers; the harness spec moved to `gradle/mod/`.
- Pre-release build numbers restart at 1 for each version.

## 1.0.1

### Added
- AE2 support: autocrafting machines that pattern providers push whole jobs into (`autocraft.CraftingMachines`), blocks a
  storage bus reads as ME storage (`storage.ItemStores`), storage cell contents (`CellContents`) and a block click hook.
  Adapters for AE2 on 26.1 and 1.21.1 NeoForge and 1.20.1 Forge and Fabric.
- Dynamic lights facade (`client.DynamicLights`) for mod-supplied spot and point lights through GemRender 0.1.17+.
- Mo' Bends support: `BodyAnimations` lets a mod turn off its own crouch offsets for players Mo' Bends animates
  (1.20.1 Forge, 1.21.1 NeoForge).
- Sable: whole-structure assembly (`SableMoves.wholeStructures`), block masses by rule (`SubLevels.MassRule`), sub-level
  points, pushes and carried velocity (`SubLevels.intoSubLevels`, `push`, `accelerate`, `carriedVelocity`).
- `Disguises` chunk looks: blocks whose look depends on placement (paint, connections) are baked into the chunk mesh,
  with face overlays and keyed meshes from Gem geometry, instead of a block entity renderer.
- Block outline replacement (`BlockOutlines.replaceShape`) on each loader's outline event.
- Recipe viewers: hidden-ingredient lists (`ViewerPlugin.hidden`, honours JEI's "Show Hidden Ingredients") and a focus
  query.
- `BiomeModifiers.addFeatures` with an excluded biome set (used for TerraFirmaCraft worlds).
- Read-only item view on NeoForge's item capability (for Holo Inventory and similar).
- `CompatScreen.menuBackground()` to skip the blur and darkening behind a screen.
- Harness: crosshair target in the `player` query, JEI focus counts, a view-bobbing option, and `gradlew` on Linux and macOS.

### Fixed
- 1.20.1 Forge dedicated servers dropped every Golem payload (wrong channel name).
- `GemBER` culled machines on Sable sub-levels by their grid position.
- GemRender 0.1.14 view bobbing and its 1.20.1 refmap; GemRender workarounds are now gated on the GemRender version.
- `CarriedBlock` drops read the block entity from the loot context (a player break on 1.20.1 removed it first).
- Dev runs of mods built on Golem Core load the vendored GemRender on 26.1 Fabric with no setup of their own.

### Changed
- GemRender 0.1.18 (fixes 0.1.17's 1.20.1 Forge + Embeddium crash).

## 1.0.0

Initial release
