package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for Server API bindings exposed to JavaScript via GraalVM.
 * Tests event registration, server properties, and broadcast methods.
 */
class ServerAPIBindingsTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    @Test
    fun `test Server API is importable`() {
        val script = ScriptInfo(
            name = "test-server-import.js",
            path = createTempScript("""
                import Server from 'Server';

                if (typeof Server !== 'object') {
                    throw new Error('Server should be an object');
                }

                const methods = ['on', 'off', 'once', 'broadcast', 'runCommand'];
                for (const method of methods) {
                    if (typeof Server[method] !== 'function') {
                        throw new Error('Server.' + method + ' should be a function');
                    }
                }

                const properties = ['tps', 'players', 'maxPlayers', 'motd'];
                for (const prop of properties) {
                    if (!(prop in Server)) {
                        throw new Error('Server.' + prop + ' should exist');
                    }
                }

                console.log('Server API import works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Server API should be importable with all methods")
    }

    @Test
    fun `test Server on() registers event handler`() {
        val script = ScriptInfo(
            name = "test-server-on.js",
            path = createTempScript("""
                import Server from 'Server';

                let called = false;
                const handler = (player) => {
                    called = true;
                };

                Server.on('playerJoin', handler);

                // Handler should be registered (no error thrown)
                if (typeof Server.on !== 'function') {
                    throw new Error('Server.on should register handlers');
                }

                console.log('Server.on() works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Server.on() should register event handlers")
    }

    @Test
    fun `test Server off() unregisters event handler`() {
        val script = ScriptInfo(
            name = "test-server-off.js",
            path = createTempScript("""
                import Server from 'Server';

                const handler = (player) => {
                    console.log('Handler called');
                };

                Server.on('playerJoin', handler);
                Server.off('playerJoin', handler);

                // Should not throw error
                console.log('Server.off() works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Server.off() should unregister event handlers")
    }

    @Test
    fun `test Server once() registers one-time handler`() {
        val script = ScriptInfo(
            name = "test-server-once.js",
            path = createTempScript("""
                import Server from 'Server';

                Server.once('playerJoin', (player) => {
                    console.log('One-time handler');
                });

                // Should not throw error
                console.log('Server.once() works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Server.once() should register one-time handlers")
    }

    @Test
    fun `test Server properties are accessible`() {
        val script = ScriptInfo(
            name = "test-server-properties.js",
            path = createTempScript("""
                import Server from 'Server';

                // Check properties exist and have correct types
                if (typeof Server.tps !== 'number') {
                    throw new Error('Server.tps should be a number');
                }

                if (!Array.isArray(Server.players)) {
                    throw new Error('Server.players should be an array');
                }

                if (typeof Server.maxPlayers !== 'number') {
                    throw new Error('Server.maxPlayers should be a number');
                }

                if (typeof Server.motd !== 'string') {
                    throw new Error('Server.motd should be a string');
                }

                console.log('Server properties work');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Server properties should be accessible")
    }

    @Test
    fun `test Server broadcast() sends message`() {
        val script = ScriptInfo(
            name = "test-server-broadcast.js",
            path = createTempScript("""
                import Server from 'Server';

                // Should not throw error
                Server.broadcast('Test message');

                console.log('Server.broadcast() works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Server.broadcast() should work")
    }

    @Test
    fun `test Server runCommand() executes command`() {
        val script = ScriptInfo(
            name = "test-server-runcommand.js",
            path = createTempScript("""
                import Server from 'Server';

                // Should not throw error
                Server.runCommand('time set day');

                console.log('Server.runCommand() works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Server.runCommand() should work")
    }

    @Test
    fun `test event handler can be async`() {
        val script = ScriptInfo(
            name = "test-async-handler.js",
            path = createTempScript("""
                import Server from 'Server';

                Server.on('playerJoin', async (player) => {
                    // Async handler should be allowed
                    await Promise.resolve();
                    console.log('Async handler');
                });

                console.log('Async event handlers work');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Event handlers should support async")
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }
}
