// GemRender from a local checkout instead of its CurseForge file. With `gemrender.dir` (the checkout, relative to the
// root project) and `gemrender.version` set, a node whose jar that checkout has built uses it; any other node, or a
// machine without the checkout, keeps the CurseMaven file `deps.gemrender`. Applied before `dependencies`: the jar is
// copied into a Maven layout under build/ and its notation left in extra["golem.gemrender.local"]. A flat-directory
// module skips ModDevGradle Legacy's remap transform (nested Flywheel stays SRG), a Maven one does not.
val gemDir = (findProperty("gemrender.dir") as String?)?.takeIf { it.isNotBlank() }
val gemVersion = (findProperty("gemrender.version") as String?)?.takeIf { it.isNotBlank() }
if (gemDir != null && gemVersion != null) {
    val mc = project.name.substringBefore('-')
    val node = if (project.name.endsWith("-fabric")) "$mc-fabric" else mc
    val jar = rootProject.file(gemDir).resolve("versions/$node/build/libs")
        .listFiles { f -> f.name.endsWith("-$gemVersion.jar") }?.firstOrNull()
    if (jar != null) {
        // The jar's timestamp is part of the version: Loom caches a remapped mod by coordinate, not by content.
        val version = "$gemVersion-${jar.lastModified().toString(16)}"
        val artifact = "gemrender-$node"
        val base = layout.buildDirectory.dir("gemrender-local").get().asFile.resolve("com/wf/gemrender/$artifact")
        val folder = base.resolve(version)
        if (!folder.isDirectory) {
            base.deleteRecursively()
            folder.mkdirs()
            jar.copyTo(folder.resolve("$artifact-$version.jar"))
            folder.resolve("$artifact-$version.pom").writeText(
                "<project><modelVersion>4.0.0</modelVersion><groupId>com.wf.gemrender</groupId>" +
                    "<artifactId>$artifact</artifactId><version>$version</version></project>")
        }
        repositories.maven(layout.buildDirectory.dir("gemrender-local")) { content { includeGroup("com.wf.gemrender") } }
        extra["golem.gemrender.local"] = "com.wf.gemrender:$artifact:$version"
        logger.lifecycle("GemRender $gemVersion for ${project.name} from $jar")
    }
}
