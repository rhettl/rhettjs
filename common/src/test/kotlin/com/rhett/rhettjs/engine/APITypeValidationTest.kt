package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates that TypeScript definitions match the runtime API surface.
 * Parses rhettjs.d.ts and compares against expected GraalEngine bindings.
 *
 * This ensures .d.ts stays in sync with actual runtime APIs.
 * Methods prefixed with _ are excluded (internal/private functions).
 *
 * IMPORTANT: When adding new API methods to GraalEngine, update:
 * 1. GraalEngine.createXAPIProxy() - The actual runtime binding
 * 2. getRuntimeMethods() in this test - Expected method list
 * 3. common/src/main/resources/rhettjs-types/<api>.d.ts - Type definitions (e.g., world.d.ts, commands.d.ts)
 *
 * If any of these are out of sync, this test will fail.
 *
 * Note: TypeScript definitions are now modular:
 * - Each API has its own .d.ts file (world.d.ts, commands.d.ts, etc.)
 * - rhettjs.d.ts is a barrel file that re-exports all APIs
 * - This enables both `import {World} from "rhettjs"` and `import World from "rhettjs/world"`
 */
class APITypeValidationTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    /**
     * Parse .d.ts file to extract declared method names for an API.
     * Supports both namespace pattern and module pattern.
     * @param apiName The API name (e.g., "StructureNbt", "World")
     * @return Sorted list of method names
     */
    private fun parseTypeDefinitions(apiName: String): List<String> {
        // Map API name to its file
        val fileName = when (apiName) {
            "StructureNbt", "LargeStructureNbt" -> "structure.d.ts"
            "WorldgenStructure" -> "worldgen-structure.d.ts"
            "World" -> "world.d.ts"
            "Commands" -> "commands.d.ts"
            "Server" -> "server.d.ts"
            "Client" -> "client.d.ts"
            "Store" -> "store.d.ts"
            "NBT" -> "nbt.d.ts"
            "Runtime" -> "runtime.d.ts"
            "Script" -> "script.d.ts"
            "UI" -> "ui.d.ts"
            else -> throw IllegalArgumentException("Unknown API: $apiName")
        }

        val dtsContent = javaClass.getResourceAsStream("/rhettjs-types/$fileName")
            ?.bufferedReader()
            ?.readText()
            ?: throw IllegalStateException("Type definitions not found at /rhettjs-types/$fileName")

        // Try Module Pattern first: "declare module 'rhettjs/...' { export const API: { ... } }"
        val modulePattern = """declare\s+module\s+['"]rhettjs/[\w-]+['"]\s*\{""".toRegex()
        val moduleMatch = modulePattern.find(dtsContent)

        if (moduleMatch != null) {
            return parseModulePattern(dtsContent, apiName, moduleMatch)
        }

        // Fall back to Namespace Pattern: "declare namespace API { ... }"
        val namespacePattern = """(?:declare\s+)?namespace\s+$apiName\s*\{""".toRegex()
        val namespaceMatch = namespacePattern.find(dtsContent)

        if (namespaceMatch != null) {
            return parseNamespacePattern(dtsContent, apiName, namespaceMatch)
        }

        throw IllegalStateException("No valid declaration (namespace or module) found for '$apiName' in $fileName")
    }

    /**
     * Parse module pattern: declare module 'rhettjs/api' { export const API: { ... } }
     */
    private fun parseModulePattern(content: String, apiName: String, moduleMatch: MatchResult): List<String> {
        // Find "export const API: {" or "export const API = {"
        val exportPattern = """export\s+const\s+$apiName\s*:\s*\{""".toRegex()
        val exportMatch = exportPattern.find(content, moduleMatch.range.last)
            ?: throw IllegalStateException("No 'export const $apiName' found in module declaration")

        // Extract the API object block
        val apiBlock = extractBlock(content, exportMatch)

        // Extract method names ONLY: "methodName(" or "methodName?("
        // Properties like "readonly dimensions: string[]" are excluded
        val methodPattern = """^\s+(?:readonly\s+)?(\w+)\??\s*\(""".toRegex(RegexOption.MULTILINE)

        return methodPattern.findAll(apiBlock)
            .map { it.groupValues[1] }
            .filter { it != apiName } // Exclude constructor if present
            .filter { !it.matches("""^[A-Z].*""".toRegex()) } // Exclude type names (PascalCase)
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * Parse namespace pattern: declare namespace API { ... }
     */
    private fun parseNamespacePattern(content: String, apiName: String, namespaceMatch: MatchResult): List<String> {
        val apiBlock = extractBlock(content, namespaceMatch)

        // Extract method names: "function methodName(" or "methodName("
        val methodPattern = """^\s+(?:function\s+)?(\w+)\s*\(""".toRegex(RegexOption.MULTILINE)

        return methodPattern.findAll(apiBlock)
            .map { it.groupValues[1] }
            .filter { it != apiName } // Exclude constructor if present
            .filter { !it.matches("""^[A-Z].*""".toRegex()) } // Exclude type names (PascalCase)
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * Extract a block by finding matching closing brace (accounts for nesting)
     */
    private fun extractBlock(content: String, match: MatchResult): String {
        val startIdx = match.range.last + 1
        var braceCount = 1
        var endIdx = startIdx

        for (i in startIdx until content.length) {
            when (content[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        endIdx = i
                        break
                    }
                }
            }
        }

        return content.substring(startIdx, endIdx)
    }

    /**
     * Get actual runtime API methods by introspecting GraalEngine bindings.
     * This ACTUALLY checks what methods exist at runtime, not a manually maintained list.
     *
     * Properties (like Server.tps, World.dimensions) are excluded since they're declared differently in .d.ts.
     *
     * @param apiName The API name (e.g., "Structure", "World")
     * @return Sorted list of method names that exist in the actual runtime
     */
    private fun getRuntimeMethods(apiName: String): List<String> {
        // Get actual GraalVM context and introspect bindings
        val context = GraalEngine.getOrCreateContext()
        val bindings = context.getBindings("js")

        // Map API name to builtin binding name
        // Runtime is bound directly as "Runtime", others as "__builtin_<Name>"
        val builtinName = when (apiName) {
            "Runtime" -> "Runtime"
            "Console" -> "console"  // Special case: lowercase
            "StructureNbt", "LargeStructureNbt", "WorldgenStructure", "World", "Commands", "Server", "Client", "Store", "NBT", "UI", "Script" -> "__builtin_$apiName"
            else -> throw IllegalArgumentException("Unknown API: $apiName")
        }

        // Get the API object from bindings
        val apiObject = bindings.getMember(builtinName)
            ?: throw IllegalStateException("API not found in bindings: $builtinName")

        // Get member keys from the Value object
        // memberKeys returns a Set<String> which we convert to List
        val memberKeys = apiObject.memberKeys.toList()

        // Known properties to exclude (declared as properties in .d.ts, not methods)
        val knownProperties = setOf(
            "env",          // Runtime.env
            "dimensions",   // World.dimensions
            "tps", "players", "maxPlayers", "motd", "eventTypes",  // Server properties
            "player",       // Client.player
            "raw"           // Script.argv.raw
        )

        // Filter and return
        return memberKeys
            .filter { !it.startsWith("_") }      // Exclude private (underscore prefix)
            .filter { it !in knownProperties }   // Exclude known properties
            .sorted()
            .toList()
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }

    @Test
    fun `StructureNbt API matches type definitions`() {
        val expected = parseTypeDefinitions("StructureNbt")
        val actual = getRuntimeMethods("StructureNbt")

        assertEquals(
            expected,
            actual,
            """
            StructureNbt API methods don't match structure.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/structure.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `LargeStructureNbt API matches type definitions`() {
        val expected = parseTypeDefinitions("LargeStructureNbt")
        val actual = getRuntimeMethods("LargeStructureNbt")

        assertEquals(
            expected,
            actual,
            """
            LargeStructureNbt API methods don't match structure.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/structure.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `World API matches type definitions`() {
        val expected = parseTypeDefinitions("World")
        val actual = getRuntimeMethods("World")

        assertEquals(
            expected,
            actual,
            """
            World API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/rhettjs.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `Store API matches type definitions`() {
        val expected = parseTypeDefinitions("Store")
        val actual = getRuntimeMethods("Store")

        assertEquals(
            expected,
            actual,
            """
            Store API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/store.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `Commands API matches type definitions`() {
        val expected = parseTypeDefinitions("Commands")
        val actual = getRuntimeMethods("Commands")

        assertEquals(
            expected,
            actual,
            """
            Commands API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/commands.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `Server API matches type definitions`() {
        val expected = parseTypeDefinitions("Server")
        val actual = getRuntimeMethods("Server")

        assertEquals(
            expected,
            actual,
            """
            Server API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/server.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `Client API matches type definitions`() {
        // Force CLIENT context by executing a CLIENT script first
        // This ensures Client API is injected into bindings
        val initScript = ScriptInfo(
            name = "client-api-init.js",
            path = createTempScript("console.log('Initializing CLIENT context');"),
            category = ScriptCategory.CLIENT,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )
        GraalEngine.executeScript(initScript)

        val expected = parseTypeDefinitions("Client")
        val actual = getRuntimeMethods("Client")

        assertEquals(
            expected,
            actual,
            """
            Client API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/client.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `NBT API matches type definitions`() {
        val expected = parseTypeDefinitions("NBT")
        val actual = getRuntimeMethods("NBT")

        assertEquals(
            expected,
            actual,
            """
            NBT API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/nbt.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `Runtime API matches type definitions`() {
        val expected = parseTypeDefinitions("Runtime")
        val actual = getRuntimeMethods("Runtime")

        assertEquals(
            expected,
            actual,
            """
            Runtime API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/runtime.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `WorldgenStructure API matches type definitions`() {
        val expected = parseTypeDefinitions("WorldgenStructure")
        val actual = getRuntimeMethods("WorldgenStructure")

        assertEquals(
            expected,
            actual,
            """
            WorldgenStructure API methods don't match worldgen-structure.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/worldgen-structure.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `UI API matches type definitions`() {
        val expected = parseTypeDefinitions("UI")
        val actual = getRuntimeMethods("UI")

        assertEquals(
            expected,
            actual,
            """
            UI API methods don't match ui.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/ui.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `type definitions files exist and are readable`() {
        // Check barrel file
        val barrelContent = javaClass.getResourceAsStream("/rhettjs-types/rhettjs.d.ts")
            ?.bufferedReader()
            ?.readText()
        assertNotNull(barrelContent, "rhettjs.d.ts barrel file should be present in resources")

        // Check individual API files
        val apiFiles = listOf("world.d.ts", "commands.d.ts", "server.d.ts", "structure.d.ts",
                              "worldgen-structure.d.ts", "store.d.ts", "nbt.d.ts", "runtime.d.ts", "script.d.ts", "ui.d.ts", "types.d.ts")

        for (file in apiFiles) {
            val content = javaClass.getResourceAsStream("/rhettjs-types/$file")
                ?.bufferedReader()
                ?.readText()
            assertNotNull(content, "$file should be present in resources")
        }

        // Verify structure.d.ts contains both APIs (module pattern)
        val structureContent = javaClass.getResourceAsStream("/rhettjs-types/structure.d.ts")
            ?.bufferedReader()
            ?.readText()
        assertTrue(structureContent!!.contains("export const StructureNbt"), "structure.d.ts should contain StructureNbt API")
        assertTrue(structureContent.contains("export const LargeStructureNbt"), "structure.d.ts should contain LargeStructureNbt API")
    }
}
