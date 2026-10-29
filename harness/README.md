# Golem harness — in-game test harness for Golem Core mods

The harness starts real development clients of a Golem Core mod and drives them like a player would: it
presses keys, clicks slots, places blocks, runs commands, reads blocks and block entities, and takes
screenshots. Tests are single-file Java **scenarios**. Each run writes a report with a result per node and
every screenshot.

It works with any mod built on Golem Core. Nothing in this folder knows about a specific mod: each mod
supplies one `harness.json` plus its own scenarios.

- **Two clients** run at the same time by default, each with a 2 GB heap. `up --all` runs every declared
  node at once (all six for HBM, about 12 GB of working set) — see "Running all nodes at once".
- Client windows are **minimized, never focused and never grab the mouse**, so you can keep working.
- Clients start from `java` directly (a launch captured once from Gradle). No Gradle daemon stays alive.
- All six nodes are supported: 26.1 NeoForge/Fabric, 1.21.1 NeoForge/Fabric, 1.20.1 Forge/Fabric.
- Dev builds only. The uploaded Golem Core jars (`releaseJar`, `gradle/release-jar.gradle.kts`) leave the listener,
  its mixins and its registrations out; `HarnessQueryRegistry` stays, so a mod's query registrations still compile and run.
  Each GitHub release also carries the unstripped jars (`<name>-dev.jar`) and the CLI (`golem-harness.jar`) for mods
  built from released jars (see "Mods without a Golem Core checkout").

## What is where

| Location | What | Owner |
|---|---|---|
| `Golem-Core/harness/` (this folder) | The CLI (`cli`), the scenario API (`api`), wrappers, default client options | Golem Core |
| `Golem-Core/src/main/java/dev/golem/core/harness/` | The in-game listener: commands, input, queries, screenshots, worlds, log buffer; `HarnessSetup` (NeoForge/Forge) and `HarnessFabricClient` start it | Golem Core |
| `Golem-Core/src/main/java/dev/golem/core/harness/mixin/` + `golemcore.harness.mixins.json` | `MixinHarnessWindow`, `MixinHarnessMouseGrab` and the accessor mixins, in their own config | Golem Core |
| `Golem-Core/gradle/release-jar.gradle.kts` | `releaseJar`: the uploaded jar, with everything above except `HarnessQueryRegistry` and `HarnessHooks` removed | Golem Core |
| `Golem-Core/gradle/mod/harness-spec.gradle.kts` | Captures a node's real `runClient` launch as a JSON spec | Golem Core |
| `<mod>/harness.json` | The mod's configuration: nodes, ports, paths | the mod |
| `<mod>/harness/scenarios/*.java` | The mod's scenarios | the mod |
| `<mod>/harness/fixtures/*.nbt` | Structure templates, published into the world as `harness:<name>` | the mod |
| `<mod>/harness/out/` | Screenshots, client logs, Gradle logs, reports (git-ignored) | the mod |
| `<mod>/.harness/state.json` | Which clients are running (git-ignored) | the mod |

The listener is only active on a **development client** started with `-Dgolem.harness.port=<port>`. Release
jars do not contain it (see `releaseJar` above).

## First-time setup (new developers)

1. **Check out the repositories side by side.** The mod's composite build expects Golem Core next to it:

   ```
   <workspace>/
     Golem-Core/       https://github.com/Bruv-Network/Golem-Core
     <mod>/            e.g. NTM-Continuum (HBM)
   ```

2. **Install the tools.**
   - A JDK 21 or newer on the `PATH` or in `JAVA_HOME`. The first build also lets Gradle provision the JDKs
     each node needs (26.1 → 25, 1.21.1 → 21, 1.20.1 → 17).
   - On Windows, **Git Bash**. Use the POSIX wrapper `./harness`; the `.cmd` wrapper mangles JSON arguments.
   - About 5 GB of free RAM for two clients.

3. **Build the mod once** so every node has its dependencies (this takes a while the first time):

   ```bash
   cd <workspace>/<mod>
   ./gradlew chiseledBuild
   ```

4. **Check the configuration** from the mod folder:

   ```bash
   cd <workspace>/<mod>
   ../Golem-Core/harness/harness config        # builds the harness jar on first use, prints harness.json
   ```

5. **Start two clients and run a scenario:**

   ```bash
   ../Golem-Core/harness/harness up --nodes 26.1-neoforge,26.1-fabric
   ../Golem-Core/harness/harness run BarrelVsChest.java      # bare name = the mod's scenarios folder
   ../Golem-Core/harness/harness down
   ```

   The first `up` of a node builds it, captures its launch, creates a superflat creative world named
   `harness`, snapshots it, and joins it. That takes a few minutes. Later starts take about a minute.
   The report is in `<mod>/harness/out/runs/<stamp>-BarrelVsChest/report.md`.

Tip: add an alias, e.g. `alias harness=../Golem-Core/harness/harness`, or put the folder on your `PATH`.
The CLI finds `harness.json` by walking up from the working directory, so run it anywhere inside the mod.

## Adding the harness to a mod

1. In each loader buildscript (`build.neoforge.gradle.kts`, `build.fabric.gradle.kts`, `build.forge.gradle.kts`):

   ```kotlin
   apply(from = rootProject.file("../Golem-Core/gradle/mod/harness-spec.gradle.kts"))
   ```

   It needs the `java_version` extra property that the Stonecutter nodes already set.

2. Put a `harness.json` at the mod root. All paths are relative to that file:

   ```json
   {
     "name": "mymod",
     "mod": ".",
     "golemCore": "../Golem-Core",
     "scenarios": "harness/scenarios",
     "fixtures": "harness/fixtures",
     "out": "harness/out",
     "world": "harness",
     "defaultNodes": ["1.21.1-neoforge", "1.21.1-fabric"],
     "maxClients": 2,
     "allNodesMaxClients": 6,
     "retentionHours": 10,
     "nodes": [
       {"name": "26.1-neoforge", "minecraft": "26.1", "loader": "neoforge", "port": 47601, "packFormat": 84, "java": 25}
     ]
   }
   ```

   Give every node a distinct `port`. `maxClients` caps an ordinary `up` (keep it 2); `allNodesMaxClients`
   (default: the number of nodes) caps `up --all`. `optionsTemplate` (optional) replaces the bundled `options.txt`.
   `retentionHours` (default 10, `0` keeps everything) is how long run folders, ad-hoc screenshots and logs
   under `out` are kept (see Disk use).

3. Ignore `harness/out/` and `.harness/` in the mod's `.gitignore`.

4. Optional: register mod-specific queries on the client, callable as `query --node N <kind> key=value` or
   `c.query("<kind>", params)` from a scenario:

   ```java
   dev.golem.core.harness.HarnessQueryRegistry.register("mymod_machine", (mc, params) -> { ... return json; });
   ```

   A query runs on the client thread. If it needs server data, read only chunks that are already fully loaded
   and answer `pending` otherwise — a query that waits on chunk generation can freeze the client.

## Mods without a Golem Core checkout

An addon built from released jars (the wiki's template generator takes Golem Core and NTM - Continuum from their GitHub
releases) has no checkout to build the listener from. It gets it from the same Golem Core release; the template sets
this up:

- `harness.json` has `"golemCore": null`, so the CLI builds and watches only the mod.
- Each loader buildscript reads Golem Core from an Ivy repository over
  `https://github.com/Warfactory-Official/Golem-Core/releases/download` (pattern
  `v[revision]/[module]-[revision]+[classifier].[ext]`, coordinates `github.golem-core:golemcore-<loader>:<version>`
  with classifier `<mc>`; a second pattern reads the `<module>-<mc>-<version>+<mc>.jar` names of releases up to 1.0.1) and applies `gradle/mod/harness-spec.gradle.kts` (copied into the project). Only when
  `-Pharness.spec` is set, the run classpath takes classifier `<mc>-dev` instead, the release's `<name>-dev.jar`. Every
  node is configured in that run, so the release must carry all six dev jars.
- `harness/harness` (and `harness.cmd`) download `golem-harness.jar` from the release named by `deps.golemcore` in
  `gradle.properties` into `.harness/` and run it.

## Multiplayer (dedicated server)

A client can join a dedicated server on localhost instead of a singleplayer world, so interaction, menus and
block-entity sync are tested over the real network.

1. In the node's `runs/server` folder (the mod's EULA is already accepted there) write a `server.properties` with
   `online-mode=false`, a free `server-port`, `enable-rcon=true`, `rcon.port`, `rcon.password=rc` and
   `pause-when-empty-seconds=-1` (an empty paused server ignores rcon).
2. Start the server from the mod folder: `./gradlew.bat :<node>:runServer`. Stop it by sending `stop` over rcon, and check
   that its java process is gone (stopping Gradle does not stop the forked server).
3. Join it: `harness up --nodes <node> --server 127.0.0.1:<server-port> --username RcServer`. Op the player after its
   first join (`op RcServer` over rcon); opping a never-seen name resolves a different offline profile.
4. In a scenario, `c.joinServer("127.0.0.1", port, timeoutMs)` rejoins after `c.leaveWorld(...)`, and
   `new Rcon("127.0.0.1", rconPort, "rc").command("data get block x y z")` reads what the server holds. The `block` and
   `block_entity` queries only see the client's copy on a dedicated server, which is what a sync test wants.

`RcServerMenus` and `RcServerSync` in HBM's scenarios are working examples; they read the ports from the node's
`server.properties`.

## Commands

```bash
harness up [--nodes a,b | --all] [--rebuild | --no-build] [--no-sodium] [--show] [--heap 2G] [--world harness] [--world-type seeded] [--username Name] [--server host:port] [--size 1920x1080]
harness down [--nodes a,b | --all]
harness restart [--nodes a,b | --all]    # down + up, same options as up
harness status                           # live clients: in world, screen, fps, heap
harness logs --node N                    # tail of the client log
harness run <Scenario.java> [--nodes a,b] [--label name] [--echo]
harness world snapshot|reset|create --node N [--world name] [--world-type seeded]
harness clean [--older-than <hours>]     # delete old runs/screenshots/logs now (0 = everything)
harness config                           # print the resolved harness.json
harness help                             # everything
```

| `up` option | Effect |
|---|---|
| `--nodes a,b` | Any two nodes declared in `harness.json` (default: `defaultNodes`) |
| `--all` | Every declared node at once, capped by `allNodesMaxClients` (opt-in; see below) |
| (default) | Run Gradle only for nodes whose build inputs changed since their last captured launch |
| `--rebuild` | Always run Gradle, even when no input looks newer than the captured launch |
| `--no-build` | Never run Gradle; reuse the captured launch even if sources changed |
| `--no-sodium` | Leave out Sodium/Embeddium (and Indium) |
| `--show` | Keep the windows visible (still unfocused) |
| `--heap 1500M` | Change the heap per client |
| `--size 1920x1080` | Window size (default 1280x720). Windows shrinks a window larger than the screen to fit it, *after* boot, so `status` may still show the requested size; `up` warns when the size exceeds the screen. Read the real size from the framebuffer (`Minecraft.getWindow().getWidth()`) when it matters |
| `--world-type seeded` | Use the seeded normal world (see Worlds) |
| `--username Name` | Offline player name instead of the dev run's (`Dev` / `Player###`); a singleplayer host keeps one player in `level.dat` whatever the name |

How `up` saves time:

- **Gradle is skipped when nothing changed.** A node's launch spec (`versions/<node>/build/harness/spec.json`)
  is reused when no file in either project's `src/`, `gradle/`, top-level `*.kts`/`*.properties` or the
  node's `versions/<node>/` folder (minus `build`, `runs`, `logs`) is newer than it. This is a timestamp
  check, so a touched-but-unchanged file still rebuilds; something it cannot see (a changed dependency in
  the Gradle cache, a file outside those folders) needs `--rebuild`.
- **One Gradle run for all nodes.** When nodes do need building, one invocation captures every one of them
  (`-Pharness.spec=.../{node}/...`), so Gradle starts and configures once rather than once per node. Fabric
  nodes build Golem Core first, also in one invocation.
- **Clients boot while Gradle is still working.** Each client launches the moment its own spec is written
  (in whatever order Gradle finishes them), and all clients wait for their world in parallel.

Measured on the 1.21.1 pair (2026-10-03): nothing changed **47 s** to both in world; one touched source
file **1 min 48 s** (both nodes recompiled).

Driving one client from the shell (`--node` may be left out when only one client is up):

```bash
harness cmd --node 1.21.1-fabric /give @s minecraft:chest
harness key --node 1.21.1-fabric key.use          # key mapping name, letter, space, escape, f3, mouse.left
harness lookat --node 1.21.1-fabric 0 101 2
harness use --node 1.21.1-fabric
harness query --node 1.21.1-fabric screen         # slots and widgets with GUI coordinates
harness click --node 1.21.1-fabric 88 40          # GUI coordinates from the screen query
harness query --node 1.21.1-fabric block x=0 y=101 z=2
harness query --node 1.21.1-fabric log lines=40 filter=Exception
harness screenshot --node 1.21.1-fabric after-click --crop 0,0,200,120
harness exec --node 1.21.1-fabric '{"cmd":"wait_screen","contains":"Crate","timeout_ms":5000}'
```

All input goes through vanilla's own keyboard and mouse handlers, so key bindings, screens and slots react
as they do for a real device.

## Writing a scenario

A scenario is one public class that implements `Scenario`. It is compiled in memory; it is not a mod class
and needs no mod rebuild.

```java
import dev.golem.harness.api.*;

public class OpenCrate implements Scenario {
    public void run(Client c) throws Exception {
        c.markLog();
        Fixtures.platform(c, 0, 100, 0, 6);                  // forceload, fill, creative, day, tp, face +Z
        Fixtures.placeOnTop(c, "mymod:crate", 0, 100, 2);    // by hand: give, aim at the top face, use
        Fixtures.interact(c, 0, 101, 2);                     // empty-handed right-click
        c.waitScreen("Crate", 5000);
        Expect.screenStaysOpen(c, "Crate", 15);              // catches a menu the server closes next tick
        c.clickSlot(0);                                      // real mouse click at the slot's centre
        c.screenshot("crate-open");                          // out/runs/<run>/<node>/crate-open.png
        Expect.logCleanSinceMark(c, "/(ERROR|FATAL)[]]");    // pair with c.markLog() at the start
    }
}
```

`harness run OpenCrate.java` runs it once on every live client, in parallel. It writes `report.md` and
`report.json` (one row per node, every screenshot, the full command transcript) and a `failure.png` for a
failing node. The exit code is 1 when any node fails.

- `Fixtures`: `platform`, `fill`, `waitForSkylight`, `waitForTarget`, `give`, `giveOnly`, `placeOnTop`, `interact`,
  `template`, `setBlock`.
- `Expect`: `screenOpen`, `noScreen`, `screenStaysOpen`, `block`, `slotHolds`, `slotEmpty`, `inventoryHolds`,
  `logMatches`, `logClean`, `logCleanSinceMark`, `equal`, `that`.
- `Client`: `cmd`, `chat`, `key`, `keyDown/Up`, `type`, `mouseMove`, `click`, `scroll`, `use`, `attack`,
  `hotbar`, `slotClick`, `clickSlot`, `clickWidget`, `look`, `lookAt`, `teleport`, `waitTicks`, `waitServerTicks`,
  `waitScreen`, `waitInWorld`, `tickRate`, `setOption`, `leaveWorld`, `joinWorld`, `createWorld`, `screenshot`, `node()`, and the
  queries `status`, `player`, `inventory`, `screen`, `block`, `blockEntity` (server-side NBT as SNBT),
  `entities(radius)` (the raw `entities` query with `server:true` returns server positions, game time and effects), `log`, plus mod queries.
`tickRate` applies to the client and integrated server and persists across world rejoins. `waitTicks`
counts client ticks; RPC `timeout_ms` uses elapsed wall time. Screenshot `skip_frames` counts completed
rendered frames, so captures remain synchronized when ticks run faster than rendering.
`waitServerTicks` follows the integrated world's game time, so server startup timers retain their duration
when rendering and server simulation achieve different tick rates.
View changes complete after a rendered frame, including vanilla's crosshair pick. The raw `clear_chat`
command removes chat history before a pixel comparison.

**Look at the screenshots.** A passing check proves only what it checks: several real rendering bugs passed
every assertion and were visible only in the PNG.

## Worlds

- `world snapshot --node N` stores a clean copy of the save. `world reset --node N` restores it in a few
  seconds without restarting the JVM. Reset after a scenario that leaves craters, fluids or radiation.
- `world create --node N --world other` makes another superflat creative world.
- **Seeded world (opt-in, for worldgen tests):** `up --world-type seeded` or `world create --world-type seeded`
  uses `harness_seeded`: the default (non-flat) world preset with the fixed seed `4815162342`
  (`Client.SEEDED_WORLD_SEED`; never change it — worldgen tests depend on the terrain), creative, peaceful,
  commands on, time 6000, clear weather, no daylight/weather cycle, no mob spawning, with its own snapshot.
  From a scenario: `c.useSeededWorld()`, `c.useFlatWorld()`, `c.resetWorld()`, `c.currentWorld()`,
  `c.freezeWorld()`. The default stays the superflat `harness` world.
- The fixtures send both game-rule spellings: 26.1 renamed `doDaylightCycle`/`doWeatherCycle`/`doMobSpawning`/
  `naturalRegeneration` to `advance_time`/`advance_weather`/`spawn_mobs`/`natural_health_regeneration`.

## Version differences scenarios must allow for

- **Reach:** 26.1 uses `minecraft:block_interaction_range`, 1.21.1 `minecraft:player.block_interaction_range`,
  and 1.20.1 has no reach attribute at all. Keep clicks within 5 blocks of the eye on every node.
- **Item syntax in `/give`:** 26.1 and 1.21.1 use components (`item[custom_name='x']`), 1.20.1 uses NBT.
- **Fabric before 26.1** gives a key to one key binding only; two bindings on one key do not both fire.
- On 1.20.1, vanilla's "Loaded N recipes" line counts recipe *types*, not recipes.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `no harness.json found` | Run the CLI from inside the mod folder, or pass `--config <file>` / set `HARNESS_CONFIG`. |
| JSON arguments break on Windows | Use `./harness` from Git Bash, not `harness.cmd`. |
| `up` reused a launch although something changed | The freshness check only looks at timestamps in `src/`, `gradle/`, build files and `versions/<node>/`. Run `up --rebuild`. |
| `up` hangs at "building and capturing the client launch" | The capture fell through to a real client. Build the node by hand, then `up --no-build` to reuse `versions/<node>/build/harness/spec.json`. |
| A Fabric node fails with a stale Golem Core jar | Golem Core sources are newer than its remapped jar. `up` builds Golem Core first; if you build by hand run `../Golem-Core/gradlew chiseledBuild`. Uncommitted broken Golem Core sources stop every Fabric node. |
| A Gradle build fails while clients are up | Configuring a Fabric node re-remaps the Golem Core jar a running client holds open. Use `--configure-on-demand` (the CLI does) or `harness down` first. |
| "client thread did not answer in time" | A query or command blocked the client, often on chunk generation after a big `/forceload`. Make the query non-blocking (see "Adding the harness"); restart the clients. |
| Log checks fail on boot warnings | Scope them with `c.markLog()` and `Expect.logCleanSinceMark`. |
| 1.21.1 on the wrong Java dies with `ClassNotFoundException: java.lang.Boolean` | Each node must run on its own Java (26.1 → 25, 1.21.1 → 21, 1.20.1 → 17); the captured spec does this. |

## Running all nodes at once

```bash
harness up --all                       # every node in harness.json, launched one after another, all left running
harness run WiajCanneries.java         # runs on every live client in parallel, one report
harness down
```

- Without `--all` the cap stays `maxClients` (2): `--nodes all` alone refuses at the third client.
  `--max-clients N` overrides either cap.
- Nodes still launch one at a time (each `up` builds its Stonecutter node), so six take several minutes
  the first time. Plan for about 2 GB of working set per client — about 12 GB for six; lower `--heap` if needed.
- **Agents: run two nodes unless the user has explicitly asked for more.** Do not use `--all`, `--nodes all`
  or `--max-clients` above 2 on your own initiative — six clients can exhaust the machine's memory and stall
  the user's own work. When more than two nodes are needed and the user did not ask for `--all`, cover them two
  at a time (`down`, then `up --nodes` the next pair).

## Limits

- Singleplayer only (integrated server). Server-side queries run on the integrated server thread.
- `look`/`lookAt` set the rotation directly; there is no mouse-delta camera path while the cursor is free.
- Minimized windows still render. On 26.1 the idle FPS limit is `afk` (30 fps after a minute without
  physical input); screenshots are unaffected.
- Each client uses about 2 GB of working set (two by default; six with `up --all`).

## Disk use

Screenshots are full-size PNGs and add up quickly (a busy day of runs is several gigabytes). `up`, `restart`
and `run` therefore first delete harness output older than `retentionHours` (`harness.json`, default 10):

- run folders under `out/runs/`, aged by the `yyyyMMdd-HHmmss` stamp in their name;
- files under `out/adhoc/`, `out/clients/` and `out/gradle/`, aged by modification time (the log of a client
  that is still up is kept).

Nothing else in `out` is touched, so a tool's own folder there (HBM's `icon-audit`) stays. A line such as
`[harness] cleanup: removed 267 run folder(s) and 505 other file(s) older than 10 h, freed 2971.8 MB` says
what went. Keep a run you need by copying its folder out of `out/runs` first. `harness clean` does the same
on demand, `--older-than 2` with another age, `--older-than 0` empties it; `"retentionHours": 0` turns the
automatic cleanup off.
