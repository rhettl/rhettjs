package com.rhett.rhettjs.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for LoggerAPI.
 * Tests the JavaScript logger.info(), logger.warn(), and logger.error() functionality.
 */
class LoggerAPITest {

    @Test
    fun `test logger info`() {
        val logger = LoggerAPI()

        assertDoesNotThrow {
            logger.info("Info message")
        }
    }

    @Test
    fun `test logger warn`() {
        val logger = LoggerAPI()

        assertDoesNotThrow {
            logger.warn("Warning message")
        }
    }

    @Test
    fun `test logger error without exception`() {
        val logger = LoggerAPI()

        assertDoesNotThrow {
            logger.error("Error message")
        }
    }

    @Test
    fun `test logger error with exception`() {
        val logger = LoggerAPI()
        val exception = RuntimeException("Test exception")

        assertDoesNotThrow {
            logger.error("Error message", exception)
        }
    }

    @Test
    fun `test logger error with null exception`() {
        val logger = LoggerAPI()

        assertDoesNotThrow {
            logger.error("Error message", null)
        }
    }

    @Test
    fun `test logger error with non-throwable object`() {
        val logger = LoggerAPI()

        // Should handle non-Throwable gracefully
        assertDoesNotThrow {
            logger.error("Error message", "Not a throwable")
        }
    }

    @Test
    fun `test logger with empty strings`() {
        val logger = LoggerAPI()

        assertDoesNotThrow {
            logger.info("")
            logger.warn("")
            logger.error("")
        }
    }

    @Test
    fun `test logger with multiline strings`() {
        val logger = LoggerAPI()

        assertDoesNotThrow {
            logger.info("Line 1\nLine 2\nLine 3")
            logger.warn("Multi\nline\nwarning")
        }
    }
}
