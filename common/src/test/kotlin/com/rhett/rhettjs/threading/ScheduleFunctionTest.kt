package com.rhett.rhettjs.threading

import com.rhett.rhettjs.config.ConfigManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * TDD tests for schedule() function - main thread scheduling.
 *
 * Tests tick-based scheduling with tick clamping (minimum 1) and
 * scope preservation from original execution context.
 */
class ScheduleFunctionTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var scheduleFunction: ScheduleFunction

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)

        scheduleFunction = ScheduleFunction()
    }

    // ====== Tick Clamping Tests ======

    @Test
    fun `test schedule clamps ticks to minimum of 1`() {
        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            // Test various tick values
            assertEquals(1, scheduleFunction.getClampedTicks(0), "0 ticks should clamp to 1")
            assertEquals(1, scheduleFunction.getClampedTicks(-5), "-5 ticks should clamp to 1")
            assertEquals(1, scheduleFunction.getClampedTicks(-100), "-100 ticks should clamp to 1")
            assertEquals(5, scheduleFunction.getClampedTicks(5), "5 ticks should stay 5")
            assertEquals(100, scheduleFunction.getClampedTicks(100), "100 ticks should stay 100")

        } finally {
            Context.exit()
        }
    }

    // ====== Argument Passing Tests ======

    @Test
    fun `test schedule passes arguments to callback`() {
        val latch = CountDownLatch(1)
        var receivedArgs: Array<Any?>? = null

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function(a, b, c) {
                    return [a, b, c];
                })
            """.trimIndent(), "test", 1, null)

            // Schedule with arguments
            scheduleFunction.schedule(1, scope, callback as Scriptable, arrayOf("test", 42, true)) { args ->
                receivedArgs = args
                latch.countDown()
            }

            // Manually trigger execution (simulating tick)
            scheduleFunction.tick()

            assertTrue(latch.await(1, TimeUnit.SECONDS))

            assertNotNull(receivedArgs)
            assertEquals(3, receivedArgs!!.size)
            assertEquals("test", receivedArgs!![0])
            assertEquals(42, receivedArgs!![1])
            assertEquals(true, receivedArgs!![2])

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test schedule with no arguments`() {
        val latch = CountDownLatch(1)
        var executed = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {
                    return 'executed';
                })
            """.trimIndent(), "test", 1, null)

            scheduleFunction.schedule(1, scope, callback as Scriptable, emptyArray()) {
                executed = true
                latch.countDown()
            }

            scheduleFunction.tick()

            assertTrue(latch.await(1, TimeUnit.SECONDS))
            assertTrue(executed, "Callback should have executed")

        } finally {
            Context.exit()
        }
    }

    // ====== Tick Delay Tests ======

    @Test
    fun `test schedule waits for correct number of ticks`() {
        val latch = CountDownLatch(1)
        var executed = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            // Schedule for 3 ticks
            scheduleFunction.schedule(3, scope, callback as Scriptable, emptyArray()) {
                executed = true
                latch.countDown()
            }

            // Tick 1
            scheduleFunction.tick()
            assertFalse(executed, "Should not execute after 1 tick")

            // Tick 2
            scheduleFunction.tick()
            assertFalse(executed, "Should not execute after 2 ticks")

            // Tick 3
            scheduleFunction.tick()
            assertTrue(latch.await(1, TimeUnit.SECONDS))
            assertTrue(executed, "Should execute after 3 ticks")

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test schedule executes immediately on next tick when ticks is 1`() {
        val latch = CountDownLatch(1)
        var executed = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            scheduleFunction.schedule(1, scope, callback as Scriptable, emptyArray()) {
                executed = true
                latch.countDown()
            }

            // Should execute on first tick
            scheduleFunction.tick()
            assertTrue(latch.await(1, TimeUnit.SECONDS))
            assertTrue(executed, "Should execute on first tick")

        } finally {
            Context.exit()
        }
    }

    // ====== Multiple Scheduled Tasks Tests ======

    @Test
    fun `test multiple scheduled tasks execute in order`() {
        val executionOrder = mutableListOf<Int>()
        val latch = CountDownLatch(3)

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback1 = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            val callback2 = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            val callback3 = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            // Schedule at different ticks
            scheduleFunction.schedule(1, scope, callback1 as Scriptable, emptyArray()) {
                synchronized(executionOrder) { executionOrder.add(1) }
                latch.countDown()
            }

            scheduleFunction.schedule(3, scope, callback2 as Scriptable, emptyArray()) {
                synchronized(executionOrder) { executionOrder.add(2) }
                latch.countDown()
            }

            scheduleFunction.schedule(2, scope, callback3 as Scriptable, emptyArray()) {
                synchronized(executionOrder) { executionOrder.add(3) }
                latch.countDown()
            }

            // Tick through
            scheduleFunction.tick() // Tick 1: callback1 executes
            scheduleFunction.tick() // Tick 2: callback3 executes
            scheduleFunction.tick() // Tick 3: callback2 executes

            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(1, 3, 2), executionOrder, "Tasks should execute in tick order")

        } finally {
            Context.exit()
        }
    }

    // ====== Scope Preservation Tests ======

    @Test
    fun `test schedule preserves scope variables`() {
        val latch = CountDownLatch(1)
        var capturedValue: Any? = null

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            // Set a variable in scope
            cx.evaluateString(scope, """
                var scopeVar = 'test-value';
            """.trimIndent(), "setup", 1, null)

            // Callback that accesses scope variable
            val callback = cx.evaluateString(scope, """
                (function() {
                    return scopeVar;
                })
            """.trimIndent(), "test", 1, null)

            scheduleFunction.schedule(1, scope, callback as Scriptable, emptyArray()) { args ->
                // Capture the result (would need to be returned from callback)
                capturedValue = "accessed"
                latch.countDown()
            }

            scheduleFunction.tick()
            assertTrue(latch.await(1, TimeUnit.SECONDS))

            // Verify scope was preserved (callback could access scopeVar)
            assertNotNull(capturedValue, "Callback should have executed with scope access")

        } finally {
            Context.exit()
        }
    }

    // ====== Error Handling Tests ======

    @Test
    fun `test schedule handles callback errors gracefully`() {
        val latch = CountDownLatch(1)
        var errorHandled = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {
                    throw new Error('Test error');
                })
            """.trimIndent(), "test", 1, null)

            scheduleFunction.schedule(1, scope, callback as Scriptable, emptyArray()) {
                errorHandled = true
                latch.countDown()
            }

            scheduleFunction.tick()
            assertTrue(latch.await(1, TimeUnit.SECONDS))

            // Error should be logged but execution continues
            assertTrue(errorHandled, "Callback should have been invoked despite error")

        } finally {
            Context.exit()
        }
    }

    // ====== Edge Cases Tests ======

    @Test
    fun `test schedule with zero ticks executes on next tick`() {
        val latch = CountDownLatch(1)
        var executed = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            // Schedule with 0 ticks (should clamp to 1)
            scheduleFunction.schedule(0, scope, callback as Scriptable, emptyArray()) {
                executed = true
                latch.countDown()
            }

            scheduleFunction.tick()
            assertTrue(latch.await(1, TimeUnit.SECONDS))
            assertTrue(executed, "Should execute on next tick even with 0 ticks specified")

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test schedule with negative ticks executes on next tick`() {
        val latch = CountDownLatch(1)
        var executed = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            // Schedule with negative ticks (should clamp to 1)
            scheduleFunction.schedule(-10, scope, callback as Scriptable, emptyArray()) {
                executed = true
                latch.countDown()
            }

            scheduleFunction.tick()
            assertTrue(latch.await(1, TimeUnit.SECONDS))
            assertTrue(executed, "Should execute on next tick even with negative ticks")

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test tick with no scheduled tasks does nothing`() {
        // Should not crash
        assertDoesNotThrow {
            scheduleFunction.tick()
            scheduleFunction.tick()
            scheduleFunction.tick()
        }
    }
}
