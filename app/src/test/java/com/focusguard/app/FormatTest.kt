package com.focusguard.app

import com.focusguard.app.core.FocusSessionService
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun formatsMinutesSeconds() {
        assertEquals("25:00", FocusSessionService.format(1500))
        assertEquals("00:05", FocusSessionService.format(5))
    }
    @Test fun formatsHours() {
        assertEquals("1:01:01", FocusSessionService.format(3661))
    }
}
