package com.nuvio.app.features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForkBuildsTest {
    @Test
    fun readsTheCommitStampFromForkTags() {
        assertEquals(202610311402L, ForkBuilds.stamp("shad-20261031-1402-9670785d"))
        assertNull(ForkBuilds.stamp("v0.6.4"))
        assertNull(ForkBuilds.stamp("shad-2026-x"))
    }

    @Test
    fun aNewerCommitIsAnUpdate() {
        assertEquals(true, ForkBuilds.isRemoteNewer("shad-20261031-1402-abc1234", localBuild = "202610311300"))
        assertEquals(false, ForkBuilds.isRemoteNewer("shad-20261031-1402-abc1234", localBuild = "202610311402"))
        assertEquals(false, ForkBuilds.isRemoteNewer("shad-20261031-1402-abc1234", localBuild = "202611010900"))
        assertNull(ForkBuilds.isRemoteNewer("v0.7.0", localBuild = "202610311300"))
    }

    @Test
    fun theLatestForkReleaseIsOfferedFromTheGitHubResponse() {
        val body = """
            {"tag_name":"shad-20261031-1428-e7cdbb5b","name":"Nuvio 0.6.3 (2026-10-31)","draft":false,
             "prerelease":false,"assets":[{"name":"Nuvio.apk",
             "browser_download_url":"https://github.com/x/y/releases/download/t/Nuvio.apk","size":1}]}
        """.trimIndent()
        val update = AppUpdaterRepository.selectUpdate(body, UpdateChannel.STABLE, listOf("arm64-v8a"))
        assertEquals("shad-20261031-1428-e7cdbb5b", update?.tag)
        assertEquals("Nuvio.apk", update?.assetName)
        assertEquals(true, ForkBuilds.isRemoteNewer(update!!.tag, localBuild = "202610311414"))
    }
}
