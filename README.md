# Golem Core

Golem Core is a cross-version, cross-loader compatibility layer for Minecraft mods. A mod built on it is
written once, against the newest Minecraft API, and compiles from that one source tree to every supported
target:

| Minecraft | Loaders |
|---|---|
| 26.3 | NeoForge, Fabric |
| 26.2 | NeoForge, Fabric |
| 26.1 | NeoForge, Fabric |
| 1.21.11 | NeoForge, Fabric |
| 1.21.1 | NeoForge, Fabric |
| 1.20.1 | Forge, Fabric |

Golem Core takes the newest API shapes and backports them onto older versions. Where Minecraft versions or
loaders differ, it provides one neutral API that hides the difference. Mods call it unconditionally and need
no version branches of their own. Its facades cover registration, networking, rendering, GUI, entity and
block-entity data, fluids, recipes, GameTests, recipe viewers (JEI and REI) and build-time JSON generation.
The goal is identical behaviour on every target, not just similar behaviour.

Golem Core is a required dependency: install the jar matching your Minecraft version and loader alongside
any mod built on it.

## Rendering contracts

`Fogs.Modifier` supplies native linear fog ranges and colors without replacing vanilla shader includes.
`Clouds.registerAdditions` keeps the native first cloud layer and appends layers using that pass's height,
color, alpha and clock. A mod that retains native cloud or lightmap differences between targets documents
those exceptions beside its registration.

Development launches retain native OpenGL validation by default. On 26.1 Fabric, opt into production
validation behavior with `-Dgolemcore.disableGlValidation=true`; NeoForge provides
`-Dneoforge.disableGlValidation=true`. These switches apply at startup, and
`RenderOps.validationEnabled()` reports the actual native flag (always false on 26.3, whose OpenGL backend has none).

26.2 and 26.3 draw with reversed depth (cleared to 0, nearer is greater); `PipelineStates.Depth` keeps meaning
"nearer" on every node. 26.3 compiles every shader to SPIR-V, so its copies of Core's shaders live in
`src/shaders-26.3` (explicit locations, `#include`); a mod's own shaders paired with Core's need the same there.

## In-game test harness

For 26.1 Fabric client GameTests, `-Dgolemcore.client.gametest.serverTickRate=<rate>` adjusts positive
wait budgets for the server rate chosen by the launch, using whole multiples of the native 20 TPS budget.
The default is 20; nonpositive timeout values keep their native meaning.

`harness/` holds a mod-neutral test harness. It starts real development clients of a Golem Core mod and
drives them like a player would: it presses keys, clicks slots, places blocks, runs commands, reads blocks
and block entities, and takes screenshots. Tests are single-file Java **scenarios**, and each run writes a
report with a result per node and every screenshot.

- **Two clients** run at the same time by default, each with a 2 GB heap.
- Client windows stay minimized, are never focused and never grab the mouse, so you can keep working.
- All twelve nodes are supported. Golem Core's own `harness.json` runs the modern nodes against the dev-only probe
  mod (`src/probe`, never in a jar) with its scenario `harness/scenarios/ProbeLooks.java`.
- On NeoForge the harness presses "Proceed to main menu" when another mod only warns at loading; an error still stops
  the client.
- Each mod supplies its own `harness.json` and scenarios; nothing in the harness knows about a specific mod.

| Location | What |
|---|---|
| `harness/` | The CLI, the scenario API, wrappers and default client options |
| `src/main/java/dev/golem/core/harness/` | The in-game listener: commands, input, queries, screenshots |
| `gradle/mod/harness-spec.gradle.kts` | Captures a node's `runClient` launch as a JSON spec |

The listener only runs on a development client started with `-Dgolem.harness.port=<port>`. Release builds
never open it.

Setup, usage and first-time setup for new developers: [`harness/README.md`](harness/README.md).

## Dev runs of Core itself

On the modern nodes (1.21.11 and newer) the dev runs carry the probe mod `golemprobe` (`src/probe`): a `Fuels` fuel,
GameTests (`runGameTestServer` on NeoForge, `runGametest` on Fabric), recipe-viewer and Jade content, and a skybox and
weather that draw while the player holds a debug stick. `-Ppeers.run=true` adds the optional peers the node declares
(JEI with its config libraries, REI with Cloth Config and Architectury, Jade, CC: Tweaked, and AE2 with GuideME on
NeoForge 26.x); `-Ppeers.skip=cc` (any of jei, rei, jade, cc, ae2) leaves one out. CC: Tweaked 1.120.0 for 26.1.2
Fabric names an REI plugin class its jar lacks, so on that node CC and REI cannot run together.

26.2 and 26.3 also run on Vulkan (`preferredGraphicsBackend:"vulkan"` in `options.txt`). On NeoForge 26.2 the early
loading window creates an OpenGL window that Vulkan cannot use: set `earlyWindowControl = false` in `fml.toml` there.

## License

LGPL-3.0. See [`LICENSE`](LICENSE), [`LICENSE.LESSER`](LICENSE.LESSER) and
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
