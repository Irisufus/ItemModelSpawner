package me.iris.itemmodelspawner.messaging

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.Identifier
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class DisplaySpawnPayload(val itemId: Identifier, val modelId: Identifier, val color: Int) : CustomPacketPayload {
    companion object {
        const val NO_COLOR = -1

        val TYPE = CustomPacketPayload.Type<DisplaySpawnPayload>(Identifier.fromNamespaceAndPath("modelviewer", "spawn_display"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, DisplaySpawnPayload> = StreamCodec.composite(
            Identifier.STREAM_CODEC,
            DisplaySpawnPayload::itemId,
            Identifier.STREAM_CODEC,
            DisplaySpawnPayload::modelId,
            ByteBufCodecs.INT,
            DisplaySpawnPayload::color,
            ::DisplaySpawnPayload
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}