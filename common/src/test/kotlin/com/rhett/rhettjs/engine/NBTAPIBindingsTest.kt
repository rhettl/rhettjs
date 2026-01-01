package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for NBT API bindings exposed to JavaScript via GraalVM.
 * Tests that NBT helper functions are accessible and work correctly.
 */
class NBTAPIBindingsTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    @Test
    fun `test NBT compound creates object`() {
        val script = ScriptInfo(
            name = "test-nbt-compound.js",
            path = createTempScript("""
                import NBT from 'NBT';

                console.log('NBT imported successfully');
                console.log('NBT:', NBT);

                const compound = NBT.compound({
                    Health: NBT.double(20.0),
                    CustomName: NBT.string('Test')
                });

                console.log('compound:', compound);

                if (!compound) {
                    throw new Error('NBT.compound should return an object');
                }

                console.log('NBT.compound works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT.compound should work")
    }

    @Test
    fun `test NBT primitive types`() {
        val script = ScriptInfo(
            name = "test-nbt-primitives.js",
            path = createTempScript("""
                import NBT from 'NBT';

                const str = NBT.string('test');
                const num = NBT.int(42);
                const dbl = NBT.double(3.14);
                const b = NBT.byte(1);

                // NBT helpers just pass through values - primitives stay primitives
                if (str !== 'test') throw new Error('NBT.string should return string');
                if (num !== 42) throw new Error('NBT.int should return number');
                if (dbl !== 3.14) throw new Error('NBT.double should return number');
                if (b !== 1) throw new Error('NBT.byte should return number');

                console.log('NBT primitives work');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT primitive types should work")
    }

    @Test
    fun `test NBT list creates array`() {
        val script = ScriptInfo(
            name = "test-nbt-list.js",
            path = createTempScript("""
                import NBT from 'NBT';

                const list = NBT.list([
                    NBT.string('a'),
                    NBT.string('b')
                ]);

                if (typeof list !== 'object') {
                    throw new Error('NBT.list should return an object');
                }

                console.log('NBT.list works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT.list should work")
    }

    @Test
    fun `test NBT get retrieves value`() {
        val script = ScriptInfo(
            name = "test-nbt-get.js",
            path = createTempScript("""
                import NBT from 'NBT';

                const compound = NBT.compound({
                    Health: NBT.double(20.0)
                });

                const health = NBT.get(compound, 'Health');
                if (health !== 20.0) {
                    throw new Error('NBT.get should retrieve value');
                }

                console.log('NBT.get works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT.get should retrieve values")
    }

    @Test
    fun `test NBT has checks existence`() {
        val script = ScriptInfo(
            name = "test-nbt-has.js",
            path = createTempScript("""
                import NBT from 'NBT';

                const compound = NBT.compound({
                    Health: NBT.double(20.0)
                });

                const hasHealth = NBT.has(compound, 'Health');
                const hasMissing = NBT.has(compound, 'Missing');

                if (hasHealth !== true) {
                    throw new Error('NBT.has should return true for existing key');
                }
                if (hasMissing !== false) {
                    throw new Error('NBT.has should return false for missing key');
                }

                console.log('NBT.has works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT.has should check existence")
    }

    @Test
    fun `test NBT set returns new object`() {
        val script = ScriptInfo(
            name = "test-nbt-set.js",
            path = createTempScript("""
                import NBT from 'NBT';

                const original = NBT.compound({
                    Health: NBT.double(20.0)
                });

                const modified = NBT.set(original, 'Health', NBT.double(10.0));

                // Should be immutable
                if (NBT.get(original, 'Health') !== 20.0) {
                    throw new Error('Original should not be modified');
                }
                if (NBT.get(modified, 'Health') !== 10.0) {
                    throw new Error('Modified should have new value');
                }

                console.log('NBT.set works (immutable)');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT.set should return new object")
    }

    @Test
    fun `test NBT delete returns new object`() {
        val script = ScriptInfo(
            name = "test-nbt-delete.js",
            path = createTempScript("""
                import NBT from 'NBT';

                const original = NBT.compound({
                    Health: NBT.double(20.0),
                    Name: NBT.string('Test')
                });

                const modified = NBT.delete(original, 'Health');

                // Should be immutable
                if (!NBT.has(original, 'Health')) {
                    throw new Error('Original should not be modified');
                }
                if (NBT.has(modified, 'Health')) {
                    throw new Error('Modified should not have deleted key');
                }
                if (!NBT.has(modified, 'Name')) {
                    throw new Error('Modified should keep other keys');
                }

                console.log('NBT.delete works (immutable)');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT.delete should return new object")
    }

    @Test
    fun `test NBT nested path queries`() {
        val script = ScriptInfo(
            name = "test-nbt-nested.js",
            path = createTempScript("""
                import NBT from 'NBT';

                const compound = NBT.compound({
                    tag: NBT.compound({
                        Damage: NBT.int(50)
                    })
                });

                const damage = NBT.get(compound, 'tag.Damage');
                if (damage !== 50) {
                    throw new Error('NBT.get should support nested paths');
                }

                console.log('NBT nested paths work');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT should support nested path queries")
    }

    @Test
    fun `test NBT API is importable`() {
        val script = ScriptInfo(
            name = "test-nbt-import.js",
            path = createTempScript("""
                import NBT from 'NBT';

                if (typeof NBT !== 'object') {
                    throw new Error('NBT should be an object');
                }

                const methods = ['compound', 'list', 'string', 'int', 'double', 'byte', 'get', 'set', 'has', 'delete'];
                for (const method of methods) {
                    if (typeof NBT[method] !== 'function') {
                        throw new Error('NBT.' + method + ' should be a function');
                    }
                }

                console.log('NBT API import works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT API should be importable with all methods")
    }

    @Test
    fun `test NBT merge shallow`() {
        val script = ScriptInfo(
            name = "test-nbt-merge-shallow.js",
            path = createTempScript("""
                import NBT from 'NBT';

                const base = NBT.compound({
                    Health: NBT.double(20.0),
                    CustomName: NBT.string('Bob')
                });

                const updates = {
                    Health: NBT.double(10.0),
                    IsVillager: NBT.byte(1)
                };

                const merged = NBT.merge(base, updates);

                // Original should be unchanged
                if (NBT.get(base, 'Health') !== 20.0) {
                    throw new Error('Original should not be modified');
                }

                // Merged should have updated Health
                if (NBT.get(merged, 'Health') !== 10.0) {
                    throw new Error('Merged should have new Health value');
                }

                // Merged should have new IsVillager field
                if (!NBT.has(merged, 'IsVillager')) {
                    throw new Error('Merged should have IsVillager field');
                }

                // Merged should preserve CustomName
                if (NBT.get(merged, 'CustomName') !== 'Bob') {
                    throw new Error('Merged should preserve CustomName');
                }

                console.log('NBT.merge (shallow) works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT.merge shallow should work")
    }

    @Test
    fun `test NBT merge deep`() {
        val script = ScriptInfo(
            name = "test-nbt-merge-deep.js",
            path = createTempScript("""
                import NBT from 'NBT';

                const base = NBT.compound({
                    tag: NBT.compound({
                        Damage: NBT.int(50),
                        display: NBT.compound({
                            Name: NBT.string('Sword'),
                            Lore: NBT.list([NBT.string('Epic')])
                        })
                    })
                });

                const updates = {
                    tag: {
                        Damage: NBT.int(100),
                        display: {
                            Name: NBT.string('Super Sword')
                        }
                    }
                };

                const merged = NBT.merge(base, updates, true);

                // Original should be unchanged
                if (NBT.get(base, 'tag.Damage') !== 50) {
                    throw new Error('Original should not be modified');
                }

                // Merged should have updated Damage
                if (NBT.get(merged, 'tag.Damage') !== 100) {
                    throw new Error('Merged should have new Damage value');
                }

                // Merged should have updated Name
                if (NBT.get(merged, 'tag.display.Name') !== 'Super Sword') {
                    throw new Error('Merged should have new Name value');
                }

                // Deep merge should preserve Lore
                if (!NBT.has(merged, 'tag.display.Lore')) {
                    throw new Error('Deep merge should preserve Lore');
                }

                console.log('NBT.merge (deep) works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "NBT.merge deep should work")
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }
}
