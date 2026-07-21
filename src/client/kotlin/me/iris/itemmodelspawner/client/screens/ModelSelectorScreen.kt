package me.iris.itemmodelspawner.screens

import me.iris.itemmodelspawner.messaging.DisplaySpawnPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import kotlin.math.max

class ModelSelectorScreen : Screen(Component.literal("Loaded Item Models")) {
    private val loadedItems : MutableList<ItemStack> = mutableListOf()
    private var scrollOffset = 0

    companion object {
        private const val COLUMNS = 9
        private const val SLOT_SIZE = 18
        private const val PADDING = 10
    }

    override fun init() {
        super.init()
        loadedItems.clear()
        for (item in BuiltInRegistries.ITEM) {
            loadedItems.add(ItemStack(item))
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val startX = (this.width - (COLUMNS * SLOT_SIZE)) / 2
        val startY = PADDING + 25

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, PADDING, 0xFFFFFF)

        for (i in loadedItems.indices) {
            val row = i / COLUMNS
            val col = i % COLUMNS
            val x = startX + (col * SLOT_SIZE)
            val y = startY + (row * SLOT_SIZE) - scrollOffset

            if (y > PADDING + 15 && y < this.height - PADDING - 20) {
                val stack = loadedItems[i]
                guiGraphics.fill(x, y, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, 0x33FFFFFF)
                guiGraphics.renderItem(stack, x, y)

                if (mouseX in x..(x + SLOT_SIZE) && mouseY in y..(y + SLOT_SIZE)) {
                    val tooltipContext = Item.TooltipContext.of(minecraft.level)
                    val component = stack.getTooltipLines(tooltipContext, minecraft.player, TooltipFlag.NORMAL)
                    val clientTooltip = component.map { component ->
                        ClientTooltipComponent.create(component.visualOrderText)
                    }
                    guiGraphics.renderTooltip(
                        font,
                        clientTooltip,
                        mouseX,
                        mouseY,
                        DefaultTooltipPositioner.INSTANCE,
                        null
                    )
                }
            }
        }
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (mouseButtonEvent.button() != 0) return super.mouseClicked(mouseButtonEvent, bl)

        val mouseX = mouseButtonEvent.x
        val mouseY = mouseButtonEvent.y

        val startX = (this.width - (COLUMNS * SLOT_SIZE)) / 2
        val startY = PADDING + 25

        for (i in loadedItems.indices) {
            val row = i / COLUMNS
            val col = i % COLUMNS
            val x = startX + (col * SLOT_SIZE)
            val y = startY + (row * SLOT_SIZE) - scrollOffset

            if (y > PADDING + 15 && y < this.height - PADDING - 20) {
                if (mouseX in x.toDouble()..(x + SLOT_SIZE).toDouble() && mouseY in y.toDouble()..(y + SLOT_SIZE).toDouble()) {
                    val stack = loadedItems[i]
                    val itemId = BuiltInRegistries.ITEM.getKey(stack.item)

                    ClientPlayNetworking.send(DisplaySpawnPayload(itemId))
                    return true
                }
            }
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollOffset = max(0, scrollOffset - (scrollY * 16).toInt())
        return true
    }

    override fun isPauseScreen(): Boolean = false
}