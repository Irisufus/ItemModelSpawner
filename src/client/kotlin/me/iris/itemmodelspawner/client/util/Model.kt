package me.iris.itemmodelspawner.client.util

import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

data class Model(
    val item: ItemStack,
    val modelId: Identifier,
    val builtIn: Boolean
)