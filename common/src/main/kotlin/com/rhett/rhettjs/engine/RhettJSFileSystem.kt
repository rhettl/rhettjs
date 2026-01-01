package com.rhett.rhettjs.engine

import com.rhett.rhettjs.config.ConfigManager
import org.graalvm.polyglot.io.FileSystem
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileTime
import java.util.*

/**
 * Custom FileSystem implementation that enables bare specifier imports for built-in APIs.
 *
 * Intercepts module resolution to support:
 * - `import World from 'World'` - Built-in modules (bare specifiers)
 * - `import { add } from '../modules/math.js'` - User modules (relative paths)
 *
 * When a bare specifier for a built-in module is detected, this FileSystem:
 * 1. Returns a virtual path (/__builtins__/ModuleName)
 * 2. Generates ES6 module code that exports the global binding
 * 3. Provides the content via an in-memory byte channel
 */
class RhettJSFileSystem(
    private val delegate: FileSystem
) : FileSystem {

    companion object {
        private val BUILT_IN_MODULES = setOf("World", "Structure", "Store", "NBT")
        private const val VIRTUAL_PREFIX = "/__builtins__/"
    }

    override fun parsePath(uri: URI): Path {
        val uriString = uri.toString()
        ConfigManager.debug("FileSystem.parsePath(URI): $uriString")

        // Check if this is a bare specifier for a built-in module
        // Examples: "World", "file:///World", etc.
        if (isBareBuiltInSpecifier(uriString)) {
            val moduleName = extractModuleName(uriString)
            val virtualPath = Paths.get("$VIRTUAL_PREFIX$moduleName")
            ConfigManager.debug("Mapped built-in module '$moduleName' to virtual path: $virtualPath")
            return virtualPath
        }

        // Delegate to default for everything else
        return delegate.parsePath(uri)
    }

    override fun parsePath(path: String): Path {
        ConfigManager.debug("FileSystem.parsePath(String): $path")

        // Check for bare built-in specifiers
        if (isBareBuiltInSpecifier(path)) {
            val moduleName = extractModuleName(path)
            val virtualPath = Paths.get("$VIRTUAL_PREFIX$moduleName")
            ConfigManager.debug("Mapped built-in module '$moduleName' to virtual path: $virtualPath")
            return virtualPath
        }

        return delegate.parsePath(path)
    }

    override fun checkAccess(path: Path, modes: MutableSet<out AccessMode>?, vararg linkOptions: LinkOption?) {
        // Virtual built-in modules are always accessible for reading
        if (path.toString().startsWith(VIRTUAL_PREFIX)) {
            if (modes == null || modes.isEmpty() || modes.contains(AccessMode.READ)) {
                return // Allow read access
            }
            throw IOException("Write access not supported for built-in modules")
        }

        delegate.checkAccess(path, modes, *linkOptions)
    }

    override fun newByteChannel(
        path: Path,
        options: MutableSet<out OpenOption>?,
        vararg attrs: FileAttribute<*>?
    ): SeekableByteChannel {
        // Check if this is a virtual built-in module
        val pathString = path.toString()
        if (pathString.startsWith(VIRTUAL_PREFIX)) {
            val moduleName = path.fileName.toString()
            val moduleContent = generateBuiltInModule(moduleName)
            ConfigManager.debug("Providing virtual module content for '$moduleName' (${moduleContent.length} bytes)")
            return ByteArraySeekableByteChannel(moduleContent.toByteArray(Charsets.UTF_8))
        }

        // Delegate to default for real files
        return delegate.newByteChannel(path, options, *attrs)
    }

    /**
     * Check if a specifier is a bare built-in module reference.
     * Examples: "World", "file:///World", "World" (from URI)
     */
    private fun isBareBuiltInSpecifier(specifier: String): Boolean {
        // Extract just the module name from URI/path
        val name = extractModuleName(specifier)

        // It's a built-in if:
        // 1. Name is in our built-in list
        // 2. Doesn't have .js extension
        // 3. Doesn't look like a relative path (../, ./)
        val isBuiltIn = name in BUILT_IN_MODULES &&
                !name.endsWith(".js") &&
                !specifier.contains("../") &&
                !specifier.contains("./")

        if (isBuiltIn) {
            ConfigManager.debug("Detected bare built-in specifier: $specifier -> $name")
        }

        return isBuiltIn
    }

    /**
     * Extract the module name from various formats:
     * - "World" -> "World"
     * - "file:///World" -> "World"
     * - "/path/to/World" -> "World"
     */
    private fun extractModuleName(specifier: String): String {
        return specifier
            .substringAfterLast('/')
            .substringAfterLast(':')
            .substringBefore('?')
            .substringBefore('#')
    }

    /**
     * Generate ES6 module code for a built-in module.
     * The module exports the API from the global __builtins__ namespace.
     */
    private fun generateBuiltInModule(name: String): String {
        val moduleCode = """
            // Built-in module: $name
            // Generated by RhettJSFileSystem
            const api = globalThis.__builtin_$name;
            if (!api) {
                throw new Error('Built-in module "$name" not found on globalThis');
            }
            export default api;
        """.trimIndent()
        ConfigManager.debug("Generated virtual module for '$name':\n$moduleCode")
        return moduleCode
    }

    // Delegate all other FileSystem methods to the default implementation

    override fun toAbsolutePath(path: Path): Path {
        // Virtual paths are already absolute
        if (path.toString().startsWith(VIRTUAL_PREFIX)) {
            return path
        }
        return delegate.toAbsolutePath(path)
    }

    override fun toRealPath(path: Path, vararg linkOptions: LinkOption?): Path {
        if (path.toString().startsWith(VIRTUAL_PREFIX)) {
            return path // Virtual paths don't have real paths
        }
        return delegate.toRealPath(path, *linkOptions)
    }

    override fun readAttributes(
        path: Path,
        attributes: String?,
        vararg options: LinkOption?
    ): MutableMap<String, Any> {
        if (path.toString().startsWith(VIRTUAL_PREFIX)) {
            // Return minimal attributes for virtual files
            return mutableMapOf(
                "isRegularFile" to true,
                "isDirectory" to false,
                "size" to 1024L, // Approximate size
                "lastModifiedTime" to FileTime.fromMillis(System.currentTimeMillis())
            )
        }
        return delegate.readAttributes(path, attributes, *options)
    }

    override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> =
        delegate.newDirectoryStream(dir, filter)

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>?) =
        delegate.createDirectory(dir, *attrs)

    override fun delete(path: Path) = delegate.delete(path)

    override fun copy(source: Path, target: Path, vararg options: CopyOption?) =
        delegate.copy(source, target, *options)

    override fun move(source: Path, target: Path, vararg options: CopyOption?) =
        delegate.move(source, target, *options)

    override fun createLink(link: Path, existing: Path) =
        delegate.createLink(link, existing)

    override fun createSymbolicLink(link: Path, target: Path, vararg attrs: FileAttribute<*>?) =
        delegate.createSymbolicLink(link, target, *attrs)

    override fun readSymbolicLink(link: Path): Path =
        delegate.readSymbolicLink(link)

    override fun setCurrentWorkingDirectory(currentWorkingDirectory: Path) =
        delegate.setCurrentWorkingDirectory(currentWorkingDirectory)

    override fun getTempDirectory(): Path =
        delegate.getTempDirectory()

    override fun isSameFile(path1: Path, path2: Path, vararg options: LinkOption?): Boolean =
        delegate.isSameFile(path1, path2, *options)
}

/**
 * SeekableByteChannel implementation for in-memory content.
 * Used to provide virtual module content without actual files.
 */
class ByteArraySeekableByteChannel(
    private val content: ByteArray
) : SeekableByteChannel {
    private var position = 0L
    private var closed = false

    override fun read(dst: ByteBuffer): Int {
        if (closed) throw ClosedChannelException()
        if (position >= content.size) return -1

        val remaining = (content.size - position).toInt()
        val toRead = minOf(dst.remaining(), remaining)

        dst.put(content, position.toInt(), toRead)
        position += toRead

        return toRead
    }

    override fun position(): Long = position

    override fun position(newPosition: Long): SeekableByteChannel {
        if (closed) throw ClosedChannelException()
        position = newPosition.coerceIn(0, content.size.toLong())
        return this
    }

    override fun size(): Long = content.size.toLong()

    override fun isOpen(): Boolean = !closed

    override fun close() {
        closed = true
    }

    // Write operations not supported for read-only virtual modules
    override fun write(src: ByteBuffer): Int =
        throw UnsupportedOperationException("Write not supported for virtual modules")

    override fun truncate(size: Long): SeekableByteChannel =
        throw UnsupportedOperationException("Truncate not supported for virtual modules")
}
