package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for World API bindings exposed to JavaScript via GraalVM.
 * Tests async world operations (blocks, entities, players, time/weather).
 */
class WorldAPIBindingsTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    @Test
    fun `test World API is importable`() {
        val script = ScriptInfo(
            name = "test-world-import.js",
            path = createTempScript("""
                import World from 'World';

                if (typeof World !== 'object') {
                    throw new Error('World should be an object');
                }

                const methods = [
                    'getBlock', 'setBlock', 'fill', 'replace',
                    'getEntities', 'spawnEntity', 'getPlayers', 'getPlayer',
                    'getTime', 'setTime', 'getWeather', 'setWeather'
                ];

                for (const method of methods) {
                    if (typeof World[method] !== 'function') {
                        throw new Error('World.' + method + ' should be a function');
                    }
                }

                console.log('World API import works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World API should be importable with all methods")
    }

    @Test
    fun `test World dimensions property is array`() {
        val script = ScriptInfo(
            name = "test-world-dimensions.js",
            path = createTempScript("""
                import World from 'World';

                if (!Array.isArray(World.dimensions)) {
                    throw new Error('World.dimensions should be an array');
                }

                if (World.dimensions.length === 0) {
                    throw new Error('World.dimensions should not be empty');
                }

                // Should contain standard dimensions
                const expected = ['minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end'];
                for (const dim of expected) {
                    if (!World.dimensions.includes(dim)) {
                        throw new Error('World.dimensions should include ' + dim);
                    }
                }

                console.log('World.dimensions is correct');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.dimensions should be an array")
    }

    @Test
    fun `test World getBlock returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-getblock-promise.js",
            path = createTempScript("""
                import World from 'World';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise = World.getBlock(pos);

                if (!(promise instanceof Promise)) {
                    throw new Error('World.getBlock should return a Promise');
                }

                console.log('World.getBlock returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.getBlock should return Promise")
    }

    @Test
    fun `test World setBlock returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-setblock-promise.js",
            path = createTempScript("""
                import World from 'World';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise = World.setBlock(pos, 'minecraft:stone');

                if (!(promise instanceof Promise)) {
                    throw new Error('World.setBlock should return a Promise');
                }

                console.log('World.setBlock returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.setBlock should return Promise")
    }

    @Test
    fun `test World setBlock accepts optional properties`() {
        val script = ScriptInfo(
            name = "test-world-setblock-properties.js",
            path = createTempScript("""
                import World from 'World';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise1 = World.setBlock(pos, 'minecraft:stone');
                const promise2 = World.setBlock(pos, 'minecraft:lever', { facing: 'north' });

                if (!(promise1 instanceof Promise)) {
                    throw new Error('setBlock without properties should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('setBlock with properties should return Promise');
                }

                console.log('World.setBlock accepts optional properties');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.setBlock should accept optional properties")
    }

    @Test
    fun `test World fill returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-fill-promise.js",
            path = createTempScript("""
                import World from 'World';

                const pos1 = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const pos2 = { x: 5, y: 69, z: 5, dimension: 'minecraft:overworld' };
                const promise = World.fill(pos1, pos2, 'minecraft:air');

                if (!(promise instanceof Promise)) {
                    throw new Error('World.fill should return a Promise');
                }

                console.log('World.fill returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.fill should return Promise")
    }

    @Test
    fun `test World replace returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-replace-promise.js",
            path = createTempScript("""
                import World from 'World';

                const pos1 = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const pos2 = { x: 5, y: 69, z: 5, dimension: 'minecraft:overworld' };
                const promise = World.replace(pos1, pos2, 'minecraft:stone', 'minecraft:dirt');

                if (!(promise instanceof Promise)) {
                    throw new Error('World.replace should return a Promise');
                }

                console.log('World.replace returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.replace should return Promise")
    }

    @Test
    fun `test World getEntities returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-getentities-promise.js",
            path = createTempScript("""
                import World from 'World';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise = World.getEntities(pos, 50);

                if (!(promise instanceof Promise)) {
                    throw new Error('World.getEntities should return a Promise');
                }

                console.log('World.getEntities returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.getEntities should return Promise")
    }

    @Test
    fun `test World spawnEntity returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-spawnentity-promise.js",
            path = createTempScript("""
                import World from 'World';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise = World.spawnEntity(pos, 'minecraft:zombie');

                if (!(promise instanceof Promise)) {
                    throw new Error('World.spawnEntity should return a Promise');
                }

                console.log('World.spawnEntity returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.spawnEntity should return Promise")
    }

    @Test
    fun `test World spawnEntity accepts optional NBT`() {
        val script = ScriptInfo(
            name = "test-world-spawnentity-nbt.js",
            path = createTempScript("""
                import World from 'World';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise1 = World.spawnEntity(pos, 'minecraft:zombie');
                const promise2 = World.spawnEntity(pos, 'minecraft:zombie', { CustomName: 'Bob' });

                if (!(promise1 instanceof Promise)) {
                    throw new Error('spawnEntity without NBT should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('spawnEntity with NBT should return Promise');
                }

                console.log('World.spawnEntity accepts optional NBT');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.spawnEntity should accept optional NBT")
    }

    @Test
    fun `test World getPlayers returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-getplayers-promise.js",
            path = createTempScript("""
                import World from 'World';

                const promise = World.getPlayers();

                if (!(promise instanceof Promise)) {
                    throw new Error('World.getPlayers should return a Promise');
                }

                console.log('World.getPlayers returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.getPlayers should return Promise")
    }

    @Test
    fun `test World getPlayer returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-getplayer-promise.js",
            path = createTempScript("""
                import World from 'World';

                const promise = World.getPlayer('Steve');

                if (!(promise instanceof Promise)) {
                    throw new Error('World.getPlayer should return a Promise');
                }

                console.log('World.getPlayer returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.getPlayer should return Promise")
    }

    @Test
    fun `test World getTime returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-gettime-promise.js",
            path = createTempScript("""
                import World from 'World';

                const promise = World.getTime();

                if (!(promise instanceof Promise)) {
                    throw new Error('World.getTime should return a Promise');
                }

                console.log('World.getTime returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.getTime should return Promise")
    }

    @Test
    fun `test World getTime accepts optional dimension`() {
        val script = ScriptInfo(
            name = "test-world-gettime-dimension.js",
            path = createTempScript("""
                import World from 'World';

                const promise1 = World.getTime();
                const promise2 = World.getTime('minecraft:overworld');

                if (!(promise1 instanceof Promise)) {
                    throw new Error('getTime without dimension should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('getTime with dimension should return Promise');
                }

                console.log('World.getTime accepts optional dimension');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.getTime should accept optional dimension")
    }

    @Test
    fun `test World setTime returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-settime-promise.js",
            path = createTempScript("""
                import World from 'World';

                const promise = World.setTime(6000);

                if (!(promise instanceof Promise)) {
                    throw new Error('World.setTime should return a Promise');
                }

                console.log('World.setTime returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.setTime should return Promise")
    }

    @Test
    fun `test World setTime accepts optional dimension`() {
        val script = ScriptInfo(
            name = "test-world-settime-dimension.js",
            path = createTempScript("""
                import World from 'World';

                const promise1 = World.setTime(6000);
                const promise2 = World.setTime(6000, 'minecraft:overworld');

                if (!(promise1 instanceof Promise)) {
                    throw new Error('setTime without dimension should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('setTime with dimension should return Promise');
                }

                console.log('World.setTime accepts optional dimension');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.setTime should accept optional dimension")
    }

    @Test
    fun `test World getWeather returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-getweather-promise.js",
            path = createTempScript("""
                import World from 'World';

                const promise = World.getWeather();

                if (!(promise instanceof Promise)) {
                    throw new Error('World.getWeather should return a Promise');
                }

                console.log('World.getWeather returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.getWeather should return Promise")
    }

    @Test
    fun `test World getWeather accepts optional dimension`() {
        val script = ScriptInfo(
            name = "test-world-getweather-dimension.js",
            path = createTempScript("""
                import World from 'World';

                const promise1 = World.getWeather();
                const promise2 = World.getWeather('minecraft:overworld');

                if (!(promise1 instanceof Promise)) {
                    throw new Error('getWeather without dimension should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('getWeather with dimension should return Promise');
                }

                console.log('World.getWeather accepts optional dimension');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.getWeather should accept optional dimension")
    }

    @Test
    fun `test World setWeather returns Promise`() {
        val script = ScriptInfo(
            name = "test-world-setweather-promise.js",
            path = createTempScript("""
                import World from 'World';

                const promise = World.setWeather('clear');

                if (!(promise instanceof Promise)) {
                    throw new Error('World.setWeather should return a Promise');
                }

                console.log('World.setWeather returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.setWeather should return Promise")
    }

    @Test
    fun `test World setWeather accepts optional dimension`() {
        val script = ScriptInfo(
            name = "test-world-setweather-dimension.js",
            path = createTempScript("""
                import World from 'World';

                const promise1 = World.setWeather('clear');
                const promise2 = World.setWeather('rain', 'minecraft:overworld');

                if (!(promise1 instanceof Promise)) {
                    throw new Error('setWeather without dimension should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('setWeather with dimension should return Promise');
                }

                console.log('World.setWeather accepts optional dimension');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "World.setWeather should accept optional dimension")
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }
}
