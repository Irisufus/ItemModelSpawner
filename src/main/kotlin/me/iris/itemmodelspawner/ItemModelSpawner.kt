package me.iris.itemmodelspawner

import me.iris.itemmodelspawner.messaging.DisplaySpawnPayload
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack

class ItemModelSpawner : ModInitializer {

    override fun onInitialize() {
        PayloadTypeRegistry.playC2S().register(DisplaySpawnPayload.TYPE, DisplaySpawnPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(DisplaySpawnPayload.TYPE) { payload, context ->
            val player = context.player()
            val server = player.level().server

            server.execute {
                val isSingleplayer = server.isSingleplayer
                val isCreative = player.isCreative
                val hasOp = server.playerList.isOp(player.nameAndId())

                if (isCreative && (isSingleplayer || hasOp)) {
                    val level = player.level()
                    val display = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND)
                    val item = BuiltInRegistries.ITEM.get(payload.itemId)

                    if (display != null) {
                        val lookVec = player.lookAngle
                        val x = player.x + lookVec.x * 2.0
                        val y = player.y + player.eyeHeight
                        val z = player.z + lookVec.z * 2.0
                        display.setPos(x, y, z)
                        val itemstack = ItemStack(item.get())
                        itemstack.set(DataComponents.ITEM_MODEL, payload.modelId)
                        display.itemStack = itemstack

                        level.addFreshEntity(display)
                    }
                }
            }
        }
    }
}
