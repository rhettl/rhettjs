package com.rhett.rhettjs.api

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
 */
class StructureAPIWrapper(
    private val structureApi: StructureAPI
) : ScriptableObject() {

    override fun getClassName(): String = "Structure"

    init {
        // Add methods as BaseFunction objects
        val listFn = ListFunction(structureApi)
        val readFn = ReadFunction(structureApi)
        val writeFn = WriteFunction(structureApi)

        // Set parent scope so functions have access to the proper scope
        listFn.setParentScope(this)
        readFn.setParentScope(this)
        writeFn.setParentScope(this)

        put("list", this, listFn)
        put("read", this, readFn)
        put("write", this, writeFn)

        // Add nbt property for advanced operations
        val nbtWrapper = NBTUtilityWrapper(structureApi.getNbtApi())
        nbtWrapper.setParentScope(this)
        put("nbt", this, nbtWrapper)
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
            // Get the top-level scope - use thisObj (Structure object) to traverse the parent chain
            // thisObj is the JavaScript object this function is attached to (the Structure wrapper)
            val actualScope = if (thisObj != null) {
                getTopLevelScope(thisObj)
            } else {
                getTopLevelScope(this)
            }
            return cx.newArray(actualScope, structures.toTypedArray())
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

            // Get the top-level scope - use thisObj if available
            val actualScope = if (thisObj != null) {
                getTopLevelScope(thisObj)
            } else {
                getTopLevelScope(this)
            }
            return if (data != null) {
                Context.javaToJS(data, actualScope)
            } else {
                null
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

            structureApi.write(name, data)

            return Context.getUndefinedValue()
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

        // Set parent scope so functions have access to the proper scope
        forEachFn.setParentScope(this)
        filterFn.setParentScope(this)
        findFn.setParentScope(this)
        someFn.setParentScope(this)

        put("forEach", this, forEachFn)
        put("filter", this, filterFn)
        put("find", this, findFn)
        put("some", this, someFn)
    }

    private class ForEachFunction(private val nbtApi: NBTAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            // Get the top-level scope - use thisObj if available
            val actualScope = if (thisObj != null) {
                getTopLevelScope(thisObj)
            } else {
                getTopLevelScope(this)
            }

            if (args.size < 2) {
                throw IllegalArgumentException("nbt.forEach() requires data and callback")
            }

            val data = Context.jsToJava(args[0], Any::class.java)
            val callback = args[1] as? org.mozilla.javascript.Function
                ?: throw IllegalArgumentException("Second argument must be a function")

            nbtApi.forEach(data) { value, path, parent ->
                val jsPath = cx.newArray(actualScope, path.toTypedArray())
                callback.call(cx, actualScope, actualScope, arrayOf(
                    Context.javaToJS(value, actualScope),
                    jsPath,
                    Context.javaToJS(parent, actualScope)
                ))
            }

            return Context.getUndefinedValue()
        }
    }

    private class FilterFunction(private val nbtApi: NBTAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any {
            // Get the top-level scope - use thisObj if available
            val actualScope = if (thisObj != null) {
                getTopLevelScope(thisObj)
            } else {
                getTopLevelScope(this)
            }

            if (args.size < 2) {
                throw IllegalArgumentException("nbt.filter() requires data and predicate")
            }

            val data = Context.jsToJava(args[0], Any::class.java)
            val predicate = args[1] as? org.mozilla.javascript.Function
                ?: throw IllegalArgumentException("Second argument must be a function")

            val results = nbtApi.filter(data) { value, path, parent ->
                val jsPath = cx.newArray(actualScope, path.toTypedArray())
                val result = predicate.call(cx, actualScope, actualScope, arrayOf(
                    Context.javaToJS(value, actualScope),
                    jsPath,
                    Context.javaToJS(parent, actualScope)
                ))
                Context.toBoolean(result)
            }

            // Convert results to JavaScript array
            val jsResults = results.map { result ->
                val obj = cx.newObject(actualScope)
                obj.put("value", obj, Context.javaToJS(result.value, actualScope))
                obj.put("path", obj, cx.newArray(actualScope, result.path.toTypedArray()))
                obj.put("parent", obj, Context.javaToJS(result.parent, actualScope))
                obj
            }

            return cx.newArray(actualScope, jsResults.toTypedArray())
        }
    }

    private class FindFunction(private val nbtApi: NBTAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            // Get the top-level scope - use thisObj if available
            val actualScope = if (thisObj != null) {
                getTopLevelScope(thisObj)
            } else {
                getTopLevelScope(this)
            }

            if (args.size < 2) {
                throw IllegalArgumentException("nbt.find() requires data and predicate")
            }

            val data = Context.jsToJava(args[0], Any::class.java)
            val predicate = args[1] as? org.mozilla.javascript.Function
                ?: throw IllegalArgumentException("Second argument must be a function")

            val result = nbtApi.find(data) { value, path, parent ->
                val jsPath = cx.newArray(actualScope, path.toTypedArray())
                val predicateResult = predicate.call(cx, actualScope, actualScope, arrayOf(
                    Context.javaToJS(value, actualScope),
                    jsPath,
                    Context.javaToJS(parent, actualScope)
                ))
                Context.toBoolean(predicateResult)
            }

            return if (result != null) {
                val obj = cx.newObject(actualScope)
                obj.put("value", obj, Context.javaToJS(result.value, actualScope))
                obj.put("path", obj, cx.newArray(actualScope, result.path.toTypedArray()))
                obj.put("parent", obj, Context.javaToJS(result.parent, actualScope))
                obj
            } else {
                null
            }
        }
    }

    private class SomeFunction(private val nbtApi: NBTAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Boolean {
            // Get the top-level scope - use thisObj if available
            val actualScope = if (thisObj != null) {
                getTopLevelScope(thisObj)
            } else {
                getTopLevelScope(this)
            }

            if (args.size < 2) {
                throw IllegalArgumentException("nbt.some() requires data and predicate")
            }

            val data = Context.jsToJava(args[0], Any::class.java)
            val predicate = args[1] as? org.mozilla.javascript.Function
                ?: throw IllegalArgumentException("Second argument must be a function")

            return nbtApi.some(data) { value, path, parent ->
                val jsPath = cx.newArray(actualScope, path.toTypedArray())
                val result = predicate.call(cx, actualScope, actualScope, arrayOf(
                    Context.javaToJS(value, actualScope),
                    jsPath,
                    Context.javaToJS(parent, actualScope)
                ))
                Context.toBoolean(result)
            }
        }
    }
}
