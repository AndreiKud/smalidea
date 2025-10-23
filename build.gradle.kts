/*
 * Copyright 2025, Google Inc.
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
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("antlr")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.10.1"
}

group = "org.jf"
version = "0.08"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    google()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.4")
        // androidStudio("2025.1.4.8")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    implementation("com.android.tools.smali:smali:3.0.9")
    implementation("com.android.tools.smali:smali-util:3.0.9")
    implementation("com.android.tools.smali:smali-dexlib2:3.0.9")

    implementation("org.antlr:antlr-runtime:3.5.2")
    implementation("com.google.code.gson:gson:2.8.9")
    antlr("org.antlr:antlr:3.5.3")

    testImplementation("junit:junit:4.13.2")

    // TODO: move from javax.annotations to org.jetbrains.annotations
    implementation("com.github.spotbugs:spotbugs-annotations:4.9.7")
}

val extractTokensTask = tasks.register<Copy>("extractTokens") {
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

tasks.generateGrammarSource.configure {
    val tokensDir = layout.buildDirectory.dir("tokens/com/android/tools/smali/smali/")
    arguments = arguments + listOf("-lib", tokensDir.get().asFile.path)
    outputDirectory = layout.buildDirectory.dir("generated-src/antlr/main/org/jf/smalidea/").get().asFile
    dependsOn(extractTokensTask)
}

tasks.patchPluginXml.configure {
    sinceBuild = provider { null }
}
