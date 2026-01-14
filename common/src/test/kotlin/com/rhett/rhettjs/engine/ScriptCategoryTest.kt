package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for ScriptCategory enum.
 * Tests that all expected script categories exist with correct configuration.
 */
class ScriptCategoryTest {

    @Test
    fun `test CLIENT category exists with correct dirName`() {
        val client = ScriptCategory.CLIENT
        assertEquals("client", client.dirName, "CLIENT category should have dirName 'client'")
    }

    @Test
    fun `test all categories have unique dirNames`() {
        val dirNames = ScriptCategory.entries.map { it.dirName }
        val uniqueDirNames = dirNames.toSet()

        assertEquals(dirNames.size, uniqueDirNames.size, "All categories should have unique dirNames")
    }

    @Test
    fun `test STARTUP category`() {
        assertEquals("startup", ScriptCategory.STARTUP.dirName)
    }

    @Test
    fun `test SERVER category`() {
        assertEquals("server", ScriptCategory.SERVER.dirName)
    }

    @Test
    fun `test UTILITY category`() {
        assertEquals("scripts", ScriptCategory.UTILITY.dirName)
    }

    @Test
    fun `test MODULES category`() {
        assertEquals("modules", ScriptCategory.MODULES.dirName)
    }

    @Test
    fun `test all expected categories exist`() {
        val categories = ScriptCategory.entries

        assertTrue(categories.contains(ScriptCategory.STARTUP), "STARTUP should exist")
        assertTrue(categories.contains(ScriptCategory.SERVER), "SERVER should exist")
        assertTrue(categories.contains(ScriptCategory.CLIENT), "CLIENT should exist")
        assertTrue(categories.contains(ScriptCategory.UTILITY), "UTILITY should exist")
        assertTrue(categories.contains(ScriptCategory.MODULES), "MODULES should exist")

        assertEquals(5, categories.size, "Should have exactly 5 categories")
    }
}
