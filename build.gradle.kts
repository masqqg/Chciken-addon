plugins {
    alias(libs.plugins.fabric.loom)
}

val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val mavenGroup = providers.gradleProperty("maven_group").get()

base {
    archivesName = archivesBaseName
    version = libs.versions.mod.version.get()
    group = mavenGroup
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)

    // Meteor
    modImplementation(libs.meteor.client)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

fun toMinecraftCompat(version: String): String {
    val stable = Regex("""^(\d{2})\.([1-9]\d*)(?:\.(\d+))?$""")

    stable.matchEntire(version)?.let {
        val (year, drop, _) = it.destructured
        return "~$year.$drop"
    }

    val pre = Regex("""^(\d{2})\.([1-9]\d*)-pre[-.](\d+)$""")
    pre.matchEntire(version)?.let {
        return version.replace("-pre-", "-pre.")
    }

    val rc = Regex("""^(\d{2})\.([1-9]\d*)-rc[-.](\d+)$""")
    rc.matchEntire(version)?.let {
        return version.replace("-rc-", "-rc.")
    }

    return version
}

val protectionClasspath = configurations.compileClasspath

tasks {
    // Writes the compile classpath for protection/pipeline.sh, which passes the
    // jars to Umbra as --lib so its output verifier can load every class.
    register("protectionLibs") {
        group = "build"
        description = "Writes the jar classpath used by the protection pipeline."
        // The remapped mod jars live in build/loom-cache only after remapJar ran.
        dependsOn("remapJar")

        val outFile = layout.buildDirectory.file("protection-libs.txt")
        outputs.file(outFile)

        doLast {
            // The release jar is remapped to intermediary, so the verifier needs
            // the intermediary Minecraft jar and the remapped mod jars as well.
            val extra = fileTree(gradle.gradleUserHomeDir.resolve("caches/fabric-loom/minecraftMaven")) {
                include("**/minecraft-merged-intermediary-*.jar")
            }.files + fileTree(layout.buildDirectory.dir("loom-cache/remapped_working")) {
                include("*.jar")
            }.files

            val jars = (protectionClasspath.get().files + extra)
                .filter { it.isFile && it.name.endsWith(".jar") }
                .distinct()
                .joinToString("\n") { it.absolutePath }

            val file = outFile.get().asFile
            file.parentFile.mkdirs()
            file.writeText(jars)
        }
    }

    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to toMinecraftCompat(libs.versions.minecraft.get()),
            "jdk_version" to libs.versions.jdk.get(),
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", archivesBaseName)
        val licenseName = "LICENSE_$archivesBaseName"

        from("LICENSE") {
            rename("LICENSE", licenseName)
        }
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }
}
