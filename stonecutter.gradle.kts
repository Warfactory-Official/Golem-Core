plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1-neoforge" /* [SC] DO NOT EDIT */

// Builds every node in one invocation. Stonecutter 0.9 replaced the old `registerChiseled` API with
// task aggregation: `stonecutter.tasks.named` returns a lazy collection of that task across all nodes.
tasks.register("chiseledBuild") {
    group = "build"
    description = "Builds every registered version/loader node."
    dependsOn(stonecutter.tasks.named("build"))
}

stonecutter parameters {
    val loader = node.metadata.project.substringAfterLast('-')

    // Loader constants, used as `//? if fabric {` / `//? if forgelike {`.
    constants["fabric"] = loader == "fabric"
    constants["neoforge"] = loader == "neoforge"
    constants["forge"] = loader == "forge"
    constants["forgelike"] = loader == "neoforge" || loader == "forge"

    // Version comparisons use the node's logical version (26.1, 1.21.1, ...), so `//? if >=1.21.11`
    // works regardless of the loader suffix in the node name.
    // True only where the node declares JEI coordinates, so the integration compiles out elsewhere.
    constants["jei"] = !(node.project.findProperty("deps.jei") as String?).isNullOrBlank()

    swaps["mc_version"] = "\"${node.metadata.version}\";"
}
