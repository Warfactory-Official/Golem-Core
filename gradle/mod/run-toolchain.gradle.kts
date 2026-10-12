import org.gradle.jvm.toolchain.JavaToolchainService

// Shared by every Golem Core mod. Apply it from each loader buildscript:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/run-toolchain.gradle.kts"))
//
// Parameters. Every shared build-layer script under Golem-Core/gradle/mod reads its settings as project
// properties named `golem.build.<script>.<name>`, looked up with findProperty, so they can be set either
// as `extra["golem.build..."] = value` in the buildscript BEFORE the apply line or in gradle.properties.
//
//   golem.build.runToolchain.clientFlags  Gradle property name to JVM system property name, as a Map or as
//                                         a comma-separated String of `gradleName=systemName` pairs. Each
//                                         flag given as `-P<gradle name>` or `-D<system name>` is forwarded
//                                         to runClient as `-D<system name>` (a blank value becomes "true").
//                                         Default: none
//
// Node facts it also reads, which the loader buildscript sets as extra properties before applying:
//   java_version  the node's Java major version (required)

/**
 * Forces every game-launching `JavaExec` in this node to run on the node's declared Java toolchain.
 *
 * Gradle's own run tasks already do this. IntelliJ's "run main() through Gradle" action does not: it
 * generates a throwaway init script that creates its own `JavaExec` and hardcodes
 * `executable = <module SDK>/bin/java.exe`. The module SDK is inherited from the *project* SDK, so every
 * node gets the same JVM regardless of the toolchain the node declares — and in a multi-version project
 * no single JVM is correct for all of them.
 *
 * That is not a cosmetic mismatch. Minecraft 1.21.1 must run on Java 21: NeoForge 21.1.80 bundles Mixin
 * 0.8.7, whose ASM cannot read class-file major 69, so on Java 25 every JDK class it inspects fails to
 * load and mixin application dies with `ClassNotFoundException: java.lang.Boolean` — a message that
 * points nowhere near the actual problem. (Fabric survives the same mismatch only because its Mixin
 * ships a newer ASM. That is luck, not support.)
 *
 * Setting `executable` from `doFirst` rather than at configuration time is deliberate: it runs after the
 * init script's own configuration closure, so the node's toolchain wins no matter who configured what.
 */
fun Project.pinRunsToToolchain(javaVersion: Int) {
    val toolchains = extensions.getByType(JavaToolchainService::class.java)
    val launcher = toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(javaVersion)) }
    // Gradle JVM properties are not inherited by the Minecraft process. Keep each opt-in neutral so
    // `-D<system name>=true` and `-P<gradle name>=true` reach only the client run that consumes it.
    val clientFlagNames: Map<String, String> = when (val value = findProperty("golem.build.runToolchain.clientFlags")) {
        null -> emptyMap()
        is Map<*, *> -> value.entries.associate { it.key.toString() to it.value.toString() }
        else -> value.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }
    val clientFlags = clientFlagNames.map { (gradleName, systemName) ->
        systemName to providers.gradleProperty(gradleName)
            .orElse(providers.systemProperty(systemName))
            .map { if (it.isBlank()) "true" else it }
    }

    tasks.withType(JavaExec::class.java).configureEach {
        // `this` is the JavaExec; inside doFirst the receiver is a plain Task, so hold on to it.
        val run = this
        // Both must be set, and consistently: Gradle rejects a task whose `executable` and
        // `javaLauncher` disagree. javaLauncher is a Property finalized before execution, so it is set
        // here at configuration time; executable is set in doFirst so it lands after whatever the IDE's
        // generated script configured.
        javaLauncher.set(launcher)
        if (name == "runClient") {
            clientFlags.forEach { (systemName, flag) ->
                flag.orNull?.let {
                    systemProperty(systemName, it)
                    logger.lifecycle("Forwarding $systemName=$it to $path")
                }
            }
        }
        doFirst {
            val wanted = launcher.get().executablePath.asFile.absolutePath
            val configured = run.executable
            if (configured != null && configured != wanted) {
                logger.lifecycle(
                    "Launching on the Java $javaVersion toolchain instead of $configured — " +
                        "this node's Minecraft cannot run on another major version.")
            }
            // setExecutable, not the property: the setter takes Object, so Kotlin sees only a getter.
            run.setExecutable(wanted)
        }
    }
}

pinRunsToToolchain((extra.properties["java_version"] as String).toInt())
