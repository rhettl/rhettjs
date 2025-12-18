package com.rhett.rhettjs

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class RhettJSCommonTest {
    @Test
    fun testModId() {
        assertEquals("rhettjs", RhettJSCommon.MOD_ID)
    }
}
