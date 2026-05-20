/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
@file:Suppress("LongMethod", "NestedBlockDepth", "MagicNumber", "MaxLineLength")

package org.meshtastic.flatpak

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

/**
 * Custom Gradle task to scan the local Gradle dependency cache and generate a complete flatpak-sources.json manifest
 * for offline builds.
 */
abstract class GenerateFlatpakSourcesTask : DefaultTask() {

    @get:Internal abstract val cacheDir: DirectoryProperty

    @get:OutputFile abstract val outputFile: RegularFileProperty

    init {
        group = "flatpak"
        description = "Generates a complete flatpak-sources.json manifest from the local Gradle cache directory."
        // Ensure the task always runs when executed
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun generate() {
        val cacheFolder = cacheDir.orNull?.asFile
        if (cacheFolder == null || !cacheFolder.exists()) {
            throw GradleException(
                "Gradle cache directory does not exist or is not configured correctly: ${cacheFolder?.absolutePath}. " +
                    "Please run a build first to populate the cache.",
            )
        }

        val outputSourcesFile = outputFile.get().asFile
        logger.lifecycle("Scanning Gradle cache directory: ${cacheFolder.absolutePath}")

        val allowedExtensions = setOf("jar", "aar", "pom", "module")
        val entries = mutableListOf<Map<String, Any>>()

        cacheFolder.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in allowedExtensions) {
                    val filename = file.name
                    if (!filename.endsWith("-sources.jar") && !filename.endsWith("-javadoc.jar")) {
                        val relativePath = file.relativeTo(cacheFolder).path.replace('\\', '/')
                        val parts = relativePath.split('/')
                        if (parts.size == 5) {
                            val group = parts[0]
                            val name = parts[1]
                            val version = parts[2]

                            val groupPath = group.replace('.', '/')

                            // Reconstruct correct Maven filename if Gradle cache renamed it locally (e.g. animation.aar
                            // -> animation-android-1.10.0.aar)
                            val standardPrefix = "$name-$version"
                            val serverFilename =
                                if (filename.startsWith(standardPrefix)) {
                                    filename
                                } else {
                                    "$name-$version.$ext"
                                }

                            val mavenPath = "$groupPath/$name/$version/$serverFilename"
                            val dest = "offline-repository/$groupPath/$name/$version"

                            val sha256 = calculateSha256(file)

                            val isJitpack = group.startsWith("com.github.")
                            val primaryUrl =
                                if (isJitpack) {
                                    "https://jitpack.io/$mavenPath"
                                } else {
                                    "https://repo.maven.apache.org/maven2/$mavenPath"
                                }

                            val mirrorUrls =
                                if (isJitpack) {
                                    listOf(
                                        "https://repo.maven.apache.org/maven2/$mavenPath",
                                        "https://maven-central.storage-download.googleapis.com/maven2/$mavenPath",
                                        "https://maven.aliyun.com/repository/public/$mavenPath",
                                    )
                                } else {
                                    listOf(
                                        "https://dl.google.com/dl/android/maven2/$mavenPath",
                                        "https://plugins.gradle.org/m2/$mavenPath",
                                        "https://maven-central.storage-download.googleapis.com/maven2/$mavenPath",
                                        "https://maven.aliyun.com/repository/public/$mavenPath",
                                    )
                                }

                            entries.add(
                                mapOf(
                                    "type" to "file",
                                    "url" to primaryUrl,
                                    "sha256" to sha256,
                                    "dest" to dest,
                                    "dest-filename" to serverFilename,
                                    "mirror-urls" to mirrorUrls,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Deduplicate and sort
        val deduplicated =
            entries
                .groupBy { "${it["dest"]}/${it["dest-filename"]}" }
                .map { (_, group) -> group.first() }
                .sortedBy { "${it["dest"]}/${it["dest-filename"]}" }

        outputSourcesFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(deduplicated)))
        logger.lifecycle(
            "Successfully scanned cache and generated ${outputSourcesFile.name} containing ${deduplicated.size} entries.",
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead = inputStream.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = inputStream.read(buffer)
            }
        }
        val bytes = digest.digest()
        val hexDigits = "0123456789abcdef"
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexDigits[v ushr 4]
            hexChars[i * 2 + 1] = hexDigits[v and 0x0F]
        }
        return String(hexChars)
    }
}
