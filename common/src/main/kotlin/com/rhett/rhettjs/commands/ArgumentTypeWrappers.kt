package com.rhett.rhettjs.commands

import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.*
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument
import net.minecraft.commands.arguments.blocks.BlockStateArgument
import net.minecraft.commands.arguments.coordinates.*
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.commands.arguments.item.ItemPredicateArgument
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * Wrapper for Brigadier argument types, providing JavaScript-accessible typed arguments
 * with autocomplete support.
 *
 * Plain Kotlin class for better Rhino 1.8.1 compatibility - properties are exposed automatically.
 */
class ArgumentTypeWrappers {
    // Numeric types
    val BOOLEAN = BooleanArg
    val FLOAT = FloatArg
    val DOUBLE = DoubleArg
    val INTEGER = IntegerArg
    val LONG = LongArg

    // String types
    val STRING = StringArg
    val GREEDY_STRING = GreedyStringArg
    val WORD = WordArg

    // Entity/Player types
    val ENTITY = EntityArg
    val ENTITIES = EntitiesArg
    val PLAYER = PlayerArg
    val PLAYERS = PlayersArg
    val GAME_PROFILE = GameProfileArg

    // Position types
    val BLOCK_POS = BlockPosArg
    val COLUMN_POS = ColumnPosArg
    val VEC3 = Vec3Arg
    val VEC2 = Vec2Arg

    // Block types
    val BLOCK_STATE = BlockStateArg
    val BLOCK_PREDICATE = BlockPredicateArg

    // Item types
    val ITEM_STACK = ItemStackArg
    val ITEM_PREDICATE = ItemPredicateArg

    // Message/Chat types
    val COLOR = ColorArg
    val COMPONENT = ComponentArg
    val MESSAGE = MessageArg

    // NBT types
    val NBT_COMPOUND = NbtCompoundArg
    val NBT_TAG = NbtTagArg
    val NBT_PATH = NbtPathArg

    // Misc types
    val PARTICLE = ParticleArg
    val ANGLE = AngleArg
    val ROTATION = RotationArg
    val DIMENSION = DimensionArg
    val RESOURCE_LOCATION = ResourceLocationArg
    val UUID = UuidArg

    companion object {
        /**
         * Create an ArgumentTypes object for JavaScript access.
         */
        fun create(scope: Scriptable): ArgumentTypeWrappers {
            return ArgumentTypeWrappers()
        }
    }

    // Numeric argument types
    object BooleanArg : ArgumentTypeWrapper<Boolean>(
        { BoolArgumentType.bool() },
        { ctx, name -> BoolArgumentType.getBool(ctx, name) }
    )

    object FloatArg : ArgumentTypeWrapper<Float>(
        { FloatArgumentType.floatArg() },
        { ctx, name -> FloatArgumentType.getFloat(ctx, name) }
    )

    object DoubleArg : ArgumentTypeWrapper<Double>(
        { DoubleArgumentType.doubleArg() },
        { ctx, name -> DoubleArgumentType.getDouble(ctx, name) }
    )

    object IntegerArg : ArgumentTypeWrapper<Int>(
        { IntegerArgumentType.integer() },
        { ctx, name -> IntegerArgumentType.getInteger(ctx, name) }
    )

    object LongArg : ArgumentTypeWrapper<Long>(
        { LongArgumentType.longArg() },
        { ctx, name -> LongArgumentType.getLong(ctx, name) }
    )

    // String argument types
    object StringArg : ArgumentTypeWrapper<String>(
        { StringArgumentType.string() },
        { ctx, name -> StringArgumentType.getString(ctx, name) }
    )

    object GreedyStringArg : ArgumentTypeWrapper<String>(
        { StringArgumentType.greedyString() },
        { ctx, name -> StringArgumentType.getString(ctx, name) }
    )

    object WordArg : ArgumentTypeWrapper<String>(
        { StringArgumentType.word() },
        { ctx, name -> StringArgumentType.getString(ctx, name) }
    )

    // Entity argument types
    object EntityArg : ArgumentTypeWrapper<Any>(
        { EntityArgument.entity() },
        { ctx, name -> EntityArgument.getEntity(ctx, name) }
    )

    object EntitiesArg : ArgumentTypeWrapper<Any>(
        { EntityArgument.entities() },
        { ctx, name -> EntityArgument.getEntities(ctx, name) }
    )

    object PlayerArg : ArgumentTypeWrapper<Any>(
        { EntityArgument.player() },
        { ctx, name -> EntityArgument.getPlayer(ctx, name) }
    )

    object PlayersArg : ArgumentTypeWrapper<Any>(
        { EntityArgument.players() },
        { ctx, name -> EntityArgument.getPlayers(ctx, name) }
    )

    object GameProfileArg : ArgumentTypeWrapper<Any>(
        { GameProfileArgument.gameProfile() },
        { ctx, name -> GameProfileArgument.getGameProfiles(ctx, name) }
    )

    // Position argument types
    object BlockPosArg : ArgumentTypeWrapper<Any>(
        { BlockPosArgument.blockPos() },
        { ctx, name -> BlockPosArgument.getSpawnablePos(ctx, name) }
    )

    object ColumnPosArg : ArgumentTypeWrapper<Any>(
        { ColumnPosArgument.columnPos() },
        { ctx, name -> ColumnPosArgument.getColumnPos(ctx, name) }
    )

    object Vec3Arg : ArgumentTypeWrapper<Any>(
        { Vec3Argument.vec3(false) },
        { ctx, name -> Vec3Argument.getVec3(ctx, name) }
    )

    object Vec2Arg : ArgumentTypeWrapper<Any>(
        { Vec2Argument.vec2(false) },
        { ctx, name -> Vec2Argument.getVec2(ctx, name) }
    )

    // Block argument types
    object BlockStateArg : ArgumentTypeWrapper<Any>(
        { BlockStateArgument.block(null) },
        { ctx, name -> BlockStateArgument.getBlock(ctx, name) }
    )

    object BlockPredicateArg : ArgumentTypeWrapper<Any>(
        { BlockPredicateArgument.blockPredicate(null) },
        { ctx, name -> BlockPredicateArgument.getBlockPredicate(ctx, name) }
    )

    // Item argument types
    object ItemStackArg : ArgumentTypeWrapper<Any>(
        { ItemArgument.item(null) },
        { ctx, name -> ItemArgument.getItem(ctx, name) }
    )

    object ItemPredicateArg : ArgumentTypeWrapper<Any>(
        { ItemPredicateArgument.itemPredicate(null) },
        { ctx, name -> ItemPredicateArgument.getItemPredicate(ctx, name) }
    )

    // Message/Chat argument types
    object ColorArg : ArgumentTypeWrapper<Any>(
        { ColorArgument.color() },
        { ctx, name -> ColorArgument.getColor(ctx, name) }
    )

    object ComponentArg : ArgumentTypeWrapper<Any>(
        { ComponentArgument.textComponent(null) },
        { ctx, name -> ComponentArgument.getComponent(ctx, name) }
    )

    object MessageArg : ArgumentTypeWrapper<Any>(
        { MessageArgument.message() },
        { ctx, name -> MessageArgument.getMessage(ctx, name) }
    )

    // NBT argument types
    object NbtCompoundArg : ArgumentTypeWrapper<Any>(
        { CompoundTagArgument.compoundTag() },
        { ctx, name -> CompoundTagArgument.getCompoundTag(ctx, name) }
    )

    object NbtTagArg : ArgumentTypeWrapper<Any>(
        { NbtTagArgument.nbtTag() },
        { ctx, name -> NbtTagArgument.getNbtTag(ctx, name) }
    )

    object NbtPathArg : ArgumentTypeWrapper<Any>(
        { NbtPathArgument.nbtPath() },
        { ctx, name -> NbtPathArgument.getPath(ctx, name) }
    )

    // Misc argument types
    object ParticleArg : ArgumentTypeWrapper<Any>(
        { ParticleArgument.particle(null) },
        { ctx, name -> ParticleArgument.getParticle(ctx, name) }
    )

    object AngleArg : ArgumentTypeWrapper<Any>(
        { AngleArgument.angle() },
        { ctx, name -> AngleArgument.getAngle(ctx, name) }
    )

    object RotationArg : ArgumentTypeWrapper<Any>(
        { RotationArgument.rotation() },
        { ctx, name -> RotationArgument.getRotation(ctx, name) }
    )

    object DimensionArg : ArgumentTypeWrapper<Any>(
        { DimensionArgument.dimension() },
        { ctx, name -> DimensionArgument.getDimension(ctx, name) }
    )

    object ResourceLocationArg : ArgumentTypeWrapper<Any>(
        { ResourceLocationArgument.id() },
        { ctx, name -> ResourceLocationArgument.getId(ctx, name) }
    )

    object UuidArg : ArgumentTypeWrapper<Any>(
        { UuidArgument.uuid() },
        { ctx, name -> UuidArgument.getUuid(ctx, name) }
    )
}

/**
 * Base class for argument type wrappers.
 * Provides both the ArgumentType for registration and getter for extraction.
 * Plain Kotlin class (not ScriptableObject) for better Rhino compatibility.
 */
open class ArgumentTypeWrapper<T>(
    private val factory: () -> com.mojang.brigadier.arguments.ArgumentType<*>,
    private val getter: (CommandContext<CommandSourceStack>, String) -> T
) {
    /**
     * Create the Brigadier ArgumentType for registration.
     */
    fun create(): com.mojang.brigadier.arguments.ArgumentType<*> = factory()

    /**
     * Get the parsed argument value from a command context.
     * Called from JavaScript as: argumentType.get(context, 'argName')
     */
    fun get(context: Any, name: String): T? {
        return try {
            // Handle wrapped context (plain JS object with unwrap() method)
            val ctx = when {
                context is Scriptable && context.has("unwrap", context) -> {
                    // Call unwrap() to get the raw context
                    val unwrapFunc = context.get("unwrap", context) as? org.mozilla.javascript.Function
                    val unwrapped = unwrapFunc?.call(Context.getCurrentContext(), context, context, emptyArray())
                    if (unwrapped is NativeJavaObject) {
                        unwrapped.unwrap() as CommandContext<CommandSourceStack>
                    } else {
                        unwrapped as CommandContext<CommandSourceStack>
                    }
                }
                context is NativeJavaObject -> {
                    context.unwrap() as CommandContext<CommandSourceStack>
                }
                else -> {
                    context as CommandContext<CommandSourceStack>
                }
            }
            getter(ctx, name)
        } catch (e: Exception) {
            null
        }
    }
}
