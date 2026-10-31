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
}
