package com.FishingAddon.module

import java.awt.Color
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.KeyBindSetting
import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.helper.KeyBind
import org.lwjgl.glfw.GLFW
import org.cobalt.api.module.setting.impl.ModeSetting
import org.cobalt.api.util.InventoryUtils
import org.cobalt.api.util.render.Render3D

object Main : Module(
  name = "Main tab",
) {
  var keyBind by KeyBindSetting(
    name = "Macro Keybind",
    description = "Keybind to toggle the macro",
    defaultValue = KeyBind(GLFW.GLFW_KEY_J)
  )
  val mode by ModeSetting(
    name = "FishingMode",
    description = "Fishing mode setting",
    defaultValue = 0,
    options = arrayOf("Normal", "SurfStriders", "Worm fishing(WIP)", "Hotspot fishing(WIP)", "Piscary fishing(WIP)")
  )

  private var isToggled = false
  private var wasKeyPressed = false
  private val mc = Minecraft.getInstance()
  // thanks claude for rendering the box for me <3
  private var savedBlockX = 0
  private var savedBlockY = 0
  private var savedBlockZ = 0

  fun start() {
    isToggled = true
    MouseUtils.ungrabMouse()


    val player = mc.player
    if (player != null) {
      val playerPos = player.position()
      savedBlockX = kotlin.math.floor(playerPos.x).toInt()
      savedBlockY = kotlin.math.floor(playerPos.y - 1).toInt()
      savedBlockZ = kotlin.math.floor(playerPos.z).toInt()
    }

    when (mode) {
      0 -> Normal.start()
      1 -> SurfStriders.start()
    }
  }

  fun stop() {
    isToggled = false
    MouseUtils.grabMouse()

    when (mode) {
      0 -> Normal.resetStates()
      1 -> SurfStriders.resetStates()
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

    when (mode) {
      0 -> Normal.onTick()
      1 -> SurfStriders.onTick()
    }
  }

  @SubscribeEvent
  fun onWorldRender(event: WorldRenderEvent.Start) {
    if (!isToggled) return

    // thanks claude for rendering the box for me <3
    val blockBox = AABB(
      savedBlockX.toDouble(), savedBlockY.toDouble(), savedBlockZ.toDouble(),
      (savedBlockX + 1).toDouble(), (savedBlockY + 1).toDouble(), (savedBlockZ + 1).toDouble()
    )

    Render3D.drawBox(event.context, blockBox, Color(0, 150, 255), esp = true)
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
