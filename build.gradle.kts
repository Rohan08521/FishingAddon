import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("jvm")
  id("fabric-loom")
  `maven-publish`
  java
}

val baseGroup: String by project
val lwjglVersion: String by project
val addonVersion: String by project
val addonName: String by project

base {
  archivesName = addonName
  version = addonVersion
  group = baseGroup
}

repositories {
  mavenCentral()
  maven("https://maven.meteordev.org/releases")
  maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
  maven("https://jitpack.io/")
}

dependencies {
  minecraft("com.mojang:minecraft:${property("minecraft_version")}")
  //mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
  mappings(loom.officialMojangMappings())
  modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

  modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
  modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

  modImplementation("com.github.CobaltScripts:Cobalt:1.0.0.945a71f")
  modImplementation("meteordevelopment:discord-ipc:1.1")
  modImplementation("org.reflections:reflections:0.10.2")

  modImplementation("org.lwjgl:lwjgl-nanovg:${lwjglVersion}")
  listOf("windows", "linux", "macos", "macos-arm64").forEach {
    modImplementation("org.lwjgl:lwjgl-nanovg:${lwjglVersion}:natives-$it")
  }
  runtimeOnly("org.apache.httpcomponents:httpclient:4.5.14")
  modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")
}

tasks {
  processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
      expand(getProperties())
      expand(mutableMapOf("version" to project.version))
    }
  }

  publishing {
    publications {
      create<MavenPublication>("mavenJava") {
        artifact(remapJar) {
          builtBy(remapJar)
        }

        artifact(kotlinSourcesJar) {
          builtBy(remapSourcesJar)
        }
      }
    }
  }

  compileKotlin {
    compilerOptions {
      jvmTarget = JvmTarget.JVM_21
    }
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}
