package com.atvriders.wsprtxrx

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The APK redistributes BSD-2-Clause (MapLibre) and Apache-2.0 (OkHttp, AndroidX/Compose,
 * Room, DataStore, kotlinx) code, and both licences condition binary redistribution on
 * reproducing their notices. `res/raw/third_party_licences.txt` is what discharges that,
 * shown in-app under Settings → About & legal.
 *
 * A notice file goes stale the moment someone adds a dependency, so this asserts that
 * every library actually declared in the version catalogue is named in it.
 */
class ThirdPartyLicencesTest {

    /** Walks up from the test working directory until the repo layout is recognisable. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "gradle/libs.versions.toml").isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError("could not locate gradle/libs.versions.toml from ${System.getProperty("user.dir")}")
    }

    @Test
    fun everyDeclaredLibraryIsNamedInTheNoticeFile() {
        val root = repoRoot()
        val catalogue = File(root, "gradle/libs.versions.toml").readText()
        val notices = File(root, "app/src/main/res/raw/third_party_licences.txt")
        assertTrue("notice file is missing: $notices", notices.isFile)
        val text = notices.readText()

        // group = "x", name = "y"  ->  the artifact coordinate we expect to see credited.
        val entry = Regex("""group\s*=\s*"([^"]+)"\s*,\s*name\s*=\s*"([^"]+)"""")
        val missing = entry.findAll(catalogue)
            .map { it.groupValues[1] to it.groupValues[2] }
            // JUnit is test-only and is not redistributed in the APK.
            .filterNot { (group, _) -> group == "junit" }
            .filterNot { (_, name) -> name.startsWith("kotlinx-coroutines-test") }
            .filterNot { (group, name) -> "$group:$name" in TEST_ONLY }
            .filterNot { (group, name) -> text.contains("$group:$name") || text.contains(name) }
            .map { (group, name) -> "$group:$name" }
            .toList()

        assertTrue(
            "these dependencies ship in the APK but are not credited in " +
                "res/raw/third_party_licences.txt: $missing",
            missing.isEmpty(),
        )
    }

    private companion object {
        val TEST_ONLY = setOf("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    }
}
