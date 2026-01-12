package com.example.puppeteer

import com.rhett.rhettjs.extension.RhettJSExtension
import com.rhett.rhettjs.extension.ScriptContext
import org.graalvm.polyglot.proxy.ProxyObject
import org.graalvm.polyglot.proxy.ProxyExecutable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Example RhettJS extension for puppeteering/mannequin control.
 *
 * Demonstrates:
 * - Multi-function API
 * - Using RhettJS managers and adapters
 * - Async operations with Promises
 * - TypeScript definition integration
 * - Context-aware availability
 */
object PuppeteerExtension {

    fun init() {
        RhettJSExtension.registerModule(
            RhettJSExtension.ModuleConfig(
                moduleName = "puppeteer",
                apiFactory = { ctx -> PuppeteerAPI(ctx).toProxyObject() },
                typeDefinitionProvider = {
                    listOf(
                        "puppeteer.d.ts" to loadResource("/types/puppeteer.d.ts"),
                        "types.d.ts" to loadResource("/types/types.d.ts"),
                        "controllers/bot.d.ts" to loadResource("/types/controllers/bot.d.ts")
                    )
                },
                availableIn = setOf(ScriptContext.SERVER, ScriptContext.COMMAND),
                priority = 0
            )
        )
    }

    private fun loadResource(path: String): String {
        return PuppeteerExtension::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()
            ?: throw IllegalStateException("Resource not found: $path")
    }
}

/**
 * Puppeteer JavaScript API implementation.
 */
class PuppeteerAPI(private val ctx: RhettJSExtension.ExtensionContext) {

    private val manager = PuppeteerManager(ctx.server)

    fun toProxyObject(): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            /**
             * Spawn a puppet at the given position.
             * Returns a Promise that resolves to the puppet data.
             */
            "spawn" to ProxyExecutable { args ->
                // Parse position from JS object {x, y, z}
                val posObj = args[0]
                val x = posObj.getMember("x").asDouble()
                val y = posObj.getMember("y").asDouble()
                val z = posObj.getMember("z").asDouble()
                val pos = Vec3(x, y, z)

                // Parse optional options
                val options = if (args.size > 1 && !args[1].isNull) {
                    val optsObj = args[1]
                    PuppetOptions(
                        name = if (optsObj.hasMember("name")) optsObj.getMember("name").asString() else null,
                        skin = if (optsObj.hasMember("skin")) optsObj.getMember("skin").asString() else null
                    )
                } else {
                    PuppetOptions()
                }

                // Spawn async (world operations must be on server thread)
                val future = manager.spawnPuppet(pos, options)

                // Convert Future to Promise
                ctx.builtins.convertFutureToPromise(ctx.graalContext, future.thenApply { puppet ->
                    // Convert puppet to JS object
                    ctx.builtins.createProxyObject(mapOf(
                        "id" to puppet.id.toString(),
                        "name" to puppet.name,
                        "position" to ctx.builtins.createPositionObject(puppet.position)
                    ))
                })
            },

            /**
             * Get a controller for an existing puppet.
             * Returns null if puppet not found.
             */
            "control" to ProxyExecutable { args ->
                val id = args[0].asString()
                val puppet = manager.getPuppet(UUID.fromString(id))

                if (puppet != null) {
                    createPuppetController(puppet)
                } else {
                    null
                }
            },

            /**
             * List all active puppets.
             * Returns array of puppet data objects.
             */
            "list" to ProxyExecutable { _ ->
                val puppets = manager.getAllPuppets()
                puppets.map { puppet ->
                    ctx.builtins.createProxyObject(mapOf(
                        "id" to puppet.id.toString(),
                        "name" to puppet.name,
                        "position" to ctx.builtins.createPositionObject(puppet.position)
                    ))
                }
            },

            /**
             * Remove a puppet by ID.
             */
            "remove" to ProxyExecutable { args ->
                val id = args[0].asString()
                manager.removePuppet(UUID.fromString(id))
            },

            /**
             * Remove all puppets.
             */
            "removeAll" to ProxyExecutable { _ ->
                manager.removeAll()
                null
            },

            /**
             * Debug mode enabled?
             */
            "debugMode" to ctx.config.debug
        ))
    }

    /**
     * Create a controller object for a puppet.
     */
    private fun createPuppetController(puppet: Puppet): ProxyObject {
        return ProxyObject.fromMap(mapOf(
            /**
             * Move puppet to position (async).
             */
            "move" to ProxyExecutable { args ->
                val posObj = args[0]
                val x = posObj.getMember("x").asDouble()
                val y = posObj.getMember("y").asDouble()
                val z = posObj.getMember("z").asDouble()
                val pos = Vec3(x, y, z)

                val future = puppet.moveTo(pos)
                ctx.builtins.convertFutureToPromise(ctx.graalContext, future)
            },

            /**
             * Rotate puppet (sync).
             */
            "rotate" to ProxyExecutable { args ->
                val yaw = args[0].asFloat()
                val pitch = args[1].asFloat()
                puppet.rotate(yaw, pitch)
                null
            },

            /**
             * Set puppet skin texture.
             */
            "setSkin" to ProxyExecutable { args ->
                val skin = args[0].asString()
                puppet.setSkin(skin)
                null
            },

            /**
             * Set puppet name.
             */
            "setName" to ProxyExecutable { args ->
                val name = args[0].asString()
                puppet.setName(name)
                null
            },

            /**
             * Get puppet's current position.
             */
            "getPosition" to ProxyExecutable { _ ->
                ctx.builtins.createPositionObject(puppet.position)
            },

            /**
             * Remove this puppet.
             */
            "remove" to ProxyExecutable { _ ->
                manager.removePuppet(puppet.id)
                null
            }
        ))
    }
}

/**
 * Puppet manager - handles spawning, tracking, and controlling puppets.
 * This would contain your actual implementation logic.
 */
class PuppeteerManager(private val server: MinecraftServer) {

    private val puppets = ConcurrentHashMap<UUID, Puppet>()

    fun spawnPuppet(pos: Vec3, options: PuppetOptions): CompletableFuture<Puppet> {
        return CompletableFuture.supplyAsync {
            // In real implementation, spawn actual entity on server thread
            val puppet = Puppet(
                id = UUID.randomUUID(),
                name = options.name ?: "Puppet",
                position = pos,
                server = server
            )

            puppets[puppet.id] = puppet
            puppet
        }
    }

    fun getPuppet(id: UUID): Puppet? {
        return puppets[id]
    }

    fun getAllPuppets(): List<Puppet> {
        return puppets.values.toList()
    }

    fun removePuppet(id: UUID) {
        puppets.remove(id)?.cleanup()
    }

    fun removeAll() {
        puppets.values.forEach { it.cleanup() }
        puppets.clear()
    }
}

/**
 * Spawn options for puppets.
 */
data class PuppetOptions(
    val name: String? = null,
    val skin: String? = null
)

/**
 * Puppet data and control.
 * In real implementation, this would wrap/manage an actual Entity.
 */
class Puppet(
    val id: UUID,
    var name: String,
    var position: Vec3,
    private val server: MinecraftServer
) {

    fun moveTo(pos: Vec3): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            // In real implementation, move actual entity
            position = pos
        }
    }

    fun rotate(yaw: Float, pitch: Float) {
        // In real implementation, rotate actual entity
    }

    fun setSkin(skin: String) {
        // In real implementation, update entity texture
    }

    fun setName(newName: String) {
        this.name = newName
        // In real implementation, update entity custom name
    }

    fun cleanup() {
        // In real implementation, remove entity from world
    }
}
