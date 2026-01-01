package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.api.StructureAPI
import com.rhett.rhettjs.api.WorldAPI
import com.rhett.rhettjs.config.ConfigManager
// TODO: Re-implement event system for GraalVM
// import com.rhett.rhettjs.events.ServerEventsAPI
// import com.rhett.rhettjs.events.StartupEventsAPI
import com.rhett.rhettjs.worldgen.DimensionRegistry
import com.rhett.rhettjs.worldgen.DatapackGenerator
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * Common initialization logic for the script system.
 * Used by both Fabric and NeoForge to avoid code duplication.
 */
object ScriptSystemInitializer {

    /**
     * Initialize startup scripts and globals during mod initialization.
     * This runs BEFORE datapacks load so dimensions are ready.
     */
    fun initializeStartupScripts() {
        RhettJSCommon.LOGGER.info("[RhettJS] Loading startup scripts (mod initialization)...")

        val scriptsDir = getScriptsDirectory(null)
        ConfigManager.debug("Script directory: $scriptsDir")

        // Set scripts directory for module resolution
        GraalEngine.setScriptsDirectory(scriptsDir)

        // Ensure script directories exist
        createDirectories(scriptsDir)

        // Scan for scripts
        RhettJSCommon.LOGGER.info("[RhettJS] Scanning for scripts...")
        ScriptRegistry.scan(scriptsDir)

        // Load global libraries ONCE
        // TODO: Implement globals loading for GraalVM
        // GlobalsLoader.reload(scriptsDir)
        ConfigManager.debug("Loaded global libraries")

        // Execute startup scripts (direct registries like items/blocks)
        // Note: Dimensions use datapack JSON files, not script registration
        executeStartupScripts()

        RhettJSCommon.LOGGER.info("[RhettJS] Startup scripts initialized")
    }

    /**
     * Initialize server resources on server start.
     * Note: Startup and server scripts have already loaded earlier.
     *
     * @param server The Minecraft server instance
     */
    fun initialize(server: MinecraftServer) {
        val serverDirectory = server.serverDirectory
        ConfigManager.debug("Server starting, initializing server resources")

        // Initialize Structure API (needs server directory)
        initializeStructureAPI(serverDirectory)

        // Initialize World API (needs server instance)
        initializeWorldAPI(server)

        // Register custom commands (server scripts have loaded early and registered handlers)
        // TODO: Implement custom command registration for GraalVM
        // com.rhett.rhettjs.commands.CustomCommandRegistry.registerCommands()

        RhettJSCommon.LOGGER.info("[RhettJS] Ready! Use /rjs list to see available scripts")
        ConfigManager.debug("Server resources initialization complete")
    }

    /**
     * Reload scripts (used by /rjs reload command).
     * Reloads: globals, server scripts (via data pack reload), utility scripts (reindex).
     * Does NOT reload: startup scripts (require full restart).
     * Note: Does not reinitialize APIs (Structure, World) as they persist across reloads.
     *
     * @param serverDirectory The server's root directory
     */
    fun reload(serverDirectory: Path) {
        val scriptsDir = getScriptsDirectory(serverDirectory)

        // Update scripts directory for module resolution
        GraalEngine.setScriptsDirectory(scriptsDir)

        RhettJSCommon.LOGGER.info("[RhettJS] Reloading scripts...")

        // Reset GraalVM engine (closes context, clears cached state)
        GraalEngine.reset()

        // Clear server event handlers and globals (NOT startup - those don't reload)
        // TODO: Clear server events for GraalVM
        // ServerEventsAPI.clear()
        // TODO: Clear globals for GraalVM
        // GlobalsLoader.clear()

        // Rescan all scripts (including utility scripts for reindexing)
        RhettJSCommon.LOGGER.info("[RhettJS] Rescanning scripts...")
        ScriptRegistry.scan(scriptsDir)

        // Reload globals
        RhettJSCommon.LOGGER.info("[RhettJS] Reloading globals...")
        // TODO: Implement globals loading for GraalVM
        // GlobalsLoader.reload(scriptsDir)

        // Note: Server scripts execution is handled by caller (RJSCommand or data pack reload)
        // Startup scripts are NOT reloaded (require full server restart)
        RhettJSCommon.LOGGER.info("[RhettJS] Globals reloaded - ready for server script execution")
        RhettJSCommon.LOGGER.warn("[RhettJS] Startup scripts NOT reloaded - full restart required for dimension changes")
    }

    /**
     * Get the scripts directory, checking for testing mode.
     *
     * @param serverDirectory The server directory, or null during mod initialization
     * @return The scripts directory path
     */
    fun getScriptsDirectory(serverDirectory: Path?): Path {
        // During mod init, we don't have server directory yet, use relative path
        val baseScriptsDir = if (serverDirectory == null) {
            Paths.get("rjs")
        } else {
            serverDirectory.resolve("rjs")
        }

        // Check if in-game testing mode is enabled
        return if (ConfigManager.isIngameTestingEnabled()) {
            val testingDir = baseScriptsDir.resolve("testing")
            if (testingDir.exists()) {
                RhettJSCommon.LOGGER.info("[RhettJS] In-game testing mode enabled, using: rjs/testing/")
                ConfigManager.debug("Testing directory exists at: $testingDir")
                testingDir
            } else {
                RhettJSCommon.LOGGER.warn("[RhettJS] In-game testing mode enabled but rjs/testing/ not found, using default: rjs/")
                ConfigManager.debug("Testing directory not found, falling back to: $baseScriptsDir")
                baseScriptsDir
            }
        } else {
            baseScriptsDir
        }
    }

    /**
     * Initialize Structure API.
     * Uses server directory temporarily during initialization.
     * Will be re-initialized with world paths once worlds are loaded.
     */
    private fun initializeStructureAPI(serverDirectory: Path) {
        // Use server directory as temporary location during initialization
        // This allows initialization before worlds are loaded
        val structuresDir = serverDirectory.resolve("structures")
        val structureBackupsDir = serverDirectory.resolve("backups/structures")

        Files.createDirectories(structuresDir)
        Files.createDirectories(structureBackupsDir)

        val structureApi = StructureAPI(structuresDir, structureBackupsDir)
        // TODO: Inject Structure API through GraalEngine bindings
        // val structureWrapper = StructureAPIWrapper(structureApi)
        // ScriptEngine.initializeStructureAPI(structureWrapper)
        ConfigManager.debug("Structure API created (not yet injected to GraalVM): structures=$structuresDir, backups=$structureBackupsDir")
    }

    /**
     * Re-initialize Structure API with world paths.
     * Called after worlds are loaded (SERVER_STARTED event) to use the proper generated directory.
     */
    fun reinitializeWithWorldPaths(server: MinecraftServer) {
        // Get overworld to access world save directory
        val overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD)

        if (overworld == null) {
            RhettJSCommon.LOGGER.warn("[RhettJS] Could not re-initialize Structure API: Overworld not loaded yet")
            ConfigManager.debug("Keeping temporary Structure API paths")
            return
        }

        // Get world path: <world>/generated/minecraft/structures/
        val worldPath = overworld.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
        val structuresDir = worldPath
            .resolve("generated")
            .resolve("minecraft")
            .resolve("structures")
        val structureBackupsDir = worldPath.resolve("backups/structures")

        Files.createDirectories(structuresDir)
        Files.createDirectories(structureBackupsDir)

        val structureApi = StructureAPI(structuresDir, structureBackupsDir)
        // TODO: Inject Structure API through GraalEngine bindings
        // val structureWrapper = StructureAPIWrapper(structureApi)
        // ScriptEngine.initializeStructureAPI(structureWrapper)
        ConfigManager.debug("Structure API re-created (not yet injected to GraalVM): structures=$structuresDir, backups=$structureBackupsDir")
        RhettJSCommon.LOGGER.info("[RhettJS] Structure API ready for world directory: generated/minecraft/structures/")
    }

    /**
     * Initialize World API.
     */
    private fun initializeWorldAPI(server: MinecraftServer) {
        val worldApi = WorldAPI(server)
        // TODO: Inject World API through GraalEngine bindings
        // val worldWrapper = WorldAPIWrapper(worldApi)
        // ScriptEngine.initializeWorldAPI(worldWrapper)
        ConfigManager.debug("World API created (not yet injected to GraalVM)")
    }

    /**
     * Execute startup scripts.
     */
    private fun executeStartupScripts() {
        val startupScripts = ScriptRegistry.getScripts(ScriptCategory.STARTUP)
        if (startupScripts.isNotEmpty()) {
            RhettJSCommon.LOGGER.info("[RhettJS] Executing ${startupScripts.size} startup scripts...")
            startupScripts.forEach { script ->
                try {
                    GraalEngine.executeScript(script)
                    ConfigManager.debug("Executed startup script: ${script.name}")
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Failed to execute startup script: ${script.name}", e)
                }
            }
        }

        ConfigManager.debug("Startup handlers registered")
    }


    /**
     * Create script category directories.
     */
    private fun createDirectories(baseDir: Path) {
        ScriptCategory.values().forEach { category ->
            val dir = baseDir.resolve(category.dirName)
            if (!dir.exists()) {
                Files.createDirectories(dir)
                RhettJSCommon.LOGGER.info("[RhettJS] Created directory: ${category.dirName}/")
                ConfigManager.debug("Created script directory: ${category.dirName}")
            }
        }
    }
}