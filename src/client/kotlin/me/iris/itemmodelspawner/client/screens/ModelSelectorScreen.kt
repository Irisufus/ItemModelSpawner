package me.iris.itemmodelspawner.client.screens

import me.iris.itemmodelspawner.client.util.Model
import me.iris.itemmodelspawner.messaging.DisplaySpawnPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

class ModelSelectorScreen : Screen(Component.literal("Loaded Item Models")) {
    private val loadedItems : MutableList<Model> = mutableListOf()
    private val visibleItems: MutableList<Model> = mutableListOf()
    private var scrollOffset = 0
    private var draggingScrollbar = false
    private var showResourcePack = false

    private lateinit var searchBox: EditBox
    private lateinit var toggleButton: Button

    companion object {
        private const val COLUMNS = 12
        private const val SLOT_SIZE = 18
        private const val PADDING = 10
    }

    override fun init() {
        super.init()

        if (loadedItems.isEmpty()) {
            loadItems()
        }

        val gridWidth = COLUMNS * SLOT_SIZE
        val startX = (this.width - gridWidth) / 2

        searchBox = EditBox(this.font, startX, PADDING, gridWidth / 2 - 2, 20, Component.literal("Search Models"))
        searchBox.setResponder { updateFilter() }
        this.addRenderableWidget(searchBox)

        toggleButton = Button.builder(Component.literal("All")) { btn ->
            showResourcePack = !showResourcePack
            btn.message = Component.literal(if (showResourcePack) "Resource Pack" else "All")
            updateFilter()
        }.bounds(startX + gridWidth / 2 + 2, PADDING, gridWidth / 2 - 2, 20).build()
        this.addRenderableWidget(toggleButton)

        updateFilter()
    }

    private fun updateFilter() {
        val query = searchBox.value.lowercase()
        visibleItems.clear()

        for (item in loadedItems) {
            if (showResourcePack && item.builtIn) continue

            val hoverName = item.item.hoverName.string.lowercase()
            val idName = item.modelId.toString().lowercase()

            if (hoverName.contains(query) || idName.contains(query)) {
                visibleItems.add(item)
            }
        }

        scrollOffset = 0
    }

    private fun loadItems() {
        val resourceManager = minecraft.resourceManager

        val resources = resourceManager.listResources("items") { location ->
            location.path.endsWith(".json")
        }

        resources.keys.forEach { resourceLocation ->
            val namespace = resourceLocation.namespace
            val path = resourceLocation.path
            val modelName = path.substring(6, path.length - 5)
            val modelId = Identifier.fromNamespaceAndPath(namespace, modelName)

            if (!BuiltInRegistries.ITEM.containsKey(modelId)) {

                val stack = ItemStack(Items.PAPER)
                stack.set(DataComponents.ITEM_MODEL, modelId)

                val name = Component.literal(modelId.toString())
                stack.set(DataComponents.CUSTOM_NAME, name)

                loadedItems.add(Model(stack, modelId, false))
            }
        }

        for (item in BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                val stack = ItemStack(item)
                loadedItems.add(Model(stack, BuiltInRegistries.ITEM.getKey(item), true))
            }
        }
    }

    private fun getMaxScroll(): Int {
        val startY = PADDING + 35
        val listHeight = this.height - startY - PADDING
        val totalRows = (visibleItems.size + COLUMNS - 1) / COLUMNS
        val totalContentHeight = totalRows * SLOT_SIZE
        return max(0, totalContentHeight - listHeight)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val maxScroll = getMaxScroll()

        scrollOffset = min(maxScroll, max(0, scrollOffset))

        val startX = (this.width - (COLUMNS * SLOT_SIZE)) / 2
        val startY = PADDING + 35
        val listHeight = this.height - startY - PADDING
        val scrollbarX = startX + (COLUMNS * SLOT_SIZE) + 5

        // Scrollbar
        guiGraphics.fill(scrollbarX, startY, scrollbarX + 6, startY + listHeight, 0x88000000.toInt())

        if (maxScroll > 0) {
            val thumbHeight = max(20, (listHeight.toFloat() / (maxScroll + listHeight) * listHeight).toInt())
            val thumbY = startY + ((scrollOffset.toFloat() / maxScroll) * (listHeight - thumbHeight)).toInt()

            val isHoveringThumb = mouseX in scrollbarX..(scrollbarX + 6) && mouseY in thumbY..(thumbY + thumbHeight)
            val thumbColor = if (draggingScrollbar || isHoveringThumb) 0xFFAAAAAA.toInt() else 0xFF888888.toInt()

            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 6, thumbY + thumbHeight, thumbColor)
        }

        // Items
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, PADDING, 0xFFFFFF)

        guiGraphics.enableScissor(startX, startY, startX + (COLUMNS * SLOT_SIZE), startY + listHeight)

        for (i in visibleItems.indices) {
            val row = i / COLUMNS
            val col = i % COLUMNS
            val x = startX + (col * SLOT_SIZE)
            val y = startY + (row * SLOT_SIZE) - scrollOffset

            guiGraphics.fill(x, y, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, 0x33FFFFFF)
            guiGraphics.renderItem(visibleItems[i].item, x, y)
        }

        guiGraphics.disableScissor()

        for (i in visibleItems.indices) {
            val row = i / COLUMNS
            val col = i % COLUMNS
            val x = startX + (col * SLOT_SIZE)
            val y = startY + (row * SLOT_SIZE) - scrollOffset

            if (y >= startY && y <= startY + listHeight - SLOT_SIZE) {
                if (mouseX in x..(x + SLOT_SIZE) && mouseY in y..(y + SLOT_SIZE)) {
                    val stack = visibleItems[i].item
                    val tooltipContext = Item.TooltipContext.of(minecraft.level)
                    val component = stack.getTooltipLines(tooltipContext, minecraft.player, TooltipFlag.NORMAL)
                    val clientTooltip = component.map { component ->
                        ClientTooltipComponent.create(component.visualOrderText)
                    }
                    guiGraphics.renderTooltip(font, clientTooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null)
                }
            }
        }
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (mouseButtonEvent.button() == 0) {
            draggingScrollbar = false
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (super.mouseClicked(mouseButtonEvent, bl)) return true
        if (mouseButtonEvent.button() != 0) return super.mouseClicked(mouseButtonEvent, bl)

        val mouseX = mouseButtonEvent.x
        val mouseY = mouseButtonEvent.y

        val startX = (this.width - (COLUMNS * SLOT_SIZE)) / 2
        val startY = PADDING + 25
        val listHeight = this.height - startY - PADDING
        val scrollbarX = startX + (COLUMNS * SLOT_SIZE) + 5

        if (getMaxScroll() > 0 && mouseX >= scrollbarX && mouseX <= scrollbarX + 6 && mouseY >= startY && mouseY <= startY + listHeight) {
            draggingScrollbar = true
            return true
        }

        for (i in visibleItems.indices) {
            val row = i / COLUMNS
            val col = i % COLUMNS
            val x = startX + (col * SLOT_SIZE)
            val y = startY + (row * SLOT_SIZE) - scrollOffset

            if (y > PADDING + 15 && y < this.height - PADDING - 20) {
                if (mouseX in x.toDouble()..(x + SLOT_SIZE).toDouble() && mouseY in y.toDouble()..(y + SLOT_SIZE).toDouble()) {
                    val stack = visibleItems[i]
                    val itemId = BuiltInRegistries.ITEM.getKey(stack.item.item)

                    if (ClientPlayNetworking.canSend(DisplaySpawnPayload.TYPE)) {
                        ClientPlayNetworking.send(DisplaySpawnPayload(itemId, stack.modelId))
                    }
                    else {
                        val player = minecraft.player!!
                        val lookVec = player.lookAngle
                        val x = player.x + lookVec.x * 2.0
                        val y = player.y + player.eyeHeight
                        val z = player.z + lookVec.z * 2.0

                        val sx = String.format(java.util.Locale.US, "%.3f", x)
                        val sy = String.format(java.util.Locale.US, "%.3f", y)
                        val sz = String.format(java.util.Locale.US, "%.3f", z)

                        val command = "summon item_display $sx $sy $sz {item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"${stack.modelId}\"}}}"

                        player.connection.sendCommand(command)
                    }
                    onClose()
                    return true
                }
            }
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollOffset = min(getMaxScroll(), max(0, scrollOffset - (scrollY * 16).toInt()))
        return true
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragY: Double, e: Double): Boolean {
        if (draggingScrollbar) {
            val maxScroll = getMaxScroll()
            if (maxScroll <= 0) {
                scrollOffset = 0
                return true
            }

            val startY = PADDING + 35
            val listHeight = this.height - startY - PADDING
            val thumbHeight = max(20, (listHeight.toFloat() / (maxScroll + listHeight) * listHeight).toInt())
            val scrollableTrackHeight = listHeight - thumbHeight

            if (scrollableTrackHeight > 0) {
                val relativeY = (mouseButtonEvent.y - startY - (thumbHeight / 2.0)).coerceIn(0.0, scrollableTrackHeight.toDouble())
                val scrollRatio = relativeY / scrollableTrackHeight
                scrollOffset = (scrollRatio * maxScroll).toInt()
            }
        }
        return super.mouseDragged(mouseButtonEvent, dragY, e)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (searchBox.isFocused && keyEvent.key() != GLFW.GLFW_KEY_ESCAPE) {
            return searchBox.keyPressed(keyEvent)
        }
        return super.keyPressed(keyEvent)
    }

    override fun isPauseScreen(): Boolean = false
}