package com.rhett.rhettjs.structure.utils

import java.nio.file.Path

/**
 * Common utilities for structure NBT operations.
 * Shared between StructureNbtManager and LargeStructureNbtManager.
 */
object StructureNbtUtils {

    /**
     * Generated folder structure subdirectory name.
     * Per Minecraft wiki: runtime structures are saved to generated/<namespace>/structures/ (plural)
     * This differs from datapack structures which use data/<namespace>/structure/ (singular).
     * Minecraft's StructureTemplateManager searches both locations.
     */
    const val GENERATED_STRUCTURES_DIR = "structures"

    /**
     * Subdirectory for RhettJS large structures (multi-chunk NBT structures).
     * Stored as: generated/<namespace>/structures/rjs-large/<name>/<X>_<Y>_<Z>.nbt
     */
    const val RJS_LARGE_SUBDIR = "rjs-large"

    /**
     * Parse structure name format "[namespace:]name".
     * Returns pair of (namespace, name).
     * Defaults to "minecraft" namespace if not specified.
     */
    fun parseStructureName(nameWithNamespace: String): Pair<String, String> {
        return if (':' in nameWithNamespace) {
            val parts = nameWithNamespace.split(':', limit = 2)
            parts[0] to parts[1]
        } else {
            "minecraft" to nameWithNamespace
        }
    }

    /**
     * Get the file path for a structure in the generated folder.
     * Uses Minecraft's convention: generated/<namespace>/structures/name.nbt (plural)
     * Note: Datapack structures use data/<namespace>/structure/ (singular), but we save to generated/.
     * Minecraft's StructureTemplateManager reads from both locations.
     */
    fun getStructurePath(basePath: Path?, namespace: String, name: String): Path? {
        if (basePath == null) return null
        return basePath.resolve(namespace).resolve(GENERATED_STRUCTURES_DIR).resolve("$name.nbt")
    }

    /**
     * Validate structure name contains only valid characters for ResourceLocations.
     * Structure names must contain only lowercase letters, numbers, and characters /._-
     *
     * @throws IllegalArgumentException if name is invalid
     */
    fun validateStructureName(name: String) {
        if (!name.matches(Regex("^[a-z0-9/._-]+$"))) {
            throw IllegalArgumentException(
                "Invalid structure name '$name'. Structure names must contain only lowercase letters, numbers, and characters /._-\n" +
                "Example: 'my_structure' or 'buildings/house_1'"
            )
        }
    }
}
