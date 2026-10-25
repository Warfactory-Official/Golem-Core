# Golem Core

Golem Core is a cross-version, cross-loader compatibility layer for Minecraft mods. A mod built on it is
written once, against the newest Minecraft API, and compiles from that one source tree to every supported
target:

| Minecraft | Loaders |
|---|---|
| 26.1 | NeoForge, Fabric |
| 1.21.1 | NeoForge, Fabric |
| 1.20.1 | Forge, Fabric |

Golem Core takes the newest API shapes and backports them onto older versions. Where Minecraft versions or
loaders differ, it provides one neutral API that hides the difference. Mods call it unconditionally and need
no version branches of their own. Its facades cover registration, networking, rendering, GUI, entity and
block-entity data, fluids, recipes, GameTests, recipe viewers (JEI and REI) and build-time JSON generation.
The goal is identical behaviour on every target, not just similar behaviour.

Golem Core is a required dependency: install the jar matching your Minecraft version and loader alongside
any mod built on it.

## In-game test harness

`harness/` holds a mod-neutral test harness. It starts real development clients of a Golem Core mod and
drives them like a player would: it presses keys, clicks slots, places blocks, runs commands, reads blocks
and block entities, and takes screenshots. Tests are single-file Java **scenarios**, and each run writes a
report with a result per node and every screenshot.

- **Two clients** run at the same time by default, each with a 2 GB heap.
- Client windows stay minimized, are never focused and never grab the mouse, so you can keep working.
- All six nodes are supported.
- Each mod supplies its own `harness.json` and scenarios; nothing in the harness knows about a specific mod.

| Location | What |
|---|---|
| `harness/` | The CLI, the scenario API, wrappers and default client options |
| `src/main/java/dev/golem/core/harness/` | The in-game listener: commands, input, queries, screenshots |
| `gradle/mod/harness-spec.gradle.kts` | Captures a node's `runClient` launch as a JSON spec |

The listener only runs on a development client started with `-Dgolem.harness.port=<port>`. Release builds
never open it.

Setup, usage and first-time setup for new developers: [`harness/README.md`](harness/README.md).

## License

LGPL-3.0. See [`LICENSE`](LICENSE), [`LICENSE.LESSER`](LICENSE.LESSER) and
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
