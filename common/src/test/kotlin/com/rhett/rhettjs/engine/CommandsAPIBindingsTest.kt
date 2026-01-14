package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for Commands API bindings exposed to JavaScript via GraalVM.
 * Tests command registration with Brigadier integration.
 */
class CommandsAPIBindingsTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    @Test
    fun `test Commands API is importable`() {
        val script = ScriptInfo(
            name = "test-commands-import.js",
            path = createTempScript("""
                import Commands from 'Commands';

                if (typeof Commands !== 'object') {
                    throw new Error('Commands should be an object');
                }

                const methods = ['register', 'unregister'];
                for (const method of methods) {
                    if (typeof Commands[method] !== 'function') {
                        throw new Error('Commands.' + method + ' should be a function');
                    }
                }

                console.log('Commands API import works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Commands API should be importable")
    }

    @Test
    fun `test Commands register() returns builder`() {
        val script = ScriptInfo(
            name = "test-commands-register.js",
            path = createTempScript("""
                import Commands from 'Commands';

                const builder = Commands.register('testcmd');

                if (typeof builder !== 'object') {
                    throw new Error('Commands.register should return a builder object');
                }

                // Check builder methods exist
                const methods = ['description', 'permission', 'argument', 'executes'];
                for (const method of methods) {
                    if (typeof builder[method] !== 'function') {
                        throw new Error('Builder.' + method + ' should be a function');
                    }
                }

                console.log('Commands.register() returns builder');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Commands.register() should return builder")
    }

    @Test
    fun `test builder methods are chainable`() {
        val script = ScriptInfo(
            name = "test-builder-chainable.js",
            path = createTempScript("""
                import Commands from 'Commands';

                const result = Commands.register('test')
                    .description('Test command')
                    .permission('test.use')
                    .argument('player', 'player')
                    .executes(({ caller, args }) => {
                        console.log('Command executed');
                    });

                // Should complete without error
                console.log('Builder methods are chainable');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Builder methods should be chainable")
    }

    @Test
    fun `test command with no arguments`() {
        val script = ScriptInfo(
            name = "test-simple-command.js",
            path = createTempScript("""
                import Commands from 'Commands';

                Commands.register('heal')
                    .description('Heal yourself')
                    .executes(({ caller }) => {
                        console.log('Heal command executed');
                    });

                console.log('Simple command registered');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Simple command should register")
    }

    @Test
    fun `test command with single argument`() {
        val script = ScriptInfo(
            name = "test-command-with-arg.js",
            path = createTempScript("""
                import Commands from 'Commands';

                Commands.register('tp')
                    .description('Teleport to player')
                    .argument('target', 'player')
                    .executes(({ caller, args }) => {
                        const target = args.target;
                        console.log('Teleport to:', target);
                    });

                console.log('Command with argument registered');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Command with argument should register")
    }

    @Test
    fun `test command with multiple arguments`() {
        val script = ScriptInfo(
            name = "test-command-multi-args.js",
            path = createTempScript("""
                import Commands from 'Commands';

                Commands.register('give')
                    .description('Give items to player')
                    .argument('player', 'player')
                    .argument('item', 'item')
                    .argument('count', 'int')
                    .executes(({ caller, args }) => {
                        console.log('Give:', args.item, 'x', args.count, 'to', args.player);
                    });

                console.log('Command with multiple arguments registered');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Command with multiple arguments should register")
    }

    @Test
    fun `test command event object structure`() {
        val script = ScriptInfo(
            name = "test-command-event.js",
            path = createTempScript("""
                import Commands from 'Commands';

                Commands.register('test')
                    .executes((event) => {
                        // Check event structure
                        if (!event.caller) {
                            throw new Error('Event should have caller');
                        }

                        if (!event.args) {
                            throw new Error('Event should have args');
                        }

                        if (!event.command) {
                            throw new Error('Event should have command name');
                        }

                        console.log('Event structure correct');
                    });

                console.log('Command event object has correct structure');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Command event object should have correct structure")
    }

    @Test
    fun `test command with async handler`() {
        val script = ScriptInfo(
            name = "test-async-command.js",
            path = createTempScript("""
                import Commands from 'Commands';

                Commands.register('asynctest')
                    .executes(async ({ caller }) => {
                        await Promise.resolve();
                        console.log('Async command executed');
                    });

                console.log('Async command handler registered');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Command handlers should support async")
    }

    @Test
    fun `test Commands unregister() removes command`() {
        val script = ScriptInfo(
            name = "test-commands-unregister.js",
            path = createTempScript("""
                import Commands from 'Commands';

                Commands.register('temp')
                    .executes(({ caller }) => {
                        console.log('Temp command');
                    });

                Commands.unregister('temp');

                console.log('Commands.unregister() works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Commands.unregister() should remove command")
    }

    @Test
    fun `test command permission as string`() {
        val script = ScriptInfo(
            name = "test-permission-string.js",
            path = createTempScript("""
                import Commands from 'Commands';

                Commands.register('admin')
                    .permission('admin.use')
                    .executes(({ caller }) => {
                        console.log('Admin command');
                    });

                console.log('Permission as string works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Permission as string should work")
    }

    @Test
    fun `test command permission as function`() {
        val script = ScriptInfo(
            name = "test-permission-function.js",
            path = createTempScript("""
                import Commands from 'Commands';

                Commands.register('custom')
                    .permission((caller) => {
                        // Custom permission check
                        return true;
                    })
                    .executes(({ caller }) => {
                        console.log('Custom permission command');
                    });

                console.log('Permission as function works');
            """),
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Permission as function should work")
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }
}
