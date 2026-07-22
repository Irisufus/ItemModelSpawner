package me.iris.itemmodelspawner.client

import me.iris.itemmodelspawner.client.screens.ModelSelectorScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.server.permissions.Permissions
import org.lwjgl.glfw.GLFW

class ItemModelSpawnerClient : ClientModInitializer {

    companion object {
        lateinit var menuKey: KeyMapping
    }

    override fun onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping("Open Item Model Spawn Menu", GLFW.GLFW_KEY_V, KeyMapping.Category.CREATIVE)
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (menuKey.consumeClick()) {
                val player = client.player

                if (player != null && client.screen == null) {
                    val isCreative = player.isCreative
                    val isSingleplayer = client.hasSingleplayerServer()
                    val isOp = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)

                    if (isCreative && (isSingleplayer || isOp)) {
                        client.setScreen(ModelSelectorScreen())
                    }
                }
            }
        }

    }
}
