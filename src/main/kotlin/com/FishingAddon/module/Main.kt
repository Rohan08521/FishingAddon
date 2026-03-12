package com.FishingAddon.module

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.KeyBindSetting
import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.helper.KeyBind
import org.lwjgl.glfw.GLFW
import org.cobalt.api.module.setting.impl.ModeSetting
import org.cobalt.api.util.InventoryUtils
import org.cobalt.api.event.impl.render.NvgEvent
import org.cobalt.api.module.setting.impl.CheckboxSetting

object Main : Module(
    name = "Main tab",
) {
    var keyBind by KeyBindSetting(
        name = "Macro Keybind",
        description = "Keybind to toggle the macro",
        defaultValue = KeyBind(GLFW.GLFW_KEY_J)
    )
    var ungrabMouseToggle by CheckboxSetting(
        name = "Ungrab Mouse",
        description = "toggle mouse grab",
        defaultValue = true
    )
    val mode by ModeSetting(
        name = "FishingMode",
        description = "Fishing mode setting",
        defaultValue = 0,
        options = arrayOf("Normal", "SurfStriders", "Worm fishing(WIP)", "Hotspot fishing(WIP)", "Piscary fishing(WIP)")
    )

    private var isToggled = false
    private var wasKeyPressed = false
    private var isPausedByScreen = false
    private val mc = Minecraft.getInstance()

    fun start() {
        isToggled = true

        Visuals.captureStartBlock()
        if (ungrabMouseToggle == true){
            MouseUtils.ungrabMouse()
        }
        when (mode) {
            0 -> Normal.start()
            1 -> SurfStriders.start()
            2 -> WormFishing.start()
        }
    }

    fun stop() {
        isToggled = false
        Visuals.clearStartBlock()
        if (ungrabMouseToggle == true){
            MouseUtils.grabMouse()
        }

        when (mode) {
            0 -> Normal.resetStates()
            1 -> SurfStriders.resetStates()
            2 -> WormFishing.resetStates()
        }
    }

    @SubscribeEvent
    fun keybindListener(event: TickEvent) {

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
