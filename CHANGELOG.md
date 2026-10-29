# Changelog

Each release has a `## <version>` section; the release workflow uploads only the section matching
`mod.version` in gradle.properties.

## 1.0.2

### Added

- GemRender materials sample lightmap texel centers after combining instance and embedded lighting.
- Integer opening data for menus whose layouts depend on server state.
- Callbacks after individual server entity ticks, through the same phase on every supported node.
- Development harness control for matching client and integrated-server tick rates across all supported nodes.
- World policies for bed rules, respawn destinations, portal creation and fixed clocks across the supported nodes.
- Public item-model aliases, loaded-chunk lookup, entity tracking and particle/HUD registration seams.
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

- Recipe-viewer queries respect hidden categories and report whether the recipe screen actually opened.
- Development screenshots wait for completed rendered frames, and harness deadlines retain wall-clock duration at
  accelerated tick rates.
- Integer loot-count ranges retain their authored counts on nodes whose vanilla function clamps to item stack limits.
- Loaded-chunk queries return immediately while generation is pending.
- Recipe-table replacements remain active until the recipe manager reloads, and schemas describe shared requirement
  flags.
- Death notifications fire after loader vetoes have cleared and before drops.
- Water-physics fluids extinguish entities on Fabric; creative-tab insertions retain their requested position on older
  nodes.
- Armour geometry selection, before-chat overlays and container dragging retain the same behavior across nodes.
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
