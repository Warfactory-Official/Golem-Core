import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

dependencies {
    implementation("org.gradle.toolchains:foojay-resolver:1.0.0")
    implementation("dev.kikugie:stonecutter:0.9.8")
    implementation("dev.kikugie:loom-back-compat:0.4.2")
    implementation("net.neoforged:moddev-gradle:2.0.141")
}

gradlePlugin {
    plugins {
        register("settings") {
            id = "dev.golem.settings"
            implementationClass = "dev.golem.build.GolemSettingsPlugin"
        }
        register("stonecutter") {
            id = "dev.golem.stonecutter"
            implementationClass = "dev.golem.build.GolemControllerPlugin"
        }
        register("node") {
            id = "dev.golem.node"
            implementationClass = "dev.golem.build.GolemNodePlugin"
        }
        register("mod") {
            id = "dev.golem.mod"
            implementationClass = "dev.golem.build.GolemModPlugin"
        }
    }
}
