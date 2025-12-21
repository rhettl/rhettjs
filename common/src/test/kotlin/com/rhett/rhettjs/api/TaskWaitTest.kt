package com.rhett.rhettjs.api

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.threading.EventLoop
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.Scriptable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for wait() and task() functions.
 *
 * New API:
 * - wait(ticks) → Promise (resolved after N ticks)
 * - task(fn, ...args) → Promise (runs fn on worker thread)
 */
class TaskWaitTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)
    }

    // ====== wait() Tests ======

    @Test
    fun `test wait returns a Promise`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            val waitApi = WaitAPI()
            scope.put("wait", scope, waitApi)

            // Set up event loop
            val eventLoop = EventLoop(cx, scope)
            EventLoop.setCurrent(eventLoop)

            try {
                val result = cx.evaluateString(scope, "wait(5)", "test", 1, null)

                // Should return a Promise (NativeObject with then method)
                assertTrue(result is Scriptable, "wait() should return a Scriptable")
                val promise = result as Scriptable
                assertNotNull(promise.get("then", promise), "Result should have 'then' method (Promise)")
            } finally {
                EventLoop.setCurrent(null)
            }
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test wait requires ticks argument`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            val waitApi = WaitAPI()
            scope.put("wait", scope, waitApi)

            val eventLoop = EventLoop(cx, scope)
            EventLoop.setCurrent(eventLoop)

            try {
                val exception = assertThrows(EcmaError::class.java) {
                    waitApi.call(cx, scope, scope, arrayOf())
                }
                assertTrue(exception.message?.contains("wait() requires a ticks argument") == true)
            } finally {
                EventLoop.setCurrent(null)
            }
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test wait requires ticks to be a number`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            val waitApi = WaitAPI()
            scope.put("wait", scope, waitApi)

            val eventLoop = EventLoop(cx, scope)
            EventLoop.setCurrent(eventLoop)

            try {
                val exception = assertThrows(EcmaError::class.java) {
                    waitApi.call(cx, scope, scope, arrayOf("not-a-number"))
                }
                assertTrue(exception.message?.contains("wait() argument must be a number") == true)
            } finally {
                EventLoop.setCurrent(null)
            }
        } finally {
            Context.exit()
        }
    }

    // ====== task() Tests ======

    @Test
    fun `test task returns a Promise`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            val taskApi = TaskAPI()
            scope.put("task", scope, taskApi)

            val eventLoop = EventLoop(cx, scope)
            EventLoop.setCurrent(eventLoop)

            try {
                // Create a simple function
                val result = cx.evaluateString(scope, """
                    task(function() { return 42; })
                """.trimIndent(), "test", 1, null)

                assertTrue(result is Scriptable, "task() should return a Scriptable")
                val promise = result as Scriptable
                assertNotNull(promise.get("then", promise), "Result should have 'then' method (Promise)")
            } finally {
                EventLoop.setCurrent(null)
            }
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task requires callback argument`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            val taskApi = TaskAPI()
            scope.put("task", scope, taskApi)

            val eventLoop = EventLoop(cx, scope)
            EventLoop.setCurrent(eventLoop)

            try {
                val exception = assertThrows(EcmaError::class.java) {
                    taskApi.call(cx, scope, scope, arrayOf())
                }
                assertTrue(exception.message?.contains("task() requires at least a callback function") == true)
            } finally {
                EventLoop.setCurrent(null)
            }
        } finally {
            Context.exit()
        }
    }

    @Test
    fun `test task requires callback to be a function`() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            val taskApi = TaskAPI()
            scope.put("task", scope, taskApi)

            val eventLoop = EventLoop(cx, scope)
            EventLoop.setCurrent(eventLoop)

            try {
                val exception = assertThrows(EcmaError::class.java) {
                    taskApi.call(cx, scope, scope, arrayOf("not-a-function"))
                }
                assertTrue(exception.message?.contains("First argument to task() must be a function") == true)
            } finally {
                EventLoop.setCurrent(null)
            }
        } finally {
            Context.exit()
        }
    }
}
