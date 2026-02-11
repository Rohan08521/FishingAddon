package com.FishingAddon.module

import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.KeyBindSetting
import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.helper.KeyBind
import org.lwjgl.glfw.GLFW
import org.cobalt.api.module.setting.impl.ModeSetting

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
    options = arrayOf("Normal", "SurfStriders(WIP)", "Worm fishing(WIP)", "Hotspot fishing(WIP)", "Piscary fishing(WIP)")
  )

  private var isToggled = false
  private var wasKeyPressed = false

  fun start() {
    isToggled = true
    MouseUtils.ungrabMouse()

    when (mode) {
      0 -> Normal.start()
    }
  }

  fun stop() {
    isToggled = false
    MouseUtils.grabMouse()

    when (mode) {
      0 -> Normal.resetStates()
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
    }
  }
}

