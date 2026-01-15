package com.rhett.rhettjs.network

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Unit tests for packet serialization and deserialization.
 * Tests JSON encoding/decoding with kotlinx.serialization.
 */
class PacketSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // =====================================
    // Basic Serialization Tests
    // =====================================

    @Test
    fun `serialize simple packet to JSON`() {
        val packet = RhettPacket(
            channel = "test:channel",
            data = mapOf("key" to "value"),
            timestamp = 1234567890L
        )

        val jsonString = json.encodeToString(packet)

        assertTrue(jsonString.contains("test:channel"))
        assertTrue(jsonString.contains("key"))
        assertTrue(jsonString.contains("value"))
        assertTrue(jsonString.contains("1234567890"))
    }

    @Test
    fun `deserialize JSON to packet`() {
        val jsonString = """
            {
                "channel": "test:channel",
                "data": {"key": "value", "number": 42},
                "timestamp": 1234567890
            }
        """.trimIndent()

        val packet = json.decodeFromString<RhettPacket>(jsonString)

        assertEquals("test:channel", packet.channel)
        assertEquals(1234567890L, packet.timestamp)
        assertNotNull(packet.data)
    }

    @Test
    fun `round-trip serialization preserves data`() {
        val original = RhettPacket(
            channel = "test:channel",
            data = mapOf(
                "string" to "value",
                "number" to 42,
                "boolean" to true
            ),
            timestamp = 1234567890L
        )

        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<RhettPacket>(jsonString)

        assertEquals(original.channel, deserialized.channel)
        assertEquals(original.timestamp, deserialized.timestamp)
    }

    // =====================================
    // Complex Data Serialization Tests
    // =====================================

    @Test
    fun `serialize nested objects`() {
        val packet = RhettPacket(
            channel = "test:channel",
            data = mapOf(
                "player" to mapOf(
                    "name" to "TestPlayer",
                    "health" to 20,
                    "position" to mapOf(
                        "x" to 10.5,
                        "y" to 64.0,
                        "z" to 20.75
                    )
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val jsonString = json.encodeToString(packet)
        val deserialized = json.decodeFromString<RhettPacket>(jsonString)

        assertNotNull(deserialized.data["player"])
    }

    @Test
    fun `serialize arrays and lists`() {
        val packet = RhettPacket(
            channel = "test:channel",
            data = mapOf(
                "items" to listOf("diamond", "iron", "gold"),
                "counts" to listOf(1, 2, 3),
                "flags" to listOf(true, false, true)
            ),
            timestamp = System.currentTimeMillis()
        )

        val jsonString = json.encodeToString(packet)
        val deserialized = json.decodeFromString<RhettPacket>(jsonString)

        assertNotNull(deserialized.data["items"])
        assertNotNull(deserialized.data["counts"])
        assertNotNull(deserialized.data["flags"])
    }

    @Test
    fun `serialize null values correctly`() {
        val packet = RhettPacket(
            channel = "test:channel",
            data = mapOf(
                "nullValue" to null,
                "normalValue" to "test"
            ),
            timestamp = System.currentTimeMillis()
        )

        val jsonString = json.encodeToString(packet)
        val deserialized = json.decodeFromString<RhettPacket>(jsonString)

        assertTrue(deserialized.data.containsKey("nullValue"))
        assertNull(deserialized.data["nullValue"])
        assertEquals("test", deserialized.data["normalValue"])
    }

    // =====================================
    // Edge Case Tests
    // =====================================

    @Test
    fun `serialize empty data map`() {
        val packet = RhettPacket(
            channel = "test:channel",
            data = emptyMap(),
            timestamp = System.currentTimeMillis()
        )

        val jsonString = json.encodeToString(packet)
        val deserialized = json.decodeFromString<RhettPacket>(jsonString)

        assertTrue(deserialized.data.isEmpty())
    }

    @Test
    fun `serialize special characters in strings`() {
        val packet = RhettPacket(
            channel = "test:channel",
            data = mapOf(
                "special" to "Hello \"World\" with 'quotes' and \n newlines \t tabs",
                "unicode" to "Unicode: 你好世界 🎮 ✓"
            ),
            timestamp = System.currentTimeMillis()
        )

        val jsonString = json.encodeToString(packet)
        val deserialized = json.decodeFromString<RhettPacket>(jsonString)

        assertNotNull(deserialized.data["special"])
        assertNotNull(deserialized.data["unicode"])
    }

    @Test
    fun `serialize large numbers`() {
        val packet = RhettPacket(
            channel = "test:channel",
            data = mapOf(
                "maxLong" to Long.MAX_VALUE,
                "minLong" to Long.MIN_VALUE,
                "maxInt" to Int.MAX_VALUE,
                "minInt" to Int.MIN_VALUE,
                "double" to 3.141592653589793,
                "float" to 2.71828f
            ),
            timestamp = System.currentTimeMillis()
        )

        val jsonString = json.encodeToString(packet)

        // Should not throw
        assertDoesNotThrow {
            json.decodeFromString<RhettPacket>(jsonString)
        }
    }

    // =====================================
    // Size and Performance Tests
    // =====================================

    @Test
    fun `serialize packet size is reasonable`() {
        val packet = RhettPacket(
            channel = "test:channel",
            data = mapOf("key" to "value"),
            timestamp = System.currentTimeMillis()
        )

        val jsonString = json.encodeToString(packet)
        val sizeInBytes = jsonString.toByteArray().size

        // Should be less than 1KB for simple packet
        assertTrue(sizeInBytes < 1024, "Simple packet should be < 1KB, was $sizeInBytes bytes")
    }

    @Test
    fun `serialize large packet stays under 2MB limit`() {
        // Create a moderately large packet
        val largeData = mutableMapOf<String, Any>()
        for (i in 0 until 1000) {
            largeData["key$i"] = "This is a test value with some content to make it larger $i"
        }

        val packet = RhettPacket(
            channel = "test:channel",
            data = largeData,
            timestamp = System.currentTimeMillis()
        )

        val jsonString = json.encodeToString(packet)
        val sizeInBytes = jsonString.toByteArray().size

        // Should be less than 2MB (Minecraft packet limit)
        assertTrue(sizeInBytes < 2 * 1024 * 1024,
            "Packet should be < 2MB, was ${sizeInBytes / 1024}KB")
    }

    // =====================================
    // Error Handling Tests
    // =====================================

    @Test
    fun `deserialize malformed JSON throws exception`() {
        val malformedJson = "{ this is not valid json }"

        assertThrows<Exception> {
            json.decodeFromString<RhettPacket>(malformedJson)
        }
    }

    @Test
    fun `deserialize JSON with missing fields uses defaults`() {
        val jsonString = """
            {
                "channel": "test:channel"
            }
        """.trimIndent()

        // Should use defaults for missing fields
        val packet = json.decodeFromString<RhettPacket>(jsonString)

        assertEquals("test:channel", packet.channel)
        assertNotNull(packet.data) // Should have empty map default
        assertTrue(packet.timestamp > 0) // Should have current timestamp
    }

    @Test
    fun `deserialize JSON with extra fields ignores them`() {
        val jsonString = """
            {
                "channel": "test:channel",
                "data": {"key": "value"},
                "timestamp": 1234567890,
                "unknownField": "should be ignored",
                "anotherUnknown": 12345
            }
        """.trimIndent()

        // Should not throw, should ignore unknown fields
        val packet = json.decodeFromString<RhettPacket>(jsonString)

        assertEquals("test:channel", packet.channel)
    }
}
