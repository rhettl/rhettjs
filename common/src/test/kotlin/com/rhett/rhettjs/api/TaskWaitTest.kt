package com.rhett.rhettjs.api

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.threading.TickScheduler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * TDD tests for task.wait() function.
 * Tests scheduling a pause, then resuming on worker thread.
 */
class TaskWaitTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var taskApi: TaskAPI

    @BeforeEach
    fun setup() {
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)

        taskApi = TaskAPI()
    }

    // ====== Basic Execution Tests ======

    @Test
    fun `test task wait schedules callback after ticks`() {
        var callbackExecuted = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            val callback = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any? {
                    callbackExecuted = true
                    return null
                }
            }

            // Call task.wait()
            val waitFunc = taskApi.get("wait", taskApi) as Function
            waitFunc.call(cx, scope, scope, arrayOf(5, callback))

            // Tick 5 times
            repeat(5) {
                TickScheduler.tick()
                Thread.sleep(50) // Give worker thread time
            }

            // Wait for worker thread to execute
            Thread.sleep(200)

            // Callback should have been executed on worker thread
            assertTrue(callbackExecuted, "Callback should have executed after 5 ticks")
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task wait passes arguments to callback`() {
        val latch = CountDownLatch(1)
        var receivedArg1: String? = null
        var receivedArg2: Int? = null

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            // Callback that captures arguments
            val callback = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any? {
                    receivedArg1 = args[0] as String
                    receivedArg2 = (args[1] as Number).toInt()
                    latch.countDown()
                    return null
                }
            }

            // Call task.wait(1, callback, "test", 42)
            val waitFunc = taskApi.get("wait", taskApi) as Function
            waitFunc.call(cx, scope, scope, arrayOf(1, callback, "test", 42))

            // Tick once
            TickScheduler.tick()

            // Wait for callback
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should execute")

            assertEquals("test", receivedArg1)
            assertEquals(42, receivedArg2)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task wait with zero ticks clamps to one`() {
        val latch = CountDownLatch(1)
        var callbackExecuted = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            val callback = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any? {
                    callbackExecuted = true
                    latch.countDown()
                    return null
                }
            }

            // Call task.wait(0, callback)
            val waitFunc = taskApi.get("wait", taskApi) as Function
            waitFunc.call(cx, scope, scope, arrayOf(0, callback))

            // Should not execute yet
            assertFalse(callbackExecuted)

            // Tick once - should execute
            TickScheduler.tick()

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should execute after 1 tick")
            assertTrue(callbackExecuted)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task wait with negative ticks clamps to one`() {
        val latch = CountDownLatch(1)
        var callbackExecuted = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            val callback = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any? {
                    callbackExecuted = true
                    latch.countDown()
                    return null
                }
            }

            // Call task.wait(-5, callback)
            val waitFunc = taskApi.get("wait", taskApi) as Function
            waitFunc.call(cx, scope, scope, arrayOf(-5, callback))

            // Should not execute yet
            assertFalse(callbackExecuted)

            // Tick once - should execute
            TickScheduler.tick()

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should execute after 1 tick")
            assertTrue(callbackExecuted)
        } finally {
            Context.exit()
        }
    }

    // ====== Error Handling Tests ======

    @Test
    fun `test task wait requires ticks argument`() {
        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            val callback = object : org.mozilla.javascript.BaseFunction() {
                override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? = null
            }

            val waitFunc = taskApi.get("wait", taskApi) as Function

            // Should throw when missing arguments
            val exception = assertThrows(IllegalArgumentException::class.java) {
                waitFunc.call(cx, scope, scope, arrayOf(callback))
            }

            assertEquals("task.wait() requires ticks and callback", exception.message)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task wait requires callback argument`() {
        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            val waitFunc = taskApi.get("wait", taskApi) as Function

            // Should throw when missing callback
            val exception = assertThrows(IllegalArgumentException::class.java) {
                waitFunc.call(cx, scope, scope, arrayOf(5))
            }

            assertEquals("task.wait() requires ticks and callback", exception.message)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task wait requires ticks to be a number`() {
        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            val callback = object : org.mozilla.javascript.BaseFunction() {
                override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? = null
            }
            val waitFunc = taskApi.get("wait", taskApi) as Function

            // Should throw when ticks is not a number
            val exception = assertThrows(IllegalArgumentException::class.java) {
                waitFunc.call(cx, scope, scope, arrayOf("not-a-number", callback))
            }

            assertEquals("First argument to task.wait() must be a number (ticks)", exception.message)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task wait requires callback to be a function`() {
        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            val waitFunc = taskApi.get("wait", taskApi) as Function

            // Should throw when callback is not a function
            val exception = assertThrows(IllegalArgumentException::class.java) {
                waitFunc.call(cx, scope, scope, arrayOf(5, "not-a-function"))
            }

            assertEquals("Second argument to task.wait() must be a function", exception.message)
        } finally {
            Context.exit()
        }
    }

    // ====== Integration Tests ======

    @Test
    fun `test task wait executes on worker thread`() {
        val latch = CountDownLatch(1)
        var executionThread: Thread? = null
        val mainThread = Thread.currentThread()

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            val callback = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any? {
                    executionThread = Thread.currentThread()
                    latch.countDown()
                    return null
                }
            }

            // Call task.wait()
            val waitFunc = taskApi.get("wait", taskApi) as Function
            waitFunc.call(cx, scope, scope, arrayOf(1, callback))

            // Tick once
            TickScheduler.tick()

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should execute")

            assertNotNull(executionThread)
            assertNotEquals(mainThread, executionThread, "Callback should execute on worker thread")
            assertTrue(executionThread!!.name.contains("RhettJS-Worker"))
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task wait preserves scope`() {
        val latch = CountDownLatch(1)
        var capturedValue: String? = null

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            scope.put("task", scope, taskApi)

            // Set a value in scope
            scope.put("testValue", scope, "scope-preserved")

            // Callback that reads from scope
            val callback = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable?,
                    args: Array<Any?>
                ): Any? {
                    capturedValue = scope.get("testValue", scope) as String?
                    latch.countDown()
                    return null
                }
            }

            // Call task.wait()
            val waitFunc = taskApi.get("wait", taskApi) as Function
            waitFunc.call(cx, scope, scope, arrayOf(1, callback))

            // Tick once
            TickScheduler.tick()

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should execute")

            assertEquals("scope-preserved", capturedValue)
        } finally {
            Context.exit()
        }
    }
}
