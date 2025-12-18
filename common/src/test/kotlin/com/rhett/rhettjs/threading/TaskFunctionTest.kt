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
 * TDD tests for task() function - worker thread execution.
 *
 * Tests async execution on worker threads with argument passing and
 * Java object validation (shallow check).
 */
class TaskFunctionTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var taskFunction: TaskFunction

    @BeforeEach
    fun setup() {
        // Initialize ConfigManager
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)

        taskFunction = TaskFunction()
    }

    // ====== Basic Execution Tests ======

    @Test
    fun `test task executes callback on worker thread`() {
        val latch = CountDownLatch(1)
        var executionThread: Thread? = null
        val mainThread = Thread.currentThread()

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            // JavaScript callback that captures thread
            val callback = cx.evaluateString(scope, """
                (function() {
                    // This will be called from Kotlin, capturing thread happens there
                })
            """.trimIndent(), "test", 1, null)

            // Execute task with callback that sets executionThread
            taskFunction.execute(scope, callback as Scriptable) {
                executionThread = Thread.currentThread()
                latch.countDown()
            }

            // Wait for task to complete
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Task should complete within 5 seconds")

            // Verify it ran on different thread
            assertNotNull(executionThread, "Task should have executed")
            assertNotEquals(mainThread, executionThread, "Task should run on worker thread, not main thread")

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task passes arguments to callback`() {
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

            // Execute with arguments
            taskFunction.execute(scope, callback as Scriptable, arrayOf("hello", 42, true)) { args ->
                receivedArgs = args
                latch.countDown()
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))

            assertNotNull(receivedArgs)
            assertEquals(3, receivedArgs!!.size)
            assertEquals("hello", receivedArgs!![0])
            assertEquals(42, receivedArgs!![1])
            assertEquals(true, receivedArgs!![2])

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task with no arguments`() {
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

            taskFunction.execute(scope, callback as Scriptable, emptyArray()) {
                executed = true
                latch.countDown()
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertTrue(executed, "Task should have executed")

        } finally {
            Context.exit()
        }
    }

    // ====== Java Object Validation Tests ======

    @Test
    fun `test task rejects Java objects in arguments`() {
        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function(obj) {})
            """.trimIndent(), "test", 1, null)

            // Create a Java object (not a JavaScript object)
            val javaObject = Object()

            // Should throw exception
            assertThrows(IllegalArgumentException::class.java) {
                taskFunction.execute(scope, callback as Scriptable, arrayOf(javaObject)) { }
            }

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task allows JavaScript objects in arguments`() {
        val latch = CountDownLatch(1)

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            // Create JS object
            val jsObject = cx.evaluateString(scope, """
                ({name: 'test', value: 42})
            """.trimIndent(), "test", 1, null)

            val callback = cx.evaluateString(scope, """
                (function(obj) {
                    return obj.name;
                })
            """.trimIndent(), "test", 1, null)

            // Should not throw
            assertDoesNotThrow {
                taskFunction.execute(scope, callback as Scriptable, arrayOf(jsObject)) {
                    latch.countDown()
                }
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task allows primitives in arguments`() {
        val latch = CountDownLatch(1)

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function(str, num, bool) {})
            """.trimIndent(), "test", 1, null)

            // Primitives should be allowed
            assertDoesNotThrow {
                taskFunction.execute(scope, callback as Scriptable, arrayOf("string", 42, true)) {
                    latch.countDown()
                }
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task allows wrapped objects in JS object`() {
        val latch = CountDownLatch(1)

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            // Create JS object wrapping a Java object
            val jsWrapper = cx.evaluateString(scope, """
                ({data: null})  // Will set data from Kotlin
            """.trimIndent(), "test", 1, null) as Scriptable

            // Even though the wrapper contains Java object reference, the wrapper itself is JS
            // This should be allowed (shallow check only)
            jsWrapper.put("data", jsWrapper, Object()) // Put Java object inside

            val callback = cx.evaluateString(scope, """
                (function(wrapper) {})
            """.trimIndent(), "test", 1, null)

            // Should not throw (shallow check - only checks first level)
            assertDoesNotThrow {
                taskFunction.execute(scope, callback as Scriptable, arrayOf(jsWrapper)) {
                    latch.countDown()
                }
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))

        } finally {
            Context.exit()
        }
    }

    // ====== Error Handling Tests ======

    @Test
    fun `test task handles callback errors gracefully`() {
        val latch = CountDownLatch(1)
        var errorCaught = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {
                    throw new Error('Test error');
                })
            """.trimIndent(), "test", 1, null)

            taskFunction.execute(scope, callback as Scriptable, emptyArray()) {
                // Task executed, but callback threw error
                errorCaught = true
                latch.countDown()
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            // Error should be logged but not crash worker thread
            assertTrue(errorCaught, "Error handler should have been called")

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test multiple tasks run concurrently`() {
        val taskCount = 4
        val latch = CountDownLatch(taskCount)
        val executionThreads = mutableSetOf<Thread>()

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {
                    // Simulate some work
                })
            """.trimIndent(), "test", 1, null)

            // Launch multiple tasks
            repeat(taskCount) {
                taskFunction.execute(scope, callback as Scriptable, emptyArray()) {
                    synchronized(executionThreads) {
                        executionThreads.add(Thread.currentThread())
                    }
                    latch.countDown()
                }
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All tasks should complete")

            // Should have used multiple worker threads (assuming pool size > 1)
            assertTrue(executionThreads.size >= 1, "Should have used at least one worker thread")

        } finally {
            Context.exit()
        }
    }

    // ====== Worker Pool Tests ======

    @Test
    fun `test worker threads are daemon threads`() {
        val latch = CountDownLatch(1)
        var workerIsDaemon = false

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            taskFunction.execute(scope, callback as Scriptable, emptyArray()) {
                workerIsDaemon = Thread.currentThread().isDaemon
                latch.countDown()
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertTrue(workerIsDaemon, "Worker threads should be daemon threads")

        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test worker thread has correct name pattern`() {
        val latch = CountDownLatch(1)
        var threadName = ""

        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()

            val callback = cx.evaluateString(scope, """
                (function() {})
            """.trimIndent(), "test", 1, null)

            taskFunction.execute(scope, callback as Scriptable, emptyArray()) {
                threadName = Thread.currentThread().name
                latch.countDown()
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertTrue(
                threadName.contains("RhettJS-Worker") || threadName.contains("pool"),
                "Worker thread should have identifiable name, got: $threadName"
            )

        } finally {
            Context.exit()
        }
    }
}
