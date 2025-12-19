package com.rhett.rhettjs.engine

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ScriptableObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Inspects API objects using Java reflection to extract type information.
 */
object ReflectionIntrospector {

    data class MethodSignature(
        val name: String,
        val parameters: List<Parameter>,
        val returnType: String,
        val isVarArgs: Boolean = false
    )

    data class Parameter(
        val name: String,
        val type: String,
        val optional: Boolean = false
    )

    data class PropertySignature(
        val name: String,
        val type: String,
        val readonly: Boolean = true
    )

    /**
     * Introspect a plain Java/Kotlin object and extract method signatures.
     */
    fun introspectJavaObject(obj: Any): List<MethodSignature> {
        val methods = mutableListOf<MethodSignature>()
        val clazz = obj.javaClass

        // Get all public methods
        clazz.methods
            .filter { method ->
                // Only public methods
                Modifier.isPublic(method.modifiers) &&
                // Skip methods from Object class
                method.declaringClass != Object::class.java &&
                // Skip getClass, wait, notify, etc.
                method.name !in setOf("getClass", "wait", "notify", "notifyAll", "hashCode", "equals", "toString")
            }
            .forEach { method ->
                methods.add(extractMethodSignature(method))
            }

        return methods
    }

    /**
     * Introspect a Rhino ScriptableObject and extract its structure.
     */
    fun introspectScriptableObject(obj: ScriptableObject): Map<String, Any> {
        val members = mutableMapOf<String, Any>()

        // Get all property IDs from the scriptable object
        val ids = obj.ids
        for (id in ids) {
            val name = id.toString()

            // Skip internal properties
            if (name.startsWith("__")) continue

            val value = obj.get(name, obj)
            if (value != null && value != org.mozilla.javascript.Scriptable.NOT_FOUND) {
                when (value) {
                    is BaseFunction -> {
                        // It's a function - try to introspect it
                        members[name] = "function"
                    }
                    is ScriptableObject -> {
                        // It's a nested object - recursively introspect
                        members[name] = introspectScriptableObject(value)
                    }
                    else -> {
                        // It's a property
                        members[name] = value.javaClass.simpleName
                    }
                }
            }
        }

        return members
    }

    /**
     * Extract method signature from a Java Method via reflection.
     */
    private fun extractMethodSignature(method: Method): MethodSignature {
        val parameters = if (method.isVarArgs && method.parameterCount > 0) {
            // Handle varargs specially
            val regularParams = method.parameters.take(method.parameterCount - 1).mapIndexed { index, param ->
                val paramName = inferParameterName(param.name, param.type, index)
                val paramType = TypeMapper.toTypeScript(param.parameterizedType)
                Parameter(
                    name = paramName,
                    type = paramType,
                    optional = false
                )
            }

            // The last parameter is varargs
            val varArgsParam = method.parameters.last()
            val componentType = varArgsParam.type.componentType
            val varArgsName = inferParameterName(varArgsParam.name, varArgsParam.type, method.parameterCount - 1)
            val varArgsType = TypeMapper.toTypeScript(componentType)

            regularParams + Parameter(
                name = "...$varArgsName",
                type = "${varArgsType}[]",
                optional = false
            )
        } else {
            method.parameters.mapIndexed { index, param ->
                val paramName = inferParameterName(param.name, param.type, index)
                val paramType = TypeMapper.toTypeScript(param.parameterizedType)
                Parameter(
                    name = paramName,
                    type = paramType,
                    optional = false
                )
            }
        }

        val returnType = TypeMapper.toTypeScript(method.genericReturnType)

        return MethodSignature(
            name = method.name,
            parameters = parameters,
            returnType = returnType,
            isVarArgs = method.isVarArgs
        )
    }

    /**
     * Infer a meaningful parameter name from reflection data.
     */
    private fun inferParameterName(reflectionName: String, type: Class<*>, index: Int): String {
        // If we have a meaningful name from reflection, use it
        if (reflectionName.isNotEmpty() && !reflectionName.startsWith("arg")) {
            return reflectionName
        }

        // Otherwise, infer from type
        return when {
            type == String::class.java -> "message"
            type.isArray -> when {
                type.componentType == Any::class.java -> "messages"
                else -> "items"
            }
            Collection::class.java.isAssignableFrom(type) -> "items"
            Map::class.java.isAssignableFrom(type) -> "map"
            type == Int::class.java || type == Integer::class.java -> "value"
            type == Boolean::class.java || type == java.lang.Boolean::class.java -> "flag"
            else -> "arg$index"
        }
    }

    /**
     * Generate TypeScript interface definition from method signatures.
     */
    fun generateInterfaceDefinition(
        name: String,
        methods: List<MethodSignature>,
        properties: List<PropertySignature> = emptyList(),
        isConst: Boolean = true
    ): String {
        return buildString {
            if (isConst) {
                appendLine("declare const $name: {")
            } else {
                appendLine("interface $name {")
            }

            // Add properties
            properties.forEach { prop ->
                val readonly = if (prop.readonly) "readonly " else ""
                appendLine("    $readonly${prop.name}: ${prop.type};")
            }

            // Add methods
            methods.forEach { method ->
                val params = method.parameters.joinToString(", ") { param ->
                    // Check if parameter name already contains "..." for varargs
                    val paramName = param.name
                    val optional = if (param.optional) "?" else ""

                    // If the param name starts with "...", it's varargs - don't add optional marker
                    if (paramName.startsWith("...")) {
                        "$paramName: ${param.type}"
                    } else {
                        "$paramName$optional: ${param.type}"
                    }
                }
                appendLine("    ${method.name}($params): ${method.returnType};")
            }

            if (isConst) {
                appendLine("};")
            } else {
                appendLine("}")
            }
        }
    }

    /**
     * Generate TypeScript function declaration from method signature.
     */
    fun generateFunctionDeclaration(method: MethodSignature): String {
        val params = method.parameters.joinToString(", ") { param ->
            val optional = if (param.optional) "?" else ""
            "${param.name}$optional: ${param.type}"
        }
        return "declare function ${method.name}($params): ${method.returnType};"
    }
}
