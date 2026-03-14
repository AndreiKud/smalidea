/*
 * Copyright 2025, Google Inc.
 * Copyright 2026, Andrei Kudryavtsev (andreikudrya1995@gmail.com).
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import org.gradle.kotlin.dsl.testImplementation
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

plugins {
    id("java")
    id("antlr")
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

kotlin {
    jvmToolchain(21)
}
tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(17)
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginVerification {
        ides {
            create {
                type = IntelliJPlatformType.IntellijIdea
                version = "261.22158.46" // 2026.1 eap, March 5, 2026
            }
            create {
                type = IntelliJPlatformType.IntellijIdeaCommunity
                version = "2025.1" // April 14, 2025
            }
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.2.5")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    implementation("com.android.tools.smali:smali:3.0.9")
    implementation("com.android.tools.smali:smali-util:3.0.9")

    implementation("org.antlr:antlr-runtime:3.5.2")
    implementation("com.google.code.gson:gson:2.8.9")
    antlr("org.antlr:antlr:3.5.3")

    testImplementation("junit:junit:4.13.2")

    // TODO: move from javax.annotations to org.jetbrains.annotations
    implementation("com.github.spotbugs:spotbugs-annotations:4.9.7")
}

fun JavaExec.setupIdeExec() {
    // For testing purposes
    val smaliProjectDirPath: String? = localProperties.getProperty("smali.project.dir")
    if (!smaliProjectDirPath.isNullOrBlank()) {
        argumentProviders += CommandLineArgumentProvider { listOf(
            smaliProjectDirPath,
        ) }
    }
}

tasks {
    runIde {
        setupIdeExec()
    }

    // https://youtrack.jetbrains.com/articles/IDEA-A-21/IDEA-Latest-Builds-And-Release-Notes
    intellijPlatformTesting.runIde.register("runIntellijIdea") {
        task {
            setupIdeExec()
            jvmArgumentProviders += CommandLineArgumentProvider { listOf(
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8706",
            ) }
        }
        // type = IntelliJPlatformType.IntellijIdea
        // version = "261.22158.46" // 2026.1 eap, March 5, 2026
        type = IntelliJPlatformType.IntellijIdeaCommunity
        version = "2025.1" // 15 Apr 2025
    }

    // https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html
    intellijPlatformTesting.runIde.register("runAndroidStudio") {
        task {
            setupIdeExec()
            jvmArgumentProviders += CommandLineArgumentProvider { listOf(
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8706",
            ) }
        }
        type = IntelliJPlatformType.AndroidStudio
        // version = "2025.3.3.2" // Panda 3 Canary 2, Feb 26, 2026
        version = "2025.1.2.11" // Narwhal Feature Drop, Jul 31, 2025
    }

    val extractTokensTask = register<Copy>("extractTokens") {
        val smaliZip = providers.provider {
            val artifacts = configurations.runtimeClasspath.flatMap {
                it.incoming.artifacts.resolvedArtifacts
            }.get()
            artifacts.find { it.id.displayName.contains(":smali") }?.file
                ?: throw GradleException("Could not find 'smali' artifact in 'default' configuration")
        }

        from(smaliZip.map { zipTree(it) }) {
            include("**/*.tokens")
        }
        into(layout.buildDirectory.dir("tokens"))
    }

    generateGrammarSource.configure {
        val tokensDir = layout.buildDirectory.dir("tokens/com/android/tools/smali/smali/")
        arguments = arguments + listOf("-lib", tokensDir.get().asFile.path)
        outputDirectory = layout.buildDirectory.dir("generated-src/antlr/main/dev/resmali/").get().asFile
        dependsOn(extractTokensTask)
    }
}
