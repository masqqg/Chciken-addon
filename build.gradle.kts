buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.guardsquare:proguard-gradle:7.7.0") }
}

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

tasks {
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

// =====================================================================
// Obfuscation pipeline: jar -> ProGuard (renames) -> Skidfuscator (flow +
// string/number encryption) -> remapped to the real Minecraft names so
// the game can load it.
// =====================================================================

val proguardOut = layout.buildDirectory.file("proguard/obfuscated.jar")

val proguardObfuscate by tasks.registering(proguard.gradle.ProGuardTask::class) {
    dependsOn(tasks.jar)
    injars(tasks.jar.flatMap { it.archiveFile })
    outjars(proguardOut)
    libraryjars(configurations.runtimeClasspath)
    libraryjars(
        mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
        "${System.getProperty("java.home")}/jmods/java.base.jmod"
    )
    configuration("proguard-rules.pro")
}

val skidJar = layout.projectDirectory.file("skidfuscator.jar")
val skidConfig = layout.projectDirectory.file("skidfuscator.hocon")
val skidLibs = layout.buildDirectory.dir("skid-libs")
val skidOut = layout.buildDirectory.file("skid/obfuscated.jar")

// Java binary used to RUN skidfuscator.jar. Override with
// -PskidJavaHome=/path/to/java21 if the default JDK on the runner doesn't work.
val skidJavaBin = providers.gradleProperty("skidJavaHome")
    .map { "$it/bin/java" }
    .orElse("java")

val prepareSkidLibs by tasks.registering(Copy::class) {
    from(configurations.compileClasspath)
    into(skidLibs)
}

val skidfuscate by tasks.registering(Exec::class) {
    dependsOn(proguardObfuscate, prepareSkidLibs)
    inputs.file(proguardOut)
    inputs.file(skidConfig)
    outputs.file(skidOut)
    notCompatibleWithConfigurationCache("runs an external jar with paths resolved at execution time")

    doFirst {
        skidOut.get().asFile.parentFile.mkdirs()
        commandLine(
            skidJavaBin.get(), "-jar", skidJar.asFile.absolutePath,
            "obfuscate", proguardOut.get().asFile.absolutePath,
            "--output", skidOut.get().asFile.absolutePath,
            "--config", skidConfig.asFile.absolutePath,
            "--libs", skidLibs.get().asFile.absolutePath
        )
    }
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    dependsOn(skidfuscate)
    inputFile.set(skidOut)
}
