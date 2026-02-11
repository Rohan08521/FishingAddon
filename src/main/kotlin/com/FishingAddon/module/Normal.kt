package com.FishingAddon.module

import com.FishingAddon.module.Main.detectFishbite
import com.FishingAddon.module.Main.swapToFishingRod
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.RangeSetting
import org.cobalt.api.module.setting.impl.SliderSetting
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.ReflectionUtils
import com.FishingAddon.util.helper.Clock
import kotlin.random.Random
import net.minecraft.client.Minecraft

object Normal : Module(
  name = "Normal Settings"
) {
  private val castDelay by RangeSetting(
    name = "Cast Delay",
    description = "Delay range before recasting (in ms)",
    defaultValue = Pair(200.0, 500.0),
    min = 0.0,
    max = 1000.0
  )

  private val reelInDelay by RangeSetting(
    name = "Reel In Delay",
    description = "Delay range after fish bite detection (in ms)",
    defaultValue = Pair(50.0, 150.0),
    min = 0.0,
    max = 1000.0
  )

  private val bobberTimeout by SliderSetting(
    name = "Bobber Timeout",
    description = "Time to wait for bobber to enter water before recasting (in ms)",
    defaultValue = 20000.0,
    min = 5000.0,
    max = 60000.0
  )

  private var macroState = MacroState.IDLE
  private val clock = Clock()
  private var waitingStartTime = 0L
  private val mc = Minecraft.getInstance()

  private enum class MacroState {
    IDLE,
    SWAP_TO_ROD,
    CASTING,
    WAITING,
    REELING,
    RESETTING,
  }

  internal fun start() {
    macroState = MacroState.SWAP_TO_ROD
  }

  internal fun resetStates() {
    macroState = MacroState.IDLE
  }

  internal fun onTick() {
    if (!clock.passed()) return
    when (macroState) {
      MacroState.SWAP_TO_ROD -> {
        swapToFishingRod()
        clock.schedule(Random.nextInt(200, 500))
        macroState = MacroState.CASTING
      }

      MacroState.CASTING -> {
        MouseUtils.rightClick()
        waitingStartTime = System.currentTimeMillis()
        clock.schedule(Random.nextInt(100, 200))
        macroState = MacroState.WAITING
      }

      MacroState.WAITING -> {
        if (detectFishbite()) {
          clock.schedule(Random.nextInt(reelInDelay.first.toInt(), reelInDelay.second.toInt()))
          macroState = MacroState.REELING
        } else {
          val bobber = mc.player?.fishing
          val isBobbing = bobber?.let {
            val currentState = ReflectionUtils.getField<Any>(it, "currentState") as? Enum<*>
            currentState?.ordinal == 2
          } ?: false

          if (!isBobbing && System.currentTimeMillis() - waitingStartTime > bobberTimeout.toLong()) {
            macroState = MacroState.REELING
            clock.schedule(Random.nextInt(100, 200))
          }
        }
      }

      MacroState.REELING -> {
        MouseUtils.rightClick()
        clock.schedule(Random.nextInt(200, 400))
        macroState = MacroState.RESETTING
      }

      MacroState.RESETTING -> {
        clock.schedule(Random.nextInt(castDelay.first.toInt(), castDelay.second.toInt()))
        macroState = MacroState.CASTING
      }

      MacroState.IDLE -> {
      }
    }
  }
}
