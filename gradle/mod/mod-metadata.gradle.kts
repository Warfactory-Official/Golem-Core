// Shared by every Golem Core mod. Apply it from each loader buildscript, after its processResources setup:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/mod-metadata.gradle.kts"))
//
// Fills the credits and links of fabric.mod.json, mods.toml and neoforge.mods.toml from one place, so the three
// loaders' metadata cannot drift. Inputs, from gradle.properties (each optional, blank leaves the field empty):
//   mod.credits                             one short line, appended as the last paragraph of the description
//   mod.homepage, mod.sources, mod.issues   links
// The full contributor list stays in CREDITS.txt, which the jar tasks ship next to the licence.
// Tokens replaced in the processed files:
//   @golem.credits@   "\n\nCredits: <mod.credits>" (JSON-escaped in fabric.mod.json, raw in the TOML literal string)
//   @golem.homepage@ @golem.sources@ @golem.issues@
// Logs `modMetadata: filled N file(s)`.

val metadataFiles = listOf("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")

fun metadataProp(name: String): String = (findProperty(name) as String?)?.trim() ?: ""

// Escapes for JSON and TOML basic strings alike.
fun quoted(text: String): String = buildString {
    for (c in text) when {
        c == '"' || c == '\\' -> append('\\').append(c)
        c.code < 0x20 || c.code > 0x7e -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
}

val creditsLine = metadataProp("mod.credits").let { if (it.isEmpty()) "" else "Credits: $it" }
val links = mapOf(
    "@golem.homepage@" to quoted(metadataProp("mod.homepage")),
    "@golem.sources@" to quoted(metadataProp("mod.sources")),
    "@golem.issues@" to quoted(metadataProp("mod.issues")),
)

tasks.withType<ProcessResources>().configureEach {
    val task = this
    inputs.property("golem.modMetadata", links + ("credits" to creditsLine))
    doLast {
        var files = 0
        for (name in metadataFiles) {
            val file = task.destinationDir.resolve(name)
            if (!file.isFile) continue
            val text = file.readText(Charsets.UTF_8)
            if (links.keys.none { it in text } && "@golem.credits@" !in text) continue
            // The TOML description is a ''' literal string: no escapes there, real newlines instead.
            val credits = when {
                creditsLine.isEmpty() -> ""
                name.endsWith(".json") -> "\\n\\n" + quoted(creditsLine)
                else -> "\n\n" + creditsLine
            }
            val filled = links.entries.fold(text.replace("@golem.credits@", credits)) { acc, (token, value) -> acc.replace(token, value) }
            file.writeText(filled, Charsets.UTF_8)
            files++
        }
        if (files > 0) logger.lifecycle("modMetadata: filled $files file(s) (${task.name})")
    }
}
