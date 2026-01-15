package com.rhett.rhettjs.network

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for PacketData and PacketContext classes.
 * Tests data model validation and structure.
 */
class PacketDataTest {

    // =====================================
    // PacketData Tests
    // =====================================

    @Test
    fun `PacketData can be created with valid channel and data`() {
        val packet = PacketData(
            channel = "test:channel",
            data = mapOf("key" to "value")
        )

        assertEquals("test:channel", packet.channel)
        assertEquals("value", packet.data["key"])
    }

    @Test
    fun `PacketData accepts empty data map`() {
        val packet = PacketData(
            channel = "test:channel",
            data = emptyMap()
        )

        assertEquals("test:channel", packet.channel)
        assertTrue(packet.data.isEmpty())
    }

    @Test
    fun `PacketData handles complex nested data`() {
        val complexData = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "null" to null,
            "nested" to mapOf(
                "inner" to "data"
            ),
            "array" to listOf(1, 2, 3)
        )

        val packet = PacketData(
            channel = "test:channel",
            data = complexData
        )

        assertEquals(6, packet.data.size)
        assertEquals("value", packet.data["string"])
        assertEquals(42, packet.data["number"])
        assertEquals(true, packet.data["boolean"])
        assertNull(packet.data["null"])
        assertNotNull(packet.data["nested"])
        assertNotNull(packet.data["array"])
    }

    @Test
    fun `PacketData rejects invalid channel names`() {
        assertThrows<IllegalArgumentException> {
            PacketData(
                channel = "invalid channel with spaces",
                data = emptyMap()
            )
        }
    }

    @Test
    fun `PacketData accepts namespaced channels`() {
        val packet = PacketData(
            channel = "mymod:custom_channel-123",
            data = emptyMap()
        )

        assertEquals("mymod:custom_channel-123", packet.channel)
    }

    // =====================================
    // PacketContext Tests
    // =====================================

    @Test
    fun `PacketContext can be created with all fields`() {
        val context = PacketContext(
            senderUuid = "123e4567-e89b-12d3-a456-426614174000",
            senderName = "TestPlayer",
            position = mapOf("x" to 10.0, "y" to 64.0, "z" to 20.0),
            timestamp = 1234567890L,
            channel = "test:channel"
        )

        assertEquals("123e4567-e89b-12d3-a456-426614174000", context.senderUuid)
        assertEquals("TestPlayer", context.senderName)
        assertEquals(1234567890L, context.timestamp)
        assertEquals("test:channel", context.channel)
    }

    @Test
    fun `PacketContext position contains x y z coordinates`() {
        val position = mapOf("x" to 10.5, "y" to 64.0, "z" to 20.75)
        val context = PacketContext(
            senderUuid = "uuid",
            senderName = "Player",
            position = position,
            timestamp = 0L,
            channel = "test"
        )

        val pos = context.position as Map<*, *>
        assertEquals(10.5, pos["x"])
        assertEquals(64.0, pos["y"])
        assertEquals(20.75, pos["z"])
    }

    @Test
    fun `PacketContext can handle null position`() {
        val context = PacketContext(
            senderUuid = "uuid",
            senderName = "Server",
            position = null,
            timestamp = 0L,
            channel = "test"
        )

        assertNull(context.position)
    }

    // =====================================
    // Channel Validation Tests
    // =====================================

    @Test
    fun `validateChannel accepts valid namespaced channels`() {
        assertTrue(PacketData.validateChannel("rhettjs:custom"))
        assertTrue(PacketData.validateChannel("mymod:my_channel"))
        assertTrue(PacketData.validateChannel("test:channel-123"))
    }

    @Test
    fun `validateChannel accepts simple channel names`() {
        assertTrue(PacketData.validateChannel("simple"))
        assertTrue(PacketData.validateChannel("my_channel"))
        assertTrue(PacketData.validateChannel("channel123"))
    }

    @Test
    fun `validateChannel rejects invalid characters`() {
        assertFalse(PacketData.validateChannel("invalid channel"))  // space
        assertFalse(PacketData.validateChannel("invalid@channel"))  // @
        assertFalse(PacketData.validateChannel("invalid#channel"))  // #
        assertFalse(PacketData.validateChannel("invalid!channel"))  // !
    }

    @Test
    fun `validateChannel rejects empty strings`() {
        assertFalse(PacketData.validateChannel(""))
    }

    @Test
    fun `validateChannel rejects multiple colons`() {
        assertFalse(PacketData.validateChannel("mod:sub:channel"))
    }
}
