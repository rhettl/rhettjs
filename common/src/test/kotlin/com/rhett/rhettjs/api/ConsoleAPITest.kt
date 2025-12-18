package com.rhett.rhettjs.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for ConsoleAPI.
 * Tests the JavaScript console.log(), console.warn(), and console.error() functionality.
 */
class ConsoleAPITest {

    @Test
    fun `test console log with single argument`() {
        val console = ConsoleAPI()

        // Should not throw exceptions
        assertDoesNotThrow {
            console.log("Test message")
        }
    }

    @Test
    fun `test console log with multiple arguments`() {
        val console = ConsoleAPI()

        assertDoesNotThrow {
            console.log("Test", "message", "with", "multiple", "args")
        }
    }

    @Test
    fun `test console log with mixed types`() {
        val console = ConsoleAPI()

        assertDoesNotThrow {
            console.log("String", 42, true, null, 3.14)
        }
    }

    @Test
    fun `test console warn`() {
        val console = ConsoleAPI()

        assertDoesNotThrow {
            console.warn("Warning message")
        }
    }

    @Test
    fun `test console error`() {
        val console = ConsoleAPI()

        assertDoesNotThrow {
            console.error("Error message")
        }
    }

    @Test
    fun `test console with empty args`() {
        val console = ConsoleAPI()

        assertDoesNotThrow {
            console.log()
            console.warn()
            console.error()
        }
    }

    @Test
    fun `test console with null arguments`() {
        val console = ConsoleAPI()

        assertDoesNotThrow {
            console.log(null, null)
            console.warn(null)
            console.error(null)
        }
    }
}