// Shared by every Golem Core mod with shaders of its own. Apply it from each loader buildscript:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/shaders-26.3.gradle.kts"))
//
// 26.3 compiles every shader to SPIR-V, which needs explicit locations on stage inputs and outputs
// (`layout(location = 0) in vec2 texCoord0;`). From 26.3 on, each file under the mod's `src/shaders-26.3`
// (laid out as resources, e.g. `assets/<ns>/shaders/core/<name>.fsh`) replaces the shared file of the same path,
// as Golem Core does for its own shaders. Golem's 26.3 `sky_shader.vsh` writes `texCoord0` at location 0 and
// `localPos` at 1, so a `Sky.Shader` fragment declares those inputs and `layout(location = 0) out` for its colour.
//
// Parameters (project properties, see legacy-assets.gradle.kts):
//   golem.build.shaders263.dir   the overlay folder, relative to the root project. Default: src/shaders-26.3
//
// Node facts it reads, which the loader buildscript sets as extra properties before applying:
//   mc_version  the node's Minecraft version (required)

fun atLeast(version: String, floor: String): Boolean {
    val a = version.split('.').map { it.toIntOrNull() ?: 0 }
    val b = floor.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return true
}

val overlay: File = rootProject.file(findProperty("golem.build.shaders263.dir")?.toString() ?: "src/shaders-26.3")

if (atLeast(extra.properties["mc_version"] as String, "26.3") && overlay.isDirectory) {
    tasks.named<ProcessResources>("processResources") {
        inputs.dir(overlay)
        exclude { !it.file.toPath().startsWith(overlay.toPath()) && File(overlay, it.relativePath.pathString).isFile }
        from(overlay)
        doLast {
            val count = overlay.walkTopDown().count { it.isFile }
            logger.lifecycle("[shaders-26.3] ${project.name}: $count shader file(s) from ${overlay.name}")
        }
    }
}
