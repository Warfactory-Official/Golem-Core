# Changelog

Each release has a `## <version>` section; the release workflow uploads only the section matching
`mod.version` in gradle.properties.

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
