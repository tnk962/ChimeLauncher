package com.myenvironment.launcher

import com.myenvironment.launcher.core.update.AppUpdateParser
import com.myenvironment.launcher.core.update.AppUpdateState
import com.myenvironment.launcher.core.update.ReleaseUpdateInfo
import org.junit.Assert.*
import org.junit.Test

class UpdateEligibilityTest {
    private fun release(version: String, url: String? = "https://example.com/launcher.apk") = ReleaseUpdateInfo(
        "v$version", version, "Chime Launcher", "", "https://example.com/release", url,
        "ChimeLauncher-v$version.apk", 100L, "2026-10-03"
    )

    @Test fun `installed 133 never offers published 132 for reinstall`() {
        val published = release("1.3.2")
        val state = AppUpdateParser.stateForRelease("1.3.3", published, "20:00")
        assertTrue(state is AppUpdateState.InstalledAhead)
        assertFalse(AppUpdateParser.canInstallRelease("1.3.3", published))
        assertEquals("v1.3.2", (state as AppUpdateState.InstalledAhead).latestRelease.tagName)
    }
    @Test fun `new public release is an installable update from both existing versions`() {
        for (current in listOf("1.3.2", "1.3.3")) {
            assertTrue(AppUpdateParser.stateForRelease(current, release("1.3.4"), "20:00") is AppUpdateState.UpdateAvailable)
            assertTrue(AppUpdateParser.canInstallRelease(current, release("1.3.4")))
        }
    }
    @Test fun `same version permits reinstall and ignores build metadata`() {
        assertTrue(AppUpdateParser.stateForRelease("1.3.4", release("1.3.4"), "20:00") is AppUpdateState.UpToDate)
        assertTrue(AppUpdateParser.canInstallRelease("1.3.4+local", release("1.3.4")))
    }
    @Test fun `new release without usable launcher remains pending instead of downloadable`() {
        for (url in listOf(null, "", " ")) {
            assertTrue(AppUpdateParser.stateForRelease("1.3.3", release("1.3.4", url), "20:00") is AppUpdateState.ReleaseUnavailable)
            assertFalse(AppUpdateParser.canInstallRelease("1.3.3", release("1.3.4", url)))
        }
    }
    @Test fun `malformed versions never offer downloads`() {
        for (candidate in listOf("1.bad.4", "1..4", "release-134", "1.3.4/old", "999999999999999.0", "")) {
            assertFalse(AppUpdateParser.canInstallRelease("1.3.3", release(candidate)))
            assertTrue(AppUpdateParser.stateForRelease("1.3.3", release(candidate), "20:00") is AppUpdateState.Error)
            assertTrue(AppUpdateParser.parseVersionParts(candidate).isEmpty())
        }
    }
    @Test fun `numeric versions compare numerically and normalize missing zero components`() {
        assertTrue(AppUpdateParser.isNewerVersion("1.3.9", "v1.3.10"))
        assertTrue(AppUpdateParser.isNewerVersion("1.9.9", "v2.0.0"))
        assertEquals(0, AppUpdateParser.compareVersions("v1.3", "1.3.0"))
        assertNull(AppUpdateParser.compareVersions("1.invalid.3", "1.3.0"))
    }
    @Test fun `previews are ordered numerically before stable releases`() {
        assertTrue(AppUpdateParser.isNewerVersion("1.3.4-preview.2", "v1.3.4-preview.10"))
        assertFalse(AppUpdateParser.isNewerVersion("1.3.4-preview.10", "v1.3.4-preview.2"))
        assertTrue(AppUpdateParser.isNewerVersion("1.3.4-preview.10", "v1.3.4"))
        assertFalse(AppUpdateParser.canInstallRelease("1.3.4", release("1.3.4-preview.10")))
    }
    @Test fun `same named apk with lower build code cannot install`() {
        assertFalse(AppUpdateParser.canInstallApk("1.3.4", 20, "1.3.4", 19))
        assertTrue(AppUpdateParser.canInstallApk("1.3.4", 20, "1.3.4", 20))
        assertTrue(AppUpdateParser.canInstallApk("1.3.3", 19, "1.3.4", 20))
    }
    @Test fun `a higher code cannot make a lower semantic version installable`() {
        assertFalse(AppUpdateParser.canInstallApk("1.3.4", 20, "1.3.3", 21))
        assertFalse(AppUpdateParser.canInstallApk("1.3.4", 20, null, 21))
    }
    @Test fun `companion fresh install permits current release but newer installed companion stays intact`() {
        assertTrue(AppUpdateParser.canInstallApk(null, null, "1.3.4", 8))
        assertFalse(AppUpdateParser.canInstallApk("1.3.5", 9, "1.3.4", 8))
        assertFalse(AppUpdateParser.canInstallApk(null, null, "not-a-version", 8))
    }
    @Test fun `tag matching launcher is selected over earlier apk in same release`() {
        val parsed = AppUpdateParser.parseLatestReleaseJson("""{
          "tag_name":"v1.3.4","assets":[
            {"name":"ChimeLauncher-v1.3.2.apk","browser_download_url":"https://example.com/old.apk"},
            {"name":"ChimeLauncher-v1.3.4-debug.apk","browser_download_url":"https://example.com/debug.apk"},
            {"name":"ChimeLauncher-v1.3.4.apk","browser_download_url":"https://example.com/new.apk"},
            {"name":"ChimeDiscoverCompanion-v1.3.2.apk","browser_download_url":"https://example.com/old-companion.apk"},
            {"name":"ChimeDiscoverCompanion-v1.3.4.apk","browser_download_url":"https://example.com/new-companion.apk"}
          ]
        }""")!!
        assertEquals("https://example.com/new.apk", parsed.apkDownloadUrl)
        assertEquals("https://example.com/new-companion.apk", parsed.companionDownloadUrl)
    }
    @Test fun `old assets on new tag do not offer downloads`() {
        val parsed = AppUpdateParser.parseLatestReleaseJson("""{
          "tag_name":"v1.3.4","assets":[
            {"name":"ChimeLauncher-v1.3.2.apk","browser_download_url":"https://example.com/old.apk"}
          ]
        }""")!!
        assertNull(parsed.apkDownloadUrl)
        assertTrue(AppUpdateParser.stateForRelease("1.3.3", parsed, "20:00") is AppUpdateState.ReleaseUnavailable)
    }
    @Test fun `release assets need a real download url`() {
        val parsed = AppUpdateParser.parseLatestReleaseJson("""{
          "tag_name":"v1.3.4","assets":[{"name":"ChimeLauncher-v1.3.4.apk","browser_download_url":" "}]
        }""")!!
        assertNull(parsed.apkDownloadUrl)
    }
    @Test fun `draft and prerelease responses cannot masquerade as latest stable`() {
        for (field in listOf("draft", "prerelease")) {
            assertNull(AppUpdateParser.parseLatestReleaseJson("""{"tag_name":"v1.3.4","$field":true,"assets":[]}"""))
        }
    }
    @Test fun `malformed tag response is rejected`() {
        assertNull(AppUpdateParser.parseLatestReleaseJson("""{"tag_name":"v1.bad.4","assets":[]}"""))
    }
    @Test fun `inconsistent release tag cannot advertise an installable package`() {
        val inconsistent = release("1.3.4").copy(tagName = "v1.3.5")
        assertFalse(AppUpdateParser.canInstallRelease("1.3.3", inconsistent))
        assertTrue(AppUpdateParser.stateForRelease("1.3.3", inconsistent, "20:00") is AppUpdateState.Error)
    }
    @Test fun `installed code still prevents downgrade when installed version name is absent`() {
        assertFalse(AppUpdateParser.canInstallApk(null, 20, "1.3.4", 19))
        assertTrue(AppUpdateParser.canInstallApk(null, 20, "1.3.4", 20))
    }

}
