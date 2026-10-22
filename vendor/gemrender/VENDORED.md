# GemRender, vendored

- Upstream: https://github.com/Warfactory-Official/GemRender (MrNorwood), commit `3533dce`, which is
  release 0.1.14. Licence GPL-3; the Flywheel fork in `src/flywheel` is MIT (see its `NOTICE.md`).
- Why: GemRender publishes no 26.1.2 Fabric jar. Golem Core uses the published 0.1.14 jars on its other
  five nodes and builds this tree only for 26.1 Fabric.
- Standalone Gradle/Stonecutter build. It is not part of Golem Core's build.

## What changed against 3533dce

- New node `26.1-fabric` (`settings.gradle`, `versions/26.1-fabric/gradle.properties`).
- `src/flywheelFabric/` (new): Flywheel's Fabric platform layer, its mixin config and
  `gemrender.accesswidener`. `gradle/flywheel-fabric.gradle` (new) wires it in and nests a
  metadata-only `flywheel` 1.0.7-gemrender mod.
- `build.fabric.gradle`: applies that script when `flywheel_vendored`, adds the Flywheel mixin configs,
  leaves out `EquipmentLayerRendererMixin` (NeoForge `IClientItemExtensions`).
- `src/main/templates/fabric/fabric.mod.json`: vendored-only entries (Flywheel entrypoint, access
  widener, nested jar, `flywheel` dependency). Output for the other Fabric nodes is unchanged.
- `//? if` guards for 26.1 Fabric in `src/main/.../fabric/LevelStageHooks.java`, `fabric/DirectHooks.java`,
  `src/harness/.../fabric/HarnessHooks.java`, `spike/DirectSpike.java`, `src/flywheel/.../FogUniforms.java` and
  `src/flywheel/.../compat/SodiumCompat.java` (upstream's Sodium integration on Fabric; `-Psodium` stages
  Sodium in a dev run, `sodiumMod` in `build.fabric.gradle`). `FogUniforms`' Fabric `onRenderFog` also uploads the
  fog block, which nothing else does on 26.1.
- `src/flywheel/NOTICE.md`: a "Fabric 26.1 port" section with the details.

## Building

```
./gradlew "Set active project to 26.1-fabric"     # optional; any node builds from the reset tree
./gradlew :26.1-fabric:assemble :26.1-fabric:test --configure-on-demand
./gradlew :26.1-fabric:runClient --configure-on-demand -PmakeWorld=spike -Pradar=16 -PspikeExit=600
./gradlew "Reset active project"                   # back to 1.21.1 before committing
```

The jar is `versions/26.1-fabric/build/libs/gemrender-fabric-26.1.2-0.1.14.jar`.
`:26.1-fabric:publishToMavenLocal` publishes it as `com.wf.gemrender:gemrender-fabric-26.1.2:0.1.14`, which Golem
Core's 26.1 Fabric node compiles against and nests (`deps.gemrender=vendor`); run it before the first Core build.
`--configure-on-demand` keeps Gradle from configuring the other five nodes. `ktxTest` fails on Windows on
every node: LWJGL's Windows `ktx.dll` does not export `ktxTexture2_SetImageFromMemory`.
