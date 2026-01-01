package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for Script.argv parsing in utility scripts.
 * Tests argument parsing with flags, positional args, and helper methods.
 */
class ScriptArgvTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    @Test
    fun `test Script argv is accessible in utility script`() {
        val script = ScriptInfo(
            name = "test-argv-exists.js",
            path = createTempScript("""
                if (typeof Script === 'undefined') {
                    throw new Error('Script should be defined in utility scripts');
                }

                if (typeof Script.argv === 'undefined') {
                    throw new Error('Script.argv should be defined');
                }

                console.log('Script.argv exists');
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val args = listOf("arg1", "arg2", "--flag")
        val result = GraalEngine.executeScript(script, mapOf("Args" to args))
        assertTrue(result is ScriptResult.Success, "Script.argv should be accessible in utility scripts")
    }

    @Test
    fun `test Script argv parses positional arguments`() {
        val script = ScriptInfo(
            name = "test-positional-args.js",
            path = createTempScript("""
                // Test positional arguments
                const arg1 = Script.argv.get(0);
                const arg2 = Script.argv.get(1);

                if (arg1 !== 'player1') {
                    throw new Error('First positional arg should be player1, got: ' + arg1);
                }

                if (arg2 !== 'player2') {
                    throw new Error('Second positional arg should be player2, got: ' + arg2);
                }

                console.log('Positional arguments parsed correctly');
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val args = listOf("player1", "player2", "--verbose")
        val result = GraalEngine.executeScript(script, mapOf("Args" to args))
        assertTrue(result is ScriptResult.Success, "Positional arguments should be parsed correctly")
    }

    @Test
    fun `test Script argv parses long flags`() {
        val script = ScriptInfo(
            name = "test-long-flags.js",
            path = createTempScript("""
                // Test long flags (--flag)
                if (!Script.argv.hasFlag('verbose')) {
                    throw new Error('Should have --verbose flag');
                }

                if (!Script.argv.hasFlag('force')) {
                    throw new Error('Should have --force flag');
                }

                if (Script.argv.hasFlag('notpresent')) {
                    throw new Error('Should not have --notpresent flag');
                }

                console.log('Long flags parsed correctly');
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val args = listOf("arg1", "--verbose", "--force", "arg2")
        val result = GraalEngine.executeScript(script, mapOf("Args" to args))
        assertTrue(result is ScriptResult.Success, "Long flags should be parsed correctly")
    }

    @Test
    fun `test Script argv parses short flags`() {
        val script = ScriptInfo(
            name = "test-short-flags.js",
            path = createTempScript("""
                // Test short flags (-f)
                if (!Script.argv.hasFlag('v')) {
                    throw new Error('Should have -v flag');
                }

                if (!Script.argv.hasFlag('f')) {
                    throw new Error('Should have -f flag');
                }

                if (Script.argv.hasFlag('z')) {
                    throw new Error('Should not have -z flag');
                }

                console.log('Short flags parsed correctly');
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val args = listOf("arg1", "-v", "-f", "arg2")
        val result = GraalEngine.executeScript(script, mapOf("Args" to args))
        assertTrue(result is ScriptResult.Success, "Short flags should be parsed correctly")
    }

    @Test
    fun `test Script argv getAll returns all positional arguments`() {
        val script = ScriptInfo(
            name = "test-getall.js",
            path = createTempScript("""
                // Test getAll() returns all positional args (excluding flags)
                const allArgs = Script.argv.getAll();

                if (!Array.isArray(allArgs)) {
                    throw new Error('getAll() should return an array');
                }

                if (allArgs.length !== 3) {
                    throw new Error('Should have 3 positional args, got: ' + allArgs.length);
                }

                if (allArgs[0] !== 'player1' || allArgs[1] !== 'player2' || allArgs[2] !== 'player3') {
                    throw new Error('Positional args incorrect: ' + JSON.stringify(allArgs));
                }

                console.log('getAll() works correctly');
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val args = listOf("player1", "--verbose", "player2", "-f", "player3")
        val result = GraalEngine.executeScript(script, mapOf("Args" to args))
        assertTrue(result is ScriptResult.Success, "getAll() should return all positional arguments")
    }

    @Test
    fun `test Script argv get handles missing arguments`() {
        val script = ScriptInfo(
            name = "test-missing-args.js",
            path = createTempScript("""
                // Test get() returns undefined for missing args
                const arg0 = Script.argv.get(0);
                const arg10 = Script.argv.get(10);

                if (arg0 !== 'only-arg') {
                    throw new Error('First arg should be only-arg, got: ' + arg0);
                }

                if (arg10 !== undefined) {
                    throw new Error('Missing arg should be undefined, got: ' + arg10);
                }

                console.log('Missing arguments handled correctly');
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val args = listOf("only-arg")
        val result = GraalEngine.executeScript(script, mapOf("Args" to args))
        assertTrue(result is ScriptResult.Success, "Missing arguments should return undefined")
    }

    @Test
    fun `test Script argv with no arguments`() {
        val script = ScriptInfo(
            name = "test-no-args.js",
            path = createTempScript("""
                // Test with empty args
                const allArgs = Script.argv.getAll();

                if (!Array.isArray(allArgs)) {
                    throw new Error('getAll() should return an array');
                }

                if (allArgs.length !== 0) {
                    throw new Error('Should have 0 args, got: ' + allArgs.length);
                }

                if (Script.argv.hasFlag('anyflag')) {
                    throw new Error('Should have no flags');
                }

                console.log('Empty arguments handled correctly');
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val args = emptyList<String>()
        val result = GraalEngine.executeScript(script, mapOf("Args" to args))
        assertTrue(result is ScriptResult.Success, "Empty arguments should be handled correctly")
    }

    @Test
    fun `test Script argv raw property contains original args`() {
        val script = ScriptInfo(
            name = "test-raw-args.js",
            path = createTempScript("""
                // Test Script.argv.raw contains the original args array
                if (!Array.isArray(Script.argv.raw)) {
                    throw new Error('Script.argv.raw should be an array');
                }

                if (Script.argv.raw.length !== 5) {
                    throw new Error('Should have 5 raw args, got: ' + Script.argv.raw.length);
                }

                if (Script.argv.raw[0] !== 'arg1') {
                    throw new Error('First raw arg should be arg1');
                }

                if (Script.argv.raw[2] !== '--flag') {
                    throw new Error('Third raw arg should be --flag');
                }

                console.log('Raw args array works correctly');
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val args = listOf("arg1", "arg2", "--flag", "-v", "arg3")
        val result = GraalEngine.executeScript(script, mapOf("Args" to args))
        assertTrue(result is ScriptResult.Success, "Script.argv.raw should contain original args")
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }
}
