package com.myenvironment.launcher

import com.myenvironment.launcher.ui.keepPageOnHomeReturn
import org.junit.Assert.*
import org.junit.Test

class HomeReturnPolicyTest {
    @Test fun closingSearchPreservesPageWithoutAppLaunch() { assertTrue(keepPageOnHomeReturn(false, true)) }
    @Test fun appReturnStillPreservesPage() { assertTrue(keepPageOnHomeReturn(true, false)) }
    @Test fun homeOnLauncherStillNavigatesHome() { assertFalse(keepPageOnHomeReturn(false, false)) }
}
