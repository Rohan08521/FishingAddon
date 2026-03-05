package com.FishingAddon.module

import org.cobalt.api.util.ui.NVGRenderer
import java.awt.Color
import kotlin.math.floor
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.util.ARGB.color
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.CheckboxSetting
import org.cobalt.api.module.setting.impl.KeyBindSetting
import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.helper.KeyBind
import org.lwjgl.glfw.GLFW
import org.cobalt.api.module.setting.impl.ModeSetting
import org.cobalt.api.util.InventoryUtils
import org.cobalt.api.event.impl.render.NvgEvent
import org.cobalt.api.util.render.Render3D
import org.cobalt.api.event.Event

object Main : Module(
    name = "Main tab",
) {
    val mode by ModeSetting(
        name = "FishingMode",
        description = "Fishing mode setting",
        defaultValue = 0,
        options = arrayOf("Normal", "SurfStriders", "Worm fishing", "Hotspot fishing(WIP)", "Piscary fishing(WIP)")
    )

    var keyBind by KeyBindSetting(
        name = "Macro Keybind",
        description = "Keybind to toggle the macro",
        defaultValue = KeyBind(GLFW.GLFW_KEY_Y)
    )
    var ungrabMouseKeyBind by KeyBindSetting(
        name = "Ungrab Mouse Keybind",
        description = "Keybind to toggle mouse grab",
        defaultValue = KeyBind(GLFW.GLFW_KEY_O)
    )
    private val renderMacroEsp by CheckboxSetting(
        name = "Render Macro ESP",
        description = "Renders the saved block ESP while the macro is enabled.",
        defaultValue = true
    )

    private var isToggled = false
    private var isMouseUngrabbed = false
    private var wasKeyPressed = false
    private var wasUngrabMouseKeyPressed = false
    private var isPausedByScreen = false
    private val mc = Minecraft.getInstance()

    // thanks claude for rendering the box for me <3
    private var savedBlockX = 0
    private var savedBlockY = 0
    private var savedBlockZ = 0

    fun start() {
        isToggled = true

        val player = mc.player
        if (player != null) {
            val playerPos = player.position()
            savedBlockX = floor(playerPos.x).toInt()
            savedBlockY = floor(playerPos.y - 1).toInt()
            savedBlockZ = floor(playerPos.z).toInt()
        }

        when (mode) {
            0 -> Normal.start()
            1 -> SurfStriders.start()
            2 -> WormFishing.start()
        }
    }

    fun stop() {
        isToggled = false

        when (mode) {
            0 -> Normal.resetStates()
            1 -> SurfStriders.resetStates()
            2 -> WormFishing.resetStates()
        }
    }

    @SubscribeEvent
    fun keybindListener(event: TickEvent) {
        val isUngrabMousePressed = ungrabMouseKeyBind.isPressed()
        if (isUngrabMousePressed && !wasUngrabMouseKeyPressed) {
            isMouseUngrabbed = !isMouseUngrabbed

            if (isMouseUngrabbed) MouseUtils.ungrabMouse()
            else MouseUtils.grabMouse()

            ChatUtils.sendMessage(
                "Ungrab Mouse is now "
                    + (if (isMouseUngrabbed) "§aEnabled" else "§cDisabled")
                    + "§r"
            )
        }
        wasUngrabMouseKeyPressed = isUngrabMousePressed

        val isPressed = keyBind.isPressed()
        if (isPressed && !wasKeyPressed) {
            isToggled = !isToggled

            if (isToggled) start()
            else stop()

            ChatUtils.sendMessage(
                "Fishing Macro is now "
                    + (if (isToggled) "§aEnabled" else "§cDisabled")
                    + "§r"
            )
        }
        wasKeyPressed = isPressed
    }

    fun isToggled(): Boolean {
        return isToggled
    }

    @SubscribeEvent
    fun onTick(event: TickEvent) {
        if (!isToggled) {
            return
        }

        if (mc.screen != null) {
            if (!isPausedByScreen) {
                isPausedByScreen = true
                ChatUtils.sendMessage("Fishing Macro is now §eIdle§r while a menu is open")
            }
            return
        }

        if (isPausedByScreen) {
            isPausedByScreen = false
            ChatUtils.sendMessage("Fishing Macro has §aResumed§r")
        }

        when (mode) {
            0 -> Normal.onTick()
            1 -> SurfStriders.onTick()
            2 -> WormFishing.onTick()
        }
    }

    @SubscribeEvent
    fun onWorldRender(event: WorldRenderEvent.Start) {
        if (!isToggled || !renderMacroEsp) return

        // thanks claude for rendering the box for me <3
        val blockBox = AABB(
            savedBlockX.toDouble(), savedBlockY.toDouble(), savedBlockZ.toDouble(),
            (savedBlockX + 1).toDouble(), (savedBlockY + 1).toDouble(), (savedBlockZ + 1).toDouble()
        )

        Render3D.drawBox(event.context, blockBox, Color(0, 150, 255, 25), esp = true)
    }

    @SubscribeEvent
    fun onScreenRender(event: NvgEvent) {
        if (!isToggled) return


    }

    internal fun swapToFishingRod() {
        val slot = InventoryUtils.findItemInHotbar("rod")

        if (slot == -1) {
            ChatUtils.sendMessage("${ChatFormatting.RED}No Fishing Rod found in hotbar! Disabling macro.${ChatFormatting.RESET}")
            stop()
            return
        }

        InventoryUtils.holdHotbarSlot(slot)
    }

    internal fun detectFishbite(): Boolean {
        val entities = mc.level?.entitiesForRendering() ?: return false
        var armorStandsChecked = 0
        var fishBiteStands = 0
        for (entity in entities) {
            if (entity is ArmorStand) {
                armorStandsChecked++

                if (entity.hasCustomName()) {
                    val customName = entity.customName
                    if (customName != null) {
                        val nameString = customName.string
                        if (nameString == "!!!") {
                            fishBiteStands++
                        }
                    }
                }
            }
        }
        return fishBiteStands > 0
    }
}
