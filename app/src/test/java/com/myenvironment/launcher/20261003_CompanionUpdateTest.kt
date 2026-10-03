package com.myenvironment.launcher

import com.myenvironment.launcher.core.update.AppUpdateParser
import org.junit.Assert.*
import org.junit.Test

class CompanionUpdateTest {
    @Test fun `both companion names are selected separately from launcher regardless of order`() {
        for (prefix in listOf("GoogleDiscoverCompanion", "ChimeDiscoverCompanion")) {
            val release = AppUpdateParser.parseLatestReleaseJson("""{
                "tag_name":"v1.3.2","assets":[
                    {"name":"$prefix-v1.3.2-debug.apk","browser_download_url":"https://example.com/debug.apk","size":10},
                    {"name":"$prefix-v1.3.2.apk","browser_download_url":"https://example.com/companion.apk","size":20},
                    {"name":"ChimeLauncher-v1.3.2.apk","browser_download_url":"https://example.com/launcher.apk","size":30}
                ]
            }""")!!
            assertEquals("https://example.com/launcher.apk", release.apkDownloadUrl)
            assertEquals("https://example.com/companion.apk", release.companionDownloadUrl)
            assertEquals("$prefix-v1.3.2.apk", release.companionFileName)
            assertEquals(20L, release.companionSizeBytes)
            assertEquals(30L, release.apkSizeBytes)
        }
    }

    @Test fun `old launcher only release remains installable without companion`() {
        val release = AppUpdateParser.parseLatestReleaseJson("""{
            "tag_name":"v1.1.0","assets":[
                {"name":"ChimeLauncher-v1.1.0.apk","browser_download_url":"https://example.com/launcher.apk"},
                {"name":"OtherCompanion-v1.1.0.apk","browser_download_url":"https://example.com/other.apk"}
            ]
        }""")!!
        assertNotNull(release.apkDownloadUrl)
        assertNull(release.companionDownloadUrl)
        assertEquals(0L, release.companionSizeBytes)
    }

    @Test fun `companion only release cannot replace launcher`() {
        val release = AppUpdateParser.parseLatestReleaseJson("""{
            "tag_name":"v1.3.2","assets":[
                {"name":"GoogleDiscoverCompanion-v1.3.2.apk","browser_download_url":"https://example.com/companion.apk"}
            ]
        }""")!!
        assertNull(release.apkDownloadUrl)
        assertNotNull(release.companionDownloadUrl)
    }
}
