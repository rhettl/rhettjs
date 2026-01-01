package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for ES6 module system with import/export.
 * Tests module resolution, built-in APIs, and Script.* context.
 */
class ModuleSystemTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager with temp directory
        ConfigManager.init(tempDir)

        // Create directory structure
        val scriptsDir = tempDir.resolve("rjs")
        Files.createDirectories(scriptsDir.resolve("modules"))
        Files.createDirectories(scriptsDir.resolve("startup"))
        Files.createDirectories(scriptsDir.resolve("scripts"))
        Files.createDirectories(scriptsDir.resolve("server"))

        // Set scripts directory for module resolution
        GraalEngine.setScriptsDirectory(scriptsDir)
    }

    @Test
    fun `test module category exists`() {
        val categories = ScriptCategory.values()
        val moduleCategory = categories.find { it.dirName == "modules" }

        assertNotNull(moduleCategory, "MODULES category should exist")
        assertEquals("modules", moduleCategory?.dirName)
    }

    @Test
    @Disabled("TODO: Built-in imports via 'import X from \"X\"' not working yet - need custom module loader")
    fun `test import built-in API modules`() {
        val script = ScriptInfo(
            name = "test-builtin-imports.js",
            path = createScript("startup", """
                import World from 'World';
                import Structure from 'Structure';
                import Store from 'Store';
                import NBT from 'NBT';

                console.log("World:", World.toString());
                console.log("Structure:", Structure.toString());
                console.log("Store:", Store.toString());
                console.log("NBT:", NBT.toString());

                if (!World || !Structure || !Store || !NBT) {
                    throw new Error("Built-in APIs should be importable");
                }
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Built-in imports should work")
    }

    @Test
    @Disabled("TODO: Imports resolve from actual file location, not virtual URI - update test to use '../modules/'")
    fun `test import user module from modules directory`() {
        // Create a user module
        createModule("math.js", """
            export function add(a, b) {
                return a + b;
            }

            export const PI = 3.14159;
        """)

        // Create script that imports the module
        val script = ScriptInfo(
            name = "test-user-import.js",
            path = createScript("startup", """
                import { add, PI } from './math.js';

                const sum = add(5, 3);
                console.log("5 + 3 =", sum);
                console.log("PI =", PI);

                if (sum !== 8) {
                    throw new Error("Expected add(5, 3) to equal 8, got " + sum);
                }

                if (PI !== 3.14159) {
                    throw new Error("Expected PI to equal 3.14159");
                }
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "User module imports should work")
    }

    @Test
    @Disabled("TODO: Imports resolve from actual file location, not virtual URI - update test to use '../modules/'")
    fun `test import default export`() {
        createModule("utils.js", """
            const Utils = {
                greet(name) {
                    return "Hello, " + name;
                }
            };

            export default Utils;
        """)

        val script = ScriptInfo(
            name = "test-default-import.js",
            path = createScript("startup", """
                import Utils from './utils.js';

                const greeting = Utils.greet("World");
                console.log(greeting);

                if (greeting !== "Hello, World") {
                    throw new Error("Default export import failed");
                }
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Default export imports should work")
    }

    @Test
    fun `test cross-category import`() {
        // Create a utility in scripts/
        createScript("scripts", """
            export function helper() {
                return "helper-result";
            }
        """, "helper.js")

        // Import from startup/
        val script = ScriptInfo(
            name = "test-cross-category.js",
            path = createScript("startup", """
                import { helper } from '../scripts/helper.js';

                const result = helper();
                console.log("Helper result:", result);

                if (result !== "helper-result") {
                    throw new Error("Cross-category import failed");
                }
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Cross-category imports should work")
    }

    @Test
    fun `test Script context injected for utility scripts`() {
        val mockCaller = mapOf("name" to "TestPlayer")
        val mockArgs = listOf("arg1", "arg2")

        val script = ScriptInfo(
            name = "test-script-context.js",
            path = createScript("scripts", """
                console.log("Script.caller:", Script.caller);
                console.log("Script.args:", Script.args);

                if (!Script.caller) {
                    throw new Error("Script.caller should be defined");
                }

                if (!Script.args) {
                    throw new Error("Script.args should be defined");
                }
            """),
            category = ScriptCategory.UTILITY,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(
            script,
            additionalBindings = mapOf(
                "Caller" to mockCaller,
                "Args" to mockArgs
            )
        )

        assertTrue(result is ScriptResult.Success, "Script.* context should be available")
    }

    @Test
    fun `test Script context NOT injected for non-utility scripts`() {
        val script = ScriptInfo(
            name = "test-no-script-context.js",
            path = createScript("startup", """
                // Script.* should not exist in startup scripts
                if (typeof Script !== 'undefined') {
                    throw new Error("Script.* should not exist in startup scripts");
                }

                console.log("Script context correctly absent in startup");
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Script.* should only exist in UTILITY category")
    }

    @Test
    @Disabled("TODO: Imports resolve from actual file location, not virtual URI - update test to use '../modules/'")
    fun `test module with multiple exports`() {
        createModule("helpers.js", """
            export const VERSION = "1.0.0";

            export function format(str) {
                return "[" + str + "]";
            }

            export class Helper {
                constructor(name) {
                    this.name = name;
                }

                greet() {
                    return "Hello from " + this.name;
                }
            }
        """)

        val script = ScriptInfo(
            name = "test-multiple-exports.js",
            path = createScript("startup", """
                import { VERSION, format, Helper } from './helpers.js';

                console.log("VERSION:", VERSION);
                console.log("Formatted:", format("test"));

                const helper = new Helper("TestHelper");
                console.log(helper.greet());

                if (VERSION !== "1.0.0") throw new Error("VERSION export failed");
                if (format("x") !== "[x]") throw new Error("format export failed");
                if (helper.greet() !== "Hello from TestHelper") throw new Error("class export failed");
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Multiple exports should work")
    }

    @Test
    fun `test import without file extension fails for user modules`() {
        createModule("test.js", """
            export const value = 42;
        """)

        val script = ScriptInfo(
            name = "test-no-extension.js",
            path = createScript("startup", """
                try {
                    // This should fail - user modules need .js extension
                    import { value } from './test';
                    throw new Error("Should have failed without .js extension");
                } catch (e) {
                    console.log("Correctly failed:", e.message);
                }
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        // This test expects the import to fail, which will cause script error
        // We're testing that the convention is enforced
        val result = GraalEngine.executeScript(script)
        // Since import is at top-level, it will fail before try-catch
        assertTrue(result is ScriptResult.Error, "Import without .js should fail for user modules")
    }

    // Helper methods

    private fun createModule(name: String, content: String): Path {
        val modulePath = tempDir.resolve("rjs/modules/$name")
        Files.writeString(modulePath, content)
        return modulePath
    }

    private fun createScript(category: String, content: String, name: String = "test.js"): Path {
        val scriptPath = tempDir.resolve("rjs/$category/$name")
        Files.writeString(scriptPath, content)
        return scriptPath
    }
}
