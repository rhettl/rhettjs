package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for Structure API bindings exposed to JavaScript via GraalVM.
 * Tests async file operations for structure save/load/placement.
 */
class StructureAPIBindingsTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    @Test
    fun `test Structure API is importable`() {
        val script = ScriptInfo(
            name = "test-structure-import.js",
            path = createTempScript("""
                import Structure from 'Structure';

                if (typeof Structure !== 'object') {
                    throw new Error('Structure should be an object');
                }

                const methods = ['load', 'save', 'delete', 'exists', 'list', 'place', 'capture'];
                for (const method of methods) {
                    if (typeof Structure[method] !== 'function') {
                        throw new Error('Structure.' + method + ' should be a function');
                    }
                }

                console.log('Structure API import works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure API should be importable with all methods")
    }

    @Test
    fun `test Structure save returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-save-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const data = { size: [5, 5, 5], blocks: [], palette: [] };
                const promise = Structure.save('test', data);

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.save should return a Promise');
                }

                console.log('Structure.save returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.save should return Promise")
    }

    @Test
    fun `test Structure load returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-load-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise = Structure.load('test');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.load should return a Promise');
                }

                console.log('Structure.load returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.load should return Promise")
    }

    @Test
    fun `test Structure exists returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-exists-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise = Structure.exists('test');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.exists should return a Promise');
                }

                console.log('Structure.exists returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.exists should return Promise")
    }

    @Test
    fun `test Structure delete returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-delete-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise = Structure.delete('test');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.delete should return a Promise');
                }

                console.log('Structure.delete returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.delete should return Promise")
    }

    @Test
    fun `test Structure list returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-list-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise = Structure.list();

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.list should return a Promise');
                }

                console.log('Structure.list returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.list should return Promise")
    }

    @Test
    fun `test Structure place returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-place-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise = Structure.place('test', pos, 0);

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.place should return a Promise');
                }

                console.log('Structure.place returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.place should return Promise")
    }

    @Test
    fun `test Structure capture returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-capture-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos1 = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const pos2 = { x: 5, y: 69, z: 5, dimension: 'minecraft:overworld' };
                const promise = Structure.capture('test', pos1, pos2);

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.capture should return a Promise');
                }

                console.log('Structure.capture returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.capture should return Promise")
    }

    @Test
    fun `test Structure list accepts optional pool parameter`() {
        val script = ScriptInfo(
            name = "test-structure-list-pool.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise1 = Structure.list();
                const promise2 = Structure.list('village');

                if (!(promise1 instanceof Promise)) {
                    throw new Error('Structure.list() should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('Structure.list(pool) should return Promise');
                }

                console.log('Structure.list accepts optional pool');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.list should accept optional pool parameter")
    }

    @Test
    fun `test Structure place accepts optional rotation parameter`() {
        val script = ScriptInfo(
            name = "test-structure-place-rotation.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise1 = Structure.place('test', pos);
                const promise2 = Structure.place('test', pos, 90);

                if (!(promise1 instanceof Promise)) {
                    throw new Error('Structure.place without rotation should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('Structure.place with rotation should return Promise');
                }

                console.log('Structure.place accepts optional rotation');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.place should accept optional rotation parameter")
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }
}
