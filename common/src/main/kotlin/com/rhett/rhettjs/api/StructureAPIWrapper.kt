package com.rhett.rhettjs.api

import com.rhett.rhettjs.threading.ThreadSafeAPI
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * JavaScript-accessible wrapper for Structure API.
 *
 * Provides structure-specific methods as a JavaScript object:
 *   Structure.list(pool?)
 *   Structure.read(name)
 *   Structure.write(name, data)
 *   Structure.nbt.*  (access to NBT utility methods)
 *
 * Thread-safe: Only performs file I/O operations, no Minecraft object access.
 */
class StructureAPIWrapper(
    private val structureApi: StructureAPI
) : ScriptableObject(), ThreadSafeAPI {

    override fun getClassName(): String = "Structure"

    init {
        // Add methods as BaseFunction objects
        val listFn = ListFunction(structureApi)
        val readFn = ReadFunction(structureApi)
        val writeFn = WriteFunction(structureApi)
        val backupFn = BackupFunction(structureApi)
        val listBackupsFn = ListBackupsFunction(structureApi)
        val restoreFn = RestoreFunction(structureApi)

        // Don't set parent scope here - it will be set when the wrapper is injected into the script engine
        // This allows the functions to get the correct global scope
        put("list", this, listFn)
        put("read", this, readFn)
        put("write", this, writeFn)
        put("backup", this, backupFn)
        put("listBackups", this, listBackupsFn)
        put("restore", this, restoreFn)

        // Add nbt property for advanced operations
        val nbtWrapper = NBTUtilityWrapper(structureApi.getNbtApi())
        put("nbt", this, nbtWrapper)
    }

    override fun setParentScope(scope: Scriptable?) {
        super.setParentScope(scope)

        // When parent scope is set on the wrapper, propagate it to all child functions
        // This ensures they can create JavaScript objects/arrays correctly
        (get("list", this) as? BaseFunction)?.setParentScope(scope)
        (get("read", this) as? BaseFunction)?.setParentScope(scope)
        (get("write", this) as? BaseFunction)?.setParentScope(scope)
        (get("backup", this) as? BaseFunction)?.setParentScope(scope)
        (get("listBackups", this) as? BaseFunction)?.setParentScope(scope)
        (get("restore", this) as? BaseFunction)?.setParentScope(scope)
        (get("nbt", this) as? ScriptableObject)?.setParentScope(scope)
    }

    /**
     * List function implementation.
     */
    private class ListFunction(private val structureApi: StructureAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any {
            val pool = if (args.isNotEmpty() && args[0] is String) {
                args[0] as String
            } else {
                null
            }

            val structures = structureApi.list(pool)

            // Use the scope parameter - it's the current thread's scope
            // Create JavaScript array manually
            val jsArray = cx.newObject(scope, "Array") as org.mozilla.javascript.NativeArray
            structures.forEachIndexed { index, structure ->
                jsArray.put(index, jsArray, structure)
            }
            return jsArray
        }
    }

    /**
     * Read function implementation.
     */
    private class ReadFunction(private val structureApi: StructureAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.isEmpty() || args[0] !is String) {
                throw IllegalArgumentException("Structure.read() requires a structure name")
            }

            val name = args[0] as String
            val data = structureApi.read(name)

            // Manually convert Kotlin data structures to JavaScript objects
            // This ensures all nested properties are properly converted in worker threads
            return if (data != null) {
                convertToJS(cx, scope, data)
            } else {
                null
            }
        }

        /**
         * Recursively convert Kotlin/Java data structures to JavaScript objects.
         * Context.javaToJS() doesn't always fully convert nested structures in worker threads.
         */
        private fun convertToJS(cx: Context, scope: Scriptable, value: Any?): Any? {
            return when (value) {
                null -> null
                is Map<*, *> -> {
                    val jsObj = cx.newObject(scope)
                    value.forEach { (k, v) ->
                        jsObj.put(k.toString(), jsObj, convertToJS(cx, scope, v))
                    }
                    jsObj
                }
                is List<*> -> {
                    val jsArray = cx.newArray(scope, value.size)
                    value.forEachIndexed { index, item ->
                        jsArray.put(index, jsArray, convertToJS(cx, scope, item))
                    }
                    jsArray
                }
                is String, is Number, is Boolean -> value
                else -> Context.javaToJS(value, scope) // Fallback for other types
            }
        }
    }

    /**
     * Write function implementation.
     */
    private class WriteFunction(private val structureApi: StructureAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 2) {
                throw IllegalArgumentException("Structure.write() requires name and data")
            }

            if (args[0] !is String) {
                throw IllegalArgumentException("First argument to Structure.write() must be a string (name)")
            }

            val name = args[0] as String
            val data = Context.jsToJava(args[1], Any::class.java)

             // Third parameter: skipBackup (optional, defaults to false)
            val skipBackup = if (args.size > 2 && args[2] is Boolean) {
                args[2] as Boolean
            } else {
                false
            }

            structureApi.write(name, data, skipBackup)

            return Context.getUndefinedValue()
        }
    }

    /**
     * Backup function implementation.
     */
    private class BackupFunction(private val structureApi: StructureAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.isEmpty() || args[0] !is String) {
                throw IllegalArgumentException("Structure.backup() requires a structure name")
            }

            val name = args[0] as String
            val backupFilename = structureApi.backup(name)

            return backupFilename
        }
    }

    /**
     * ListBackups function implementation.
     */
    private class ListBackupsFunction(private val structureApi: StructureAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any {
            if (args.isEmpty() || args[0] !is String) {
                throw IllegalArgumentException("Structure.listBackups() requires a structure name")
            }

            val name = args[0] as String
            val backups = structureApi.listBackups(name)

            // Convert to JavaScript array
            val jsArray = cx.newObject(scope, "Array") as org.mozilla.javascript.NativeArray
            backups.forEachIndexed { index, backup ->
                jsArray.put(index, jsArray, backup)
            }
            return jsArray
        }
    }

    /**
     * Restore function implementation.
     */
    private class RestoreFunction(private val structureApi: StructureAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any {
            if (args.isEmpty() || args[0] !is String) {
                throw IllegalArgumentException("Structure.restore() requires a structure name")
            }

            val name = args[0] as String
            val targetName = if (args.size > 1 && args[1] is String) args[1] as String else null
            val backupTimestamp = if (args.size > 2 && args[2] is String) args[2] as String else null

            val success = structureApi.restore(name, targetName, backupTimestamp)

            return success
        }
    }
}

/**
 * Wrapper for NBT utility methods (forEach, filter, find, some).
 */
class NBTUtilityWrapper(private val nbtApi: NBTAPI) : ScriptableObject() {

    override fun getClassName(): String = "nbt"

    init {
        val forEachFn = ForEachFunction(nbtApi)
        val filterFn = FilterFunction(nbtApi)
        val findFn = FindFunction(nbtApi)
        val someFn = SomeFunction(nbtApi)

        // Don't set parent scope here - it will be set when the wrapper is injected
        put("forEach", this, forEachFn)
        put("filter", this, filterFn)
        put("find", this, findFn)
        put("some", this, someFn)
    }

    override fun setParentScope(scope: Scriptable?) {
        super.setParentScope(scope)

        // Propagate parent scope to all child functions
        (get("forEach", this) as? BaseFunction)?.setParentScope(scope)
        (get("filter", this) as? BaseFunction)?.setParentScope(scope)
        (get("find", this) as? BaseFunction)?.setParentScope(scope)
        (get("some", this) as? BaseFunction)?.setParentScope(scope)
    }

    private class ForEachFunction(private val nbtApi: NBTAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            // Get the global scope from thisObj's parent
            val topScope = thisObj?.parentScope ?: ScriptableObject.getTopLevelScope(scope)

            if (args.size < 2) {
                throw IllegalArgumentException("nbt.forEach() requires data and callback")
            }

            val data = Context.jsToJava(args[0], Any::class.java)
            val callback = args[1] as? org.mozilla.javascript.Function
                ?: throw IllegalArgumentException("Second argument must be a function")

            nbtApi.forEach(data) { value, path, parent ->
                val jsPath = cx.newObject(topScope, "Array") as org.mozilla.javascript.NativeArray
                path.forEachIndexed { index, item ->
                    jsPath.put(index, jsPath, item)
                }
                callback.call(cx, topScope, topScope, arrayOf(
                    Context.javaToJS(value, topScope),
                    jsPath,
                    Context.javaToJS(parent, topScope)
                ))
            }

            return Context.getUndefinedValue()
        }
    }

    private class FilterFunction(private val nbtApi: NBTAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any {
            // Get the global scope from thisObj's parent
            val topScope = thisObj?.parentScope ?: ScriptableObject.getTopLevelScope(scope)

            if (args.size < 2) {
                throw IllegalArgumentException("nbt.filter() requires data and predicate")
            }

            val data = Context.jsToJava(args[0], Any::class.java)
            val predicate = args[1] as? org.mozilla.javascript.Function
                ?: throw IllegalArgumentException("Second argument must be a function")

            val results = nbtApi.filter(data) { value, path, parent ->
                val jsPath = cx.newObject(topScope, "Array") as org.mozilla.javascript.NativeArray
                path.forEachIndexed { index, item ->
                    jsPath.put(index, jsPath, item)
                }
                val result = predicate.call(cx, topScope, topScope, arrayOf(
                    Context.javaToJS(value, topScope),
                    jsPath,
                    Context.javaToJS(parent, topScope)
                ))
                Context.toBoolean(result)
            }

            // Convert results to JavaScript array
            val jsResults = results.map { result ->
                val obj = cx.newObject(topScope)
                val jsPath = cx.newObject(topScope, "Array") as org.mozilla.javascript.NativeArray
                result.path.forEachIndexed { index, item ->
                    jsPath.put(index, jsPath, item)
                }
                obj.put("value", obj, Context.javaToJS(result.value, topScope))
                obj.put("path", obj, jsPath)
                obj.put("parent", obj, Context.javaToJS(result.parent, topScope))
                obj
            }

            val jsResultsArray = cx.newObject(topScope, "Array") as org.mozilla.javascript.NativeArray
            jsResults.forEachIndexed { index, item ->
                jsResultsArray.put(index, jsResultsArray, item)
            }
            return jsResultsArray
        }
    }

    private class FindFunction(private val nbtApi: NBTAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            // Get the global scope from thisObj's parent
            val topScope = thisObj?.parentScope ?: ScriptableObject.getTopLevelScope(scope)

            if (args.size < 2) {
                throw IllegalArgumentException("nbt.find() requires data and predicate")
            }

            val data = Context.jsToJava(args[0], Any::class.java)
            val predicate = args[1] as? org.mozilla.javascript.Function
                ?: throw IllegalArgumentException("Second argument must be a function")

            val result = nbtApi.find(data) { value, path, parent ->
                val jsPath = cx.newObject(topScope, "Array") as org.mozilla.javascript.NativeArray
                path.forEachIndexed { index, item ->
                    jsPath.put(index, jsPath, item)
                }
                val predicateResult = predicate.call(cx, topScope, topScope, arrayOf(
                    Context.javaToJS(value, topScope),
                    jsPath,
                    Context.javaToJS(parent, topScope)
                ))
                Context.toBoolean(predicateResult)
            }

            return if (result != null) {
                val obj = cx.newObject(topScope)
                val jsPath = cx.newObject(topScope, "Array") as org.mozilla.javascript.NativeArray
                result.path.forEachIndexed { index, item ->
                    jsPath.put(index, jsPath, item)
                }
                obj.put("value", obj, Context.javaToJS(result.value, topScope))
                obj.put("path", obj, jsPath)
                obj.put("parent", obj, Context.javaToJS(result.parent, topScope))
                obj
            } else {
                null
            }
        }
    }

    private class SomeFunction(private val nbtApi: NBTAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Boolean {
            // Get the global scope from thisObj's parent
            val topScope = thisObj?.parentScope ?: ScriptableObject.getTopLevelScope(scope)

            if (args.size < 2) {
                throw IllegalArgumentException("nbt.some() requires data and predicate")
            }

            val data = Context.jsToJava(args[0], Any::class.java)
            val predicate = args[1] as? org.mozilla.javascript.Function
                ?: throw IllegalArgumentException("Second argument must be a function")

            return nbtApi.some(data) { value, path, parent ->
                val jsPath = cx.newObject(topScope, "Array") as org.mozilla.javascript.NativeArray
                path.forEachIndexed { index, item ->
                    jsPath.put(index, jsPath, item)
                }
                val result = predicate.call(cx, topScope, topScope, arrayOf(
                    Context.javaToJS(value, topScope),
                    jsPath,
                    Context.javaToJS(parent, topScope)
                ))
                Context.toBoolean(result)
            }
        }
    }
}
