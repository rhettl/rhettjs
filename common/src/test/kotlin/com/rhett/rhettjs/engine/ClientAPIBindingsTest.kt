package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for Client API bindings exposed to JavaScript via GraalVM.
 * Tests client player access, event registration, and client-side operations.
 *
 * TDD: Tests written first, implementation follows.
 */
class ClientAPIBindingsTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    @Test
    fun `test Client API is importable`() {
        val script = ScriptInfo(
            name = "test-client-import.js",
            path = createTempScript("""
                import Client from 'Client';

                if (typeof Client !== 'object') {
                    throw new Error('Client should be an object');
                }

                const methods = ['on', 'off', 'once'];
                for (const method of methods) {
                    if (typeof Client[method] !== 'function') {
                        throw new Error('Client.' + method + ' should be a function');
                    }
                }

                if (typeof Client.player !== 'object') {
                    throw new Error('Client.player should be an object');
                }

                console.log('Client API import works');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client API should be importable with all methods")
    }

    @Test
    fun `test Client eventTypes property exists`() {
        val script = ScriptInfo(
            name = "test-client-eventtypes.js",
            path = createTempScript("""
                import Client from 'Client';

                if (typeof Client.eventTypes !== 'object') {
                    throw new Error('Client.eventTypes should be an object');
                }

                const expectedTypes = ['KEY_PRESS', 'TICK', 'CHAT_SEND'];
                for (const type of expectedTypes) {
                    if (!(type in Client.eventTypes)) {
                        throw new Error('Client.eventTypes.' + type + ' should exist');
                    }
                }

                console.log('Client.eventTypes:', Client.eventTypes);
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client.eventTypes should exist with all event types")
    }

    @Test
    fun `test Client player property has expected structure`() {
        val script = ScriptInfo(
            name = "test-client-player.js",
            path = createTempScript("""
                import Client from 'Client';

                const player = Client.player;
                if (!player) {
                    throw new Error('Client.player should exist');
                }

                // Check properties
                const requiredProps = ['name', 'uuid', 'health', 'maxHealth', 'foodLevel', 'saturation', 'gameMode', 'position', 'rotation'];
                for (const prop of requiredProps) {
                    if (!(prop in player)) {
                        throw new Error('Client.player.' + prop + ' should exist');
                    }
                }

                // Check methods
                const requiredMethods = ['sendMessage', 'sendSuccess', 'sendError', 'sendWarning', 'sendInfo', 'runCommand'];
                for (const method of requiredMethods) {
                    if (typeof player[method] !== 'function') {
                        throw new Error('Client.player.' + method + ' should be a function');
                    }
                }

                console.log('Client.player structure valid');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client.player should have all expected properties and methods")
    }

    @Test
    fun `test Client on() registers event handler`() {
        val script = ScriptInfo(
            name = "test-client-on.js",
            path = createTempScript("""
                import Client from 'Client';

                let handlerCalled = false;
                Client.on(Client.eventTypes.TICK, (event) => {
                    handlerCalled = true;
                });

                // Handler registered successfully
                console.log('Event handler registered');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client.on() should register event handler")
    }

    @Test
    fun `test Client off() unregisters event handler`() {
        val script = ScriptInfo(
            name = "test-client-off.js",
            path = createTempScript("""
                import Client from 'Client';

                const handler = (event) => {
                    console.log('Handler called');
                };

                Client.on(Client.eventTypes.TICK, handler);
                Client.off(Client.eventTypes.TICK, handler);

                console.log('Event handler unregistered');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client.off() should unregister event handler")
    }

    @Test
    fun `test Client once() registers one-time handler`() {
        val script = ScriptInfo(
            name = "test-client-once.js",
            path = createTempScript("""
                import Client from 'Client';

                Client.once(Client.eventTypes.TICK, (event) => {
                    console.log('One-time handler called');
                });

                console.log('One-time event handler registered');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client.once() should register one-time handler")
    }

    @Test
    fun `test Client player sendMessage methods`() {
        val script = ScriptInfo(
            name = "test-client-player-sendmessage.js",
            path = createTempScript("""
                import Client from 'Client';

                // These should not throw
                Client.player.sendMessage('Regular message');
                Client.player.sendSuccess('Success message');
                Client.player.sendError('Error message');
                Client.player.sendWarning('Warning message');
                Client.player.sendInfo('Info message');

                console.log('All sendMessage methods work');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client.player.sendMessage methods should work")
    }

    @Test
    fun `test Client player runCommand`() {
        val script = ScriptInfo(
            name = "test-client-player-runcommand.js",
            path = createTempScript("""
                import Client from 'Client';

                // Should not throw (mock will handle)
                Client.player.runCommand('gamemode creative');

                console.log('runCommand executed');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client.player.runCommand should work")
    }

    @Test
    fun `test Client API not available in SERVER context`() {
        val script = ScriptInfo(
            name = "test-client-not-in-server.js",
            path = createTempScript("""
                try {
                    const { Client } = await import('rhettjs/client');
                    throw new Error('Client API should not be available in SERVER scripts');
                } catch (e) {
                    if (e.message && e.message.includes('not found')) {
                        console.log('PASS: Client API correctly unavailable in SERVER scripts');
                    } else {
                        throw e;
                    }
                }
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client API should not be available in SERVER context")
    }

    @Test
    fun `test Client API not available in UNIVERSAL context`() {
        val script = ScriptInfo(
            name = "test-client-not-in-universal.js",
            path = createTempScript("""
                try {
                    const { Client } = await import('rhettjs/client');
                    throw new Error('Client API should not be available in STARTUP scripts');
                } catch (e) {
                    if (e.message && e.message.includes('not found')) {
                        console.log('PASS: Client API correctly unavailable in STARTUP scripts');
                    } else {
                        throw e;
                    }
                }
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client API should not be available in UNIVERSAL context")
    }

    @Test
    fun `test Client player position structure`() {
        val script = ScriptInfo(
            name = "test-client-player-position.js",
            path = createTempScript("""
                import Client from 'Client';

                const pos = Client.player.position;
                if (!pos) {
                    throw new Error('Client.player.position should exist');
                }

                const requiredProps = ['x', 'y', 'z', 'dimension'];
                for (const prop of requiredProps) {
                    if (!(prop in pos)) {
                        throw new Error('Client.player.position.' + prop + ' should exist');
                    }
                }

                console.log('Position structure valid');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client.player.position should have x, y, z, dimension")
    }

    @Test
    fun `test Client player rotation structure`() {
        val script = ScriptInfo(
            name = "test-client-player-rotation.js",
            path = createTempScript("""
                import Client from 'Client';

                const rot = Client.player.rotation;
                if (!rot) {
                    throw new Error('Client.player.rotation should exist');
                }

                if (!('yaw' in rot) || !('pitch' in rot)) {
                    throw new Error('Client.player.rotation should have yaw and pitch');
                }

                console.log('Rotation structure valid');
            """),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Client.player.rotation should have yaw and pitch")
    }

    // ========================================
    // Helper methods
    // ========================================

    private fun createTempScript(content: String): Path {
        val scriptFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(scriptFile, content)
        return scriptFile
    }
}
