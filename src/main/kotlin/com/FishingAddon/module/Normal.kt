package com.FishingAddon.module

import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.InventoryUtils
import com.FishingAddon.util.helper.Clock
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand
import kotlin.random.Random
import net.minecraft.ChatFormatting

object Normal {
  private var macroState = MacroState.IDLE
  private val clock = Clock()
  private val mc = Minecraft.getInstance()

  enum class MacroState {
    IDLE,
    SWAP_TO_ROD,
    CASTING,
    WAITING,
    REELING,
    RESETTING,
  }

  fun detectFishbite(): Boolean {
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

  fun swapToFishingRod() {
    val slot = InventoryUtils.findItemInHotbar("rod")

    if (slot == -1) {
      ChatUtils.sendMessage("${ChatFormatting.RED}No Fishing Rod found in hotbar! Disabling macro.${ChatFormatting.RESET}")
      Main.stop()
      return
    }

    InventoryUtils.holdHotbarSlot(slot)
  }

  fun start() {
    macroState = MacroState.SWAP_TO_ROD
  }

  fun resetStates() {
    macroState = MacroState.IDLE
  }

  fun onTick() {
    if (!clock.passed()) return
    when (macroState) {
      MacroState.SWAP_TO_ROD -> {
        swapToFishingRod()
        clock.schedule(Random.nextInt(200, 500))
        macroState = MacroState.CASTING
      }

      MacroState.CASTING -> {
        MouseUtils.rightClick()
        clock.schedule(Random.nextInt(100, 200))
        macroState = MacroState.WAITING
      }

      MacroState.WAITING -> {
        if (detectFishbite()) {
          clock.schedule(Random.nextInt(50, 150))
          macroState = MacroState.REELING
        }
      }

      MacroState.REELING -> {
        MouseUtils.rightClick()
        clock.schedule(Random.nextInt(200, 400))
        macroState = MacroState.RESETTING
      }

      MacroState.RESETTING -> {
        clock.schedule(Random.nextInt(200, 500))
        macroState = MacroState.CASTING
      }

      MacroState.IDLE -> {
      }
    }
  }
}
