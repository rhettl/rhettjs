package com.rhett.rhettjs.network

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.engine.api.NetworkAPIProxy
import com.rhett.rhettjs.network.PacketContext
import com.rhett.rhettjs.network.PacketData
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.PayloadRegistrar

/**
 * NeoForge implementation of custom packet handling for RhettJS Network API.
 * Handles packet registration and routing on both client and server.
 */
object NeoForgeNetworkHandler : NetworkPlatform {

    // Packet channel identifier
    private val PACKET_ID = ResourceLocation.fromNamespaceAndPath("rhettjs", "network")

    // Protocol version
    private const val PROTOCOL_VERSION = "1"

    /**
     * Custom packet payload for RhettJS network communication.
     * Contains the channel name and JSON-serialized data.
     */
    data class RhettJSPacket(
        val channel: String,
        val jsonData: String
    ) : CustomPacketPayload {
        override fun id(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE: CustomPacketPayload.Type<RhettJSPacket> =
                CustomPacketPayload.Type<RhettJSPacket>(PACKET_ID)

            val CODEC: StreamCodec<FriendlyByteBuf, RhettJSPacket> =
                StreamCodec.of(
                    { buf, packet ->
                        buf.writeUtf(packet.channel)
                        buf.writeUtf(packet.jsonData)
                    },
                    { buf ->
                        val channel = buf.readUtf()
                        val jsonData = buf.readUtf()
                        RhettJSPacket(channel, jsonData)
                    }
                )
        }
    }

    /**
     * Register packet handlers.
     * Called during mod initialization (RegisterPayloadHandlersEvent).
     */
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(PROTOCOL_VERSION)

        // Register packet type
        registrar.playBidirectional(
            RhettJSPacket.TYPE,
            RhettJSPacket.CODEC,
            { packet, context ->
                handleReceive(packet, context)
            }
        )

        // Register this handler as the platform implementation
        NetworkPlatform.register(this)

        RhettJSCommon.LOGGER.info("[RhettJS Network] NeoForge networking initialized")
    }

    /**
     * Handle packet received on either side.
     */
    private fun handleReceive(packet: RhettJSPacket, context: IPayloadContext) {
        // Process on the appropriate thread
        context.enqueueWork {
            try {
                if (context.flow().isServerbound) {
                    // Client → Server
                    handleServerReceive(packet, context)
                } else {
                    // Server → Client
                    handleClientReceive(packet)
                }
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("Error handling network packet", e)
            }
        }
    }

    /**
     * Handle packet received on server (from client).
     */
    private fun handleServerReceive(packet: RhettJSPacket, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val networkManager = NetworkAPIProxy.getManager()

        // Deserialize the packet
        val packetData = networkManager.deserializePacket(packet.jsonData)

        // Create context with sender information
        val packetContext = networkManager.createContext(
            senderUuid = player.uuid.toString(),
            senderName = player.scoreboardName,
            position = player.position(),
            channel = packetData.channel
        )

        // Route to registered handlers
        NetworkAPIProxy.routePacket(packetData, packetContext)
    }

    /**
     * Handle packet received on client (from server).
     */
    private fun handleClientReceive(packet: RhettJSPacket) {
        val networkManager = NetworkAPIProxy.getManager()

        // Deserialize the packet
        val packetData = networkManager.deserializePacket(packet.jsonData)

        // Create context (server-sent, so no position)
        val context = PacketContext(
            senderUuid = "server",
            senderName = "Server",
            position = null,
            timestamp = System.currentTimeMillis(),
            channel = packetData.channel
        )

        // Route to registered handlers
        NetworkAPIProxy.routePacket(packetData, context)
    }

    // =====================================
    // Packet Sending API
    // =====================================

    /**
     * Send packet from client to server.
     * Called from client scripts via NetworkAPIProxy.
     */
    override fun sendToServer(packet: PacketData) {
        val networkManager = NetworkAPIProxy.getManager()
        val json = networkManager.serializePacket(packet)
        val rhettPacket = RhettJSPacket(packet.channel, json)

        // Get packet dispatcher from client
        val connection = net.minecraft.client.Minecraft.getInstance().connection
        connection?.send(rhettPacket)
    }

    /**
     * Send packet from server to specific client.
     * Called from server scripts via NetworkAPIProxy.
     */
    override fun sendToClient(playerUuid: String, packet: PacketData) {
        val server = RhettJSCommon.getServer() ?: throw IllegalStateException("Server not available")
        val player = server.playerList.getPlayer(java.util.UUID.fromString(playerUuid))
            ?: throw IllegalArgumentException("Player not found: $playerUuid")

        val networkManager = NetworkAPIProxy.getManager()
        val json = networkManager.serializePacket(packet)
        val rhettPacket = RhettJSPacket(packet.channel, json)

        player.connection.send(rhettPacket)
    }

    /**
     * Broadcast packet from server to all clients.
     * Called from server scripts via NetworkAPIProxy.
     */
    override fun broadcast(packet: PacketData) {
        val networkManager = NetworkAPIProxy.getManager()
        val json = networkManager.serializePacket(packet)
        val rhettPacket = RhettJSPacket(packet.channel, json)

        // Send to all connected players
        for (player in RhettJSCommon.getServer()?.playerList?.players ?: emptyList()) {
            player.connection.send(rhettPacket)
        }
    }

    /**
     * Broadcast packet to clients within range of a position.
     * Called from server scripts via NetworkAPIProxy.
     */
    override fun broadcastInRange(position: Map<String, Double>, range: Double, packet: PacketData) {
        val posX = position["x"] ?: throw IllegalArgumentException("Missing x coordinate")
        val posY = position["y"] ?: throw IllegalArgumentException("Missing y coordinate")
        val posZ = position["z"] ?: throw IllegalArgumentException("Missing z coordinate")

        val networkManager = NetworkAPIProxy.getManager()
        val json = networkManager.serializePacket(packet)
        val rhettPacket = RhettJSPacket(packet.channel, json)

        val rangeSq = range * range

        // Send to players within range
        for (player in RhettJSCommon.getServer()?.playerList?.players ?: emptyList()) {
            val distSq = player.position().distanceToSqr(posX, posY, posZ)
            if (distSq <= rangeSq) {
                player.connection.send(rhettPacket)
            }
        }
    }
}
