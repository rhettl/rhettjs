package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for Runtime API exposed to JavaScript via GraalVM.
 * Tests Runtime.env properties and Runtime.exit() behavior.
 */
class RuntimeAPITest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager with temp directory
        ConfigManager.init(tempDir)
    }

    @Test
    fun `test Runtime env properties are accessible`() {
        val script = ScriptInfo(
            name = "test-runtime-env.js",
            path = createTempScript("""
                const tps = Runtime.env.TICKS_PER_SECOND;
                const debug = Runtime.env.IS_DEBUG;
                const version = Runtime.env.RJS_VERSION;

                console.log("TPS:", tps);
                console.log("Debug:", debug);
                console.log("Version:", version);

                if (tps !== 20) {
                    throw new Error("Expected TICKS_PER_SECOND to be 20, got " + tps);
                }

                if (typeof version !== "string") {
                    throw new Error("Expected RJS_VERSION to be a string");
                }
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Script should execute successfully")
    }

    @Test
    fun `test Runtime exit() terminates script execution`() {
        val script = ScriptInfo(
            name = "test-runtime-exit.js",
            path = createTempScript("""
                console.log("Before exit");
                Runtime.exit();
                console.log("After exit - should not print");
                throw new Error("Should never reach here");
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        // Exit should cause graceful termination, not an error
        assertTrue(result is ScriptResult.Success, "Runtime.exit() should terminate gracefully")
    }

    @Test
    fun `test Runtime setScriptTimeout with valid value`() {
        val script = ScriptInfo(
            name = "test-set-timeout.js",
            path = createTempScript("""
                Runtime.setScriptTimeout(120000); // 2 minutes
                console.log("Timeout set successfully");
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Should set timeout successfully")
    }

    @Test
    fun `test Runtime setScriptTimeout rejects values below 1000ms`() {
        val script = ScriptInfo(
            name = "test-timeout-validation.js",
            path = createTempScript("""
                try {
                    Runtime.setScriptTimeout(500); // Too small
                    throw new Error("Should have rejected timeout < 1000ms");
                } catch (e) {
                    if (e.message.indexOf("at least 1000ms") === -1) {
                        throw new Error("Wrong error message: " + e.message);
                    }
                    console.log("Correctly rejected small timeout");
                }
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Should validate timeout correctly")
    }

    @Test
    fun `test Runtime setScriptTimeout rejects negative values`() {
        val script = ScriptInfo(
            name = "test-negative-timeout.js",
            path = createTempScript("""
                try {
                    Runtime.setScriptTimeout(-5000);
                    throw new Error("Should have rejected negative timeout");
                } catch (e) {
                    if (e.message.indexOf("at least 1000ms") === -1) {
                        throw new Error("Wrong error message: " + e.message);
                    }
                    console.log("Correctly rejected negative timeout");
                }
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Should reject negative timeout")
    }

    @Test
    fun `test Runtime setScriptTimeout rejects zero`() {
        val script = ScriptInfo(
            name = "test-zero-timeout.js",
            path = createTempScript("""
                try {
                    Runtime.setScriptTimeout(0);
                    throw new Error("Should have rejected zero timeout");
                } catch (e) {
                    if (e.message.indexOf("at least 1000ms") === -1) {
                        throw new Error("Wrong error message: " + e.message);
                    }
                    console.log("Correctly rejected zero timeout");
                }
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Should reject zero timeout")
    }

    @Test
    fun `test multiple Runtime env property accesses`() {
        val script = ScriptInfo(
            name = "test-multi-access.js",
            path = createTempScript("""
                // Access each property multiple times
                for (let i = 0; i < 10; i++) {
                    const tps = Runtime.env.TICKS_PER_SECOND;
                    const debug = Runtime.env.IS_DEBUG;
                    const version = Runtime.env.RJS_VERSION;

                    if (tps !== 20) throw new Error("TPS should always be 20");
                    if (typeof debug !== "boolean") throw new Error("Debug should be boolean");
                    if (typeof version !== "string") throw new Error("Version should be string");
                }

                console.log("Multiple accesses work correctly");
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Should handle multiple property accesses")
    }

    @Test
    fun `test Runtime API is available in all script categories`() {
        // Test in STARTUP category
        val startupScript = ScriptInfo(
            name = "test-startup-runtime.js",
            path = createTempScript("""
                console.log("Runtime.env.RJS_VERSION:", Runtime.env.RJS_VERSION);
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val startupResult = GraalEngine.executeScript(startupScript)
        assertTrue(startupResult is ScriptResult.Success, "Runtime should work in STARTUP")

        // Test in SERVER category
        val serverScript = ScriptInfo(
            name = "test-server-runtime.js",
            path = createTempScript("""
                console.log("Runtime.env.TICKS_PER_SECOND:", Runtime.env.TICKS_PER_SECOND);
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val serverResult = GraalEngine.executeScript(serverScript)
        assertTrue(serverResult is ScriptResult.Success, "Runtime should work in SERVER")
    }

    @Test
    fun `test Runtime env values are correct types`() {
        val script = ScriptInfo(
            name = "test-env-types.js",
            path = createTempScript("""
                const env = Runtime.env;

                if (typeof env.TICKS_PER_SECOND !== "number") {
                    throw new Error("TICKS_PER_SECOND must be a number");
                }

                if (typeof env.IS_DEBUG !== "boolean") {
                    throw new Error("IS_DEBUG must be a boolean");
                }

                if (typeof env.RJS_VERSION !== "string") {
                    throw new Error("RJS_VERSION must be a string");
                }

                console.log("All Runtime.env types are correct");
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "All env properties should have correct types")
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }
}