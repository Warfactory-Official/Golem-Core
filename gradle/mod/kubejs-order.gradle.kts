// Shared by every Golem Core mod. Apply it from each loader buildscript:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/kubejs-order.gradle.kts"))
//
// Parameters: none of its own. It reads the node facts the loader buildscript sets as extra properties before
// applying:
//   loader       "neoforge", "forge" or "fabric" (required)
// and the node's deps.kubejs (blank: nothing to do).
//
// Why: KubeJS asks its plugins for event groups and bindings, and runs the startup scripts, inside its own mod
// constructor. A Golem-based mod declares its dev.golem.core.script groups and bindings in its constructor
// (ModEntries.start), so it must be constructed first, or its startup events and bindings are missing from the startup
// scripts (Golem Core's adapter then WARNs and still serves the server and client scripts). This adds to each mod of
// the processed mods.toml / neoforge.mods.toml an optional dependency on kubejs with ordering BEFORE:
//   NeoForge 1.21.1 / 26.1   type = "optional"   (absent: no error; present: sorted before it)
//   Forge 1.20.1             mandatory = false   (same; Forge constructs in parallel, so Golem Core's 6.x adapter also
//                                                 waits for the Golem-based mods' declarations in its plugin init)
// A mod that already names kubejs in its metadata is left alone. Fabric has no ordering in fabric.mod.json; there the
// adapter runs the pending Golem declarations itself (see dev.golem.core.kubejs).
//
// Rewrites every ProcessResources task's output (the mod's and, e.g., a probe source set's); logs
// `kubejsOrder: ordered N mod(s) before kubejs`.

fun Project.applyKubejsOrder(loader: String) {
    if (loader == "fabric") return
    if ((findProperty("deps.kubejs") as String?).isNullOrBlank()) return
    val fileName = if (loader == "neoforge") "META-INF/neoforge.mods.toml" else "META-INF/mods.toml"
    tasks.withType<ProcessResources>().configureEach {
        val task = this
        doLast {
            val toml = task.destinationDir.resolve(fileName)
            if (!toml.isFile) return@doLast
            val text = toml.readText()
            val modIds = Regex("""\[\[mods]]\s*\r?\n(?:[^\[]*?\r?\n)*?\s*modId\s*=\s*"([^"]+)"""")
                .findAll(text).map { it.groupValues[1] }.toList()
            val blocks = StringBuilder()
            var ordered = 0
            for (id in modIds) {
                val already = Regex("""\[\[dependencies\.${Regex.escape(id)}]]\s*\r?\n\s*modId\s*=\s*"kubejs"""").containsMatchIn(text)
                if (already) continue
                blocks.append("\n# Added by Golem Core's kubejs-order.gradle.kts: declare script groups and bindings before KubeJS's startup scripts.\n")
                blocks.append("[[dependencies.$id]]\n")
                blocks.append("modId = \"kubejs\"\n")
                blocks.append(if (loader == "neoforge") "type = \"optional\"\n" else "mandatory = false\n")
                blocks.append("versionRange = \"[0,)\"\n")
                blocks.append("ordering = \"BEFORE\"\n")
                blocks.append("side = \"BOTH\"\n")
                ordered++
            }
            if (ordered > 0) toml.writeText(text.trimEnd() + "\n" + blocks)
            logger.lifecycle("kubejsOrder: ordered $ordered mod(s) before kubejs (${task.name})")
        }
    }
}

applyKubejsOrder((findProperty("loader") ?: error("kubejs-order needs extra[\"loader\"]")).toString())
