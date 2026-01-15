package com.rhett.rhettjs.network

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertThrows
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Unit tests for NetworkManager.
 * Tests packet routing, handler registration, and channel management.
 */
class NetworkManagerTest {

    private lateinit var networkManager: NetworkManager

    @BeforeEach
    fun setup() {
        networkManager = NetworkManager()
    }

    @AfterEach
    fun cleanup() {
        networkManager.clearAllHandlers()
    }

    // =====================================
    // Channel Registration Tests
    // =====================================

    @Test
    fun `registerHandler adds handler to channel`() {
        var called = false
        val handler: PacketHandler = { _, _ -> called = true }

        networkManager.registerHandler("test:channel", handler)

        // Trigger the handler
        val packet = PacketData("test:channel", mapOf("key" to "value"))
        networkManager.routePacket(packet, createTestContext())

        assertTrue(called, "Handler should have been called")
    }

    @Test
    fun `unregisterHandler removes specific handler`() {
        var handler1Called = false
        var handler2Called = false

        val handler1: PacketHandler = { _, _ -> handler1Called = true }
        val handler2: PacketHandler = { _, _ -> handler2Called = true }

        networkManager.registerHandler("test:channel", handler1)
        networkManager.registerHandler("test:channel", handler2)

        // Remove only handler1
        networkManager.unregisterHandler("test:channel", handler1)

        // Trigger handlers
        val packet = PacketData("test:channel", mapOf("key" to "value"))
        networkManager.routePacket(packet, createTestContext())

        assertFalse(handler1Called, "Handler1 should not be called after unregister")
        assertTrue(handler2Called, "Handler2 should still be called")
    }

    @Test
    fun `multiple handlers can be registered to same channel`() {
        var handler1Calls = 0
        var handler2Calls = 0

        networkManager.registerHandler("test:channel") { _, _ -> handler1Calls++ }
        networkManager.registerHandler("test:channel") { _, _ -> handler2Calls++ }

        val packet = PacketData("test:channel", mapOf("key" to "value"))
        networkManager.routePacket(packet, createTestContext())

        assertEquals(1, handler1Calls, "Handler1 should be called once")
        assertEquals(2, handler2Calls, "Handler2 should be called once")
    }

    @Test
    fun `clearAllHandlers removes all registered handlers`() {
        var called = false
        networkManager.registerHandler("test:channel") { _, _ -> called = true }

        networkManager.clearAllHandlers()

        val packet = PacketData("test:channel", mapOf("key" to "value"))
        networkManager.routePacket(packet, createTestContext())

        assertFalse(called, "No handlers should be called after clearAll")
    }

    // =====================================
    // Packet Routing Tests
    // =====================================

    @Test
    fun `routePacket delivers to correct channel handlers`() {
        var channel1Called = false
        var channel2Called = false

        networkManager.registerHandler("channel1") { _, _ -> channel1Called = true }
        networkManager.registerHandler("channel2") { _, _ -> channel2Called = true }

        val packet1 = PacketData("channel1", mapOf("test" to "data"))
        networkManager.routePacket(packet1, createTestContext())

        assertTrue(channel1Called, "Channel1 handler should be called")
        assertFalse(channel2Called, "Channel2 handler should not be called")
    }

    @Test
    fun `routePacket with no handlers does not throw error`() {
        val packet = PacketData("nonexistent:channel", mapOf("test" to "data"))

        // Should not throw
        assertDoesNotThrow {
            networkManager.routePacket(packet, createTestContext())
        }
    }

    @Test
    fun `routePacket passes correct data to handler`() {
        val testData = mapOf("key1" to "value1", "key2" to 42, "key3" to true)
        var receivedData: Map<String, Any>? = null

        networkManager.registerHandler("test:channel") { data, _ ->
            receivedData = data
        }

        val packet = PacketData("test:channel", testData)
        networkManager.routePacket(packet, createTestContext())

        assertEquals(testData, receivedData, "Handler should receive correct data")
    }

    @Test
    fun `routePacket passes correct context to handler`() {
        val testContext = PacketContext(
            senderUuid = UUID.randomUUID().toString(),
            senderName = "TestPlayer",
            position = mapOf("x" to 10.0, "y" to 64.0, "z" to 20.0),
            timestamp = System.currentTimeMillis(),
            channel = "test:channel"
        )
        var receivedContext: PacketContext? = null

        networkManager.registerHandler("test:channel") { _, context ->
            receivedContext = context
        }

        val packet = PacketData("test:channel", mapOf("key" to "value"))
        networkManager.routePacket(packet, testContext)

        assertEquals(testContext.senderUuid, receivedContext?.senderUuid)
        assertEquals(testContext.senderName, receivedContext?.senderName)
        assertEquals(testContext.channel, receivedContext?.channel)
    }

    // =====================================
    // Auto-Namespacing Tests
    // =====================================

    @Test
    fun `applyNamespace adds default namespace to simple channel name`() {
        val result = networkManager.applyNamespace("custom", namespace = "rhettjs")

        assertEquals("rhettjs:custom", result, "Should prepend namespace")
    }

    @Test
    fun `applyNamespace does not modify already namespaced channel`() {
        val result = networkManager.applyNamespace("mymod:custom", namespace = "rhettjs")

        assertEquals("mymod:custom", result, "Should not modify already namespaced channel")
    }

    @Test
    fun `applyNamespace handles empty namespace`() {
        val result = networkManager.applyNamespace("custom", namespace = "")

        assertEquals("custom", result, "Empty namespace should not modify channel")
    }

    @Test
    fun `applyNamespace handles special characters in channel name`() {
        val result = networkManager.applyNamespace("my_channel-123", namespace = "rhettjs")

        assertEquals("rhettjs:my_channel-123", result, "Should handle underscores and hyphens")
    }

    @Test
    fun `applyNamespace rejects invalid characters`() {
        assertThrows<IllegalArgumentException> {
            networkManager.applyNamespace("invalid channel", namespace = "rhettjs")
        }
    }

    // =====================================
    // Packet Serialization Tests
    // =====================================

    @Test
    fun `serializePacket produces valid JSON`() {
        val packet = PacketData(
            channel = "test:channel",
            data = mapOf("key" to "value", "number" to 42)
        )

        val json = networkManager.serializePacket(packet)

        assertNotNull(json, "Serialized JSON should not be null")
        assertTrue(json.contains("test:channel"), "JSON should contain channel name")
        assertTrue(json.contains("value"), "JSON should contain data")
    }

    @Test
    fun `deserializePacket reconstructs PacketData correctly`() {
        val originalPacket = PacketData(
            channel = "test:channel",
            data = mapOf("key" to "value", "number" to 42)
        )

        val json = networkManager.serializePacket(originalPacket)
        val reconstructed = networkManager.deserializePacket(json)

        assertEquals(originalPacket.channel, reconstructed.channel)
        assertEquals(originalPacket.data["key"], reconstructed.data["key"])
        assertEquals(originalPacket.data["number"], reconstructed.data["number"])
    }

    @Test
    fun `serializePacket handles nested objects`() {
        val packet = PacketData(
            channel = "test:channel",
            data = mapOf(
                "nested" to mapOf(
                    "inner" to "value",
                    "count" to 10
                ),
                "array" to listOf(1, 2, 3)
            )
        )

        val json = networkManager.serializePacket(packet)
        val reconstructed = networkManager.deserializePacket(json)

        assertNotNull(reconstructed.data["nested"])
        assertNotNull(reconstructed.data["array"])
    }

    @Test
    fun `serializePacket handles null values`() {
        val packet = PacketData(
            channel = "test:channel",
            data = mapOf("nullValue" to null, "normalValue" to "test")
        )

        val json = networkManager.serializePacket(packet)
        val reconstructed = networkManager.deserializePacket(json)

        assertNull(reconstructed.data["nullValue"])
        assertEquals("test", reconstructed.data["normalValue"])
    }

    @Test
    fun `deserializePacket throws on invalid JSON`() {
        val invalidJson = "{ this is not valid json }"

        assertThrows<Exception> {
            networkManager.deserializePacket(invalidJson)
        }
    }

    @Test
    fun `serializePacket handles empty data`() {
        val packet = PacketData(
            channel = "test:channel",
            data = emptyMap()
        )

        val json = networkManager.serializePacket(packet)
        val reconstructed = networkManager.deserializePacket(json)

        assertEquals(0, reconstructed.data.size, "Empty data should be preserved")
    }

    // =====================================
    // Context Creation Tests
    // =====================================

    @Test
    fun `createContext builds valid PacketContext`() {
        val uuid = UUID.randomUUID().toString()
        val context = networkManager.createContext(
            senderUuid = uuid,
            senderName = "TestPlayer",
            position = Position(10.0, 64.0, 20.0),
            channel = "test:channel"
        )

        assertEquals(uuid, context.senderUuid)
        assertEquals("TestPlayer", context.senderName)
        assertEquals("test:channel", context.channel)
        assertTrue(context.timestamp > 0, "Timestamp should be set")
    }

    @Test
    fun `createContext converts Position to Map`() {
        val context = networkManager.createContext(
            senderUuid = UUID.randomUUID().toString(),
            senderName = "TestPlayer",
            position = Position(10.5, 64.0, 20.75),
            channel = "test:channel"
        )

        val position = context.position as Map<*, *>
        assertEquals(10.5, position["x"])
        assertEquals(64.0, position["y"])
        assertEquals(20.75, position["z"])
    }

    // =====================================
    // Error Handling Tests
    // =====================================

    @Test
    fun `handler exception does not stop other handlers`() {
        var handler1Called = false
        var handler2Called = false

        networkManager.registerHandler("test:channel") { _, _ ->
            handler1Called = true
            throw RuntimeException("Handler1 failed")
        }
        networkManager.registerHandler("test:channel") { _, _ ->
            handler2Called = true
        }

        val packet = PacketData("test:channel", mapOf("key" to "value"))

        // Should not throw, should continue to handler2
        assertDoesNotThrow {
            networkManager.routePacket(packet, createTestContext())
        }

        assertTrue(handler1Called, "Handler1 should be called")
        assertTrue(handler2Called, "Handler2 should still be called despite handler1 error")
    }

    // =====================================
    // Helper Methods
    // =====================================

    private fun createTestContext(): PacketContext {
        return PacketContext(
            senderUuid = UUID.randomUUID().toString(),
            senderName = "TestPlayer",
            position = mapOf("x" to 0.0, "y" to 64.0, "z" to 0.0),
            timestamp = System.currentTimeMillis(),
            channel = "test:channel"
        )
    }

    // Simple Position class for testing
    data class Position(val x: Double, val y: Double, val z: Double)
}
