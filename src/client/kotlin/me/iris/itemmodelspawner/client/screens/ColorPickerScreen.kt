package me.iris.itemmodelspawner.client.screens

import com.mojang.blaze3d.platform.InputConstants
import me.iris.itemmodelspawner.messaging.DisplaySpawnPayload
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.DyeColor
import kotlin.math.abs
import kotlin.math.roundToInt

class ColorPickerScreen(
    private val parent: Screen,
    initialColor: Int,
    private val onSelect: (Int) -> Unit
) : Screen(Component.literal("Color Picker")) {

    private var hue: Float = 40f
    private var saturation: Float = 1f
    private var value: Float = 1f
    private var hasColor: Boolean = (initialColor != DisplaySpawnPayload.NO_COLOR)

    private var r: Int = 255
    private var g: Int = 180
    private var b: Int = 0

    private lateinit var hexInput: EditBox
    private lateinit var clearButton: Button
    private lateinit var doneButton: Button
    private lateinit var cancelButton: Button

    private var updatingFromCode = false
    private var draggingSV = false
    private var draggingHue = false

    private data class DyeSwatch(val dyeColor: DyeColor, val x: Int, val y: Int, val width: Int, val height: Int)
    private val dyeSwatches = mutableListOf<DyeSwatch>()

    companion object {
        private const val DIALOG_WIDTH = 260
        private const val PREVIEW_WIDTH = 76
        private const val GRADIENT_HEIGHT = 90
        private const val HUE_SLIDER_HEIGHT = 12
        private const val SWATCH_SIZE = 14
        private const val SWATCH_GAP = 2

        fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f
            val max = maxOf(rf, gf, bf)
            val min = minOf(rf, gf, bf)
            val delta = max - min

            var h = 0f
            if (delta > 0.00001f) {
                h = when (max) {
                    rf -> ((gf - bf) / delta) % 6f
                    gf -> ((bf - rf) / delta) + 2f
                    else -> ((rf - gf) / delta) + 4f
                }
                h *= 60f
                if (h < 0f) h += 360f
            }

            val s = if (max <= 0.00001f) 0f else delta / max
            val v = max

            return floatArrayOf(h, s, v)
        }

        fun hsvToRgb(h: Float, s: Float, v: Float): Int {
            val clampedH = (h % 360f + 360f) % 360f
            val clampedS = s.coerceIn(0f, 1f)
            val clampedV = v.coerceIn(0f, 1f)

            val c = clampedV * clampedS
            val x = c * (1f - abs((clampedH / 60f) % 2f - 1f))
            val m = clampedV - c

            val (rPrime, gPrime, bPrime) = when ((clampedH / 60f).toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

            val red = ((rPrime + m) * 255f).roundToInt().coerceIn(0, 255)
            val green = ((gPrime + m) * 255f).roundToInt().coerceIn(0, 255)
            val blue = ((bPrime + m) * 255f).roundToInt().coerceIn(0, 255)

            return (red shl 16) or (green shl 8) or blue
        }
    }

    init {
        if (initialColor != DisplaySpawnPayload.NO_COLOR) {
            r = (initialColor shr 16) and 0xFF
            g = (initialColor shr 8) and 0xFF
            b = initialColor and 0xFF
            val hsv = rgbToHsv(r, g, b)
            hue = hsv[0]
            saturation = hsv[1]
            value = hsv[2]
            hasColor = true
        } else {
            val rgb = hsvToRgb(hue, saturation, value)
            r = (rgb shr 16) and 0xFF
            g = (rgb shr 8) and 0xFF
            b = rgb and 0xFF
            hasColor = false
        }
    }

    override fun init() {
        super.init()
        dyeSwatches.clear()

        val startX = (this.width - DIALOG_WIDTH) / 2
        val mainBoxY = 24
        val hueY = mainBoxY + GRADIENT_HEIGHT + 8
        val hexY = hueY + HUE_SLIDER_HEIGHT + 8

        // hex input and clear button
        hexInput = EditBox(this.font, startX, hexY, 116, 20, Component.literal("Hex Color"))
        hexInput.setMaxLength(7)
        hexInput.setHint(Component.literal("#RRGGBB"))
        hexInput.setResponder { text ->
            if (!updatingFromCode) {
                val clean = text.trim().removePrefix("#")
                if (clean.length == 6) {
                    val parsed = clean.toIntOrNull(16)
                    if (parsed != null) {
                        r = (parsed shr 16) and 0xFF
                        g = (parsed shr 8) and 0xFF
                        b = parsed and 0xFF
                        val hsv = rgbToHsv(r, g, b)
                        hue = hsv[0]
                        saturation = hsv[1]
                        value = hsv[2]
                        hasColor = true
                    }
                }
            }
        }
        this.addRenderableWidget(hexInput)

        clearButton = Button.builder(Component.literal("No Dye")) {
            hasColor = false
            updatingFromCode = true
            hexInput.value = ""
            updatingFromCode = false
        }.bounds(startX + 122, hexY, DIALOG_WIDTH - 122, 20)
            .tooltip(Tooltip.create(Component.literal("Remove dye color from item")))
            .build()
        this.addRenderableWidget(clearButton)

        // minecraft dyes
        val dyesY = hexY + 26
        val dyeEntries = DyeColor.entries
        val dyesPerRow = 8
        val paletteStartX = startX + (DIALOG_WIDTH - (dyesPerRow * (SWATCH_SIZE + SWATCH_GAP) - SWATCH_GAP)) / 2
        for (i in dyeEntries.indices) {
            val row = i / dyesPerRow
            val col = i % dyesPerRow
            val sx = paletteStartX + col * (SWATCH_SIZE + SWATCH_GAP)
            val sy = dyesY + 12 + row * (SWATCH_SIZE + SWATCH_GAP)
            dyeSwatches.add(DyeSwatch(dyeEntries[i], sx, sy, SWATCH_SIZE, SWATCH_SIZE))
        }

        val buttonsY = dyesY + 12 + 2 * (SWATCH_SIZE + SWATCH_GAP) + 8
        val btnWidth = (DIALOG_WIDTH - 6) / 2
        doneButton = Button.builder(Component.literal("Done")) {
            val resultColor = if (hasColor) ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF) else DisplaySpawnPayload.NO_COLOR
            onSelect(resultColor)
            minecraft.gui.setScreen(parent)
        }.bounds(startX, buttonsY, btnWidth, 20).build()
        this.addRenderableWidget(doneButton)

        cancelButton = Button.builder(Component.literal("Cancel")) {
            minecraft.gui.setScreen(parent)
        }.bounds(startX + btnWidth + 6, buttonsY, btnWidth, 20).build()
        this.addRenderableWidget(cancelButton)

        if (hasColor) {
            syncHexFromRgb()
        }
    }

    private fun syncHexFromRgb() {
        updatingFromCode = true
        val colorInt = ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        hexInput.value = String.format("#%06X", colorInt)
        updatingFromCode = false
    }

    private fun updateRgbFromHsv() {
        val rgb = hsvToRgb(hue, saturation, value)
        r = (rgb shr 16) and 0xFF
        g = (rgb shr 8) and 0xFF
        b = rgb and 0xFF
        syncHexFromRgb()
    }

    private fun selectDyeColor(dyeColor: DyeColor) {
        val rgb = dyeColor.textureDiffuseColor and 0xFFFFFF
        r = (rgb shr 16) and 0xFF
        g = (rgb shr 8) and 0xFF
        b = rgb and 0xFF
        val hsv = rgbToHsv(r, g, b)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        hasColor = true
        syncHexFromRgb()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)

        val startX = (this.width - DIALOG_WIDTH) / 2
        val mainBoxY = 24
        val gradientX = startX + PREVIEW_WIDTH
        val gradientW = DIALOG_WIDTH - PREVIEW_WIDTH

        graphics.text(this.font, this.title, this.width / 2, 10, 0xFFFFFF)

        // preview
        graphics.fill(startX - 1, mainBoxY - 1, gradientX, mainBoxY + GRADIENT_HEIGHT + 1, 0xFF000000.toInt())
        val currentColorInt = ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        if (hasColor) {
            graphics.fill(startX, mainBoxY, gradientX, mainBoxY + GRADIENT_HEIGHT, 0xFF000000.toInt() or currentColorInt)
        } else {
            graphics.fill(startX, mainBoxY, gradientX, mainBoxY + GRADIENT_HEIGHT, 0xFF222222.toInt())
            graphics.text(this.font, "No Dye", startX + PREVIEW_WIDTH / 2, mainBoxY + GRADIENT_HEIGHT / 2 - 4, 0x888888)
        }

        // box color picker thing
        graphics.fill(gradientX, mainBoxY - 1, startX + DIALOG_WIDTH + 1, mainBoxY + GRADIENT_HEIGHT + 1, 0xFF000000.toInt())
        for (col in 0 until gradientW) {
            val s = col.toFloat() / (gradientW - 1).coerceAtLeast(1)
            val topRgb = hsvToRgb(hue, s, 1.0f)
            val colX = gradientX + col
            graphics.fillGradient(
                colX,
                mainBoxY,
                colX + 1,
                mainBoxY + GRADIENT_HEIGHT,
                0xFF000000.toInt() or topRgb,
                0xFF000000.toInt()
            )
        }

        val cursorX = (gradientX + saturation * (gradientW - 1)).toInt().coerceIn(gradientX, startX + DIALOG_WIDTH - 1)
        val cursorY = (mainBoxY + (1f - value) * (GRADIENT_HEIGHT - 1)).toInt().coerceIn(mainBoxY, mainBoxY + GRADIENT_HEIGHT - 1)

        graphics.fill(cursorX - 4, cursorY - 4, cursorX + 5, cursorY + 5, 0xFF000000.toInt())
        graphics.fill(cursorX - 3, cursorY - 3, cursorX + 4, cursorY + 4, 0xFFFFFFFF.toInt())
        graphics.fill(cursorX - 2, cursorY - 2, cursorX + 3, cursorY + 3, 0xFF000000.toInt() or currentColorInt)

        // slider
        val hueY = mainBoxY + GRADIENT_HEIGHT + 8
        graphics.fill(startX - 1, hueY - 1, startX + DIALOG_WIDTH + 1, hueY + HUE_SLIDER_HEIGHT + 1, 0xFF000000.toInt())
        for (col in 0 until DIALOG_WIDTH) {
            val h = (col.toFloat() / (DIALOG_WIDTH - 1).coerceAtLeast(1)) * 360f
            val rgb = hsvToRgb(h, 1.0f, 1.0f)
            val colX = startX + col
            graphics.fill(colX, hueY, colX + 1, hueY + HUE_SLIDER_HEIGHT, 0xFF000000.toInt() or rgb)
        }

        val hueHandleX = (startX + (hue / 360f) * (DIALOG_WIDTH - 1)).toInt().coerceIn(startX, startX + DIALOG_WIDTH - 1)
        val currentHueRgb = hsvToRgb(hue, 1.0f, 1.0f)
        graphics.fill(hueHandleX - 4, hueY - 2, hueHandleX + 5, hueY + HUE_SLIDER_HEIGHT + 2, 0xFF000000.toInt())
        graphics.fill(hueHandleX - 3, hueY - 1, hueHandleX + 4, hueY + HUE_SLIDER_HEIGHT + 1, 0xFFFFFFFF.toInt())
        graphics.fill(hueHandleX - 2, hueY, hueHandleX + 3, hueY + HUE_SLIDER_HEIGHT, 0xFF000000.toInt() or currentHueRgb)

        if (mouseX in startX..gradientX && mouseY in mainBoxY..(mainBoxY + GRADIENT_HEIGHT)) {
            val desc = if (hasColor) "Selected: " + String.format("#%06X", currentColorInt) else "No Dye"
            renderTextTooltip(graphics, desc, mouseX, mouseY)
        }

        val hexY = hueY + HUE_SLIDER_HEIGHT + 8
        val dyesY = hexY + 26
        graphics.text(this.font, "Minecraft dyes", startX, dyesY, 0xAAAAAA)

        var hoveredSwatch: DyeSwatch? = null
        for (swatch in dyeSwatches) {
            val color = swatch.dyeColor.textureDiffuseColor or 0xFF000000.toInt()
            val isHovered = mouseX in swatch.x..(swatch.x + swatch.width) && mouseY in swatch.y..(swatch.y + swatch.height)
            if (isHovered) hoveredSwatch = swatch

            val borderColor = if (isHovered) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            graphics.fill(swatch.x - 1, swatch.y - 1, swatch.x + swatch.width + 1, swatch.y + swatch.height + 1, borderColor)
            graphics.fill(swatch.x, swatch.y, swatch.x + swatch.width, swatch.y + swatch.height, color)
        }

        if (hoveredSwatch != null) {
            val dyeName = hoveredSwatch.dyeColor.name.lowercase().split('_')
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } } + " Dye"
            renderTextTooltip(graphics, dyeName, mouseX, mouseY)
        }
    }

    private fun renderTextTooltip(graphics: GuiGraphicsExtractor, text: String, mouseX: Int, mouseY: Int) {
        val clientTooltip = listOf(ClientTooltipComponent.create(Component.literal(text).visualOrderText))
        graphics.tooltip(font, clientTooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null, false)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (super.mouseClicked(mouseButtonEvent, bl)) return true

        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val mouseX = mouseButtonEvent.x.toInt()
            val mouseY = mouseButtonEvent.y.toInt()

            val startX = (this.width - DIALOG_WIDTH) / 2
            val mainBoxY = 24
            val gradientX = startX + PREVIEW_WIDTH
            val gradientW = DIALOG_WIDTH - PREVIEW_WIDTH
            val hueY = mainBoxY + GRADIENT_HEIGHT + 8

            if (mouseX in gradientX..(gradientX + gradientW) && mouseY in mainBoxY..(mainBoxY + GRADIENT_HEIGHT)) {
                draggingSV = true
                saturation = ((mouseX - gradientX).toFloat() / (gradientW - 1)).coerceIn(0f, 1f)
                value = (1f - (mouseY - mainBoxY).toFloat() / (GRADIENT_HEIGHT - 1)).coerceIn(0f, 1f)
                hasColor = true
                updateRgbFromHsv()
                return true
            }

            if (mouseX in startX..(startX + DIALOG_WIDTH) && mouseY in (hueY - 3)..(hueY + HUE_SLIDER_HEIGHT + 3)) {
                draggingHue = true
                hue = (((mouseX - startX).toFloat() / (DIALOG_WIDTH - 1)) * 360f).coerceIn(0f, 360f)
                hasColor = true
                updateRgbFromHsv()
                return true
            }

            for (swatch in dyeSwatches) {
                if (mouseX in swatch.x..(swatch.x + swatch.width) && mouseY in swatch.y..(swatch.y + swatch.height)) {
                    selectDyeColor(swatch.dyeColor)
                    return true
                }
            }
        }
        return false
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragY: Double, e: Double): Boolean {
        if (draggingSV) {
            val startX = (this.width - DIALOG_WIDTH) / 2
            val mainBoxY = 24
            val gradientX = startX + PREVIEW_WIDTH
            val gradientW = DIALOG_WIDTH - PREVIEW_WIDTH

            saturation = ((mouseButtonEvent.x - gradientX).toFloat() / (gradientW - 1)).coerceIn(0f, 1f)
            value = (1f - (mouseButtonEvent.y - mainBoxY).toFloat() / (GRADIENT_HEIGHT - 1)).coerceIn(0f, 1f)
            hasColor = true
            updateRgbFromHsv()
            return true
        }

        if (draggingHue) {
            val startX = (this.width - DIALOG_WIDTH) / 2
            hue = (((mouseButtonEvent.x - startX).toFloat() / (DIALOG_WIDTH - 1)) * 360f).coerceIn(0f, 360f)
            hasColor = true
            updateRgbFromHsv()
            return true
        }

        return super.mouseDragged(mouseButtonEvent, dragY, e)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingSV = false
            draggingHue = false
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (hexInput.isFocused && keyEvent.key() != InputConstants.KEY_ESCAPE) {
            return hexInput.keyPressed(keyEvent)
        }
        return super.keyPressed(keyEvent)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
