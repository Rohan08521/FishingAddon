package com.FishingAddon.module

import com.FishingAddon.module.Main.detectFishbite
import com.FishingAddon.module.Main.swapToFishingRod
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.ModeSetting
import org.cobalt.api.module.setting.impl.RangeSetting
import org.cobalt.api.module.setting.impl.SliderSetting
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.InventoryUtils
import org.cobalt.api.util.ReflectionUtils
import org.cobalt.api.rotation.EasingType
import org.cobalt.api.rotation.RotationExecutor
import org.cobalt.api.rotation.strategy.TimedEaseStrategy
import org.cobalt.api.util.helper.Rotation
import org.cobalt.api.event.impl.render.WorldRenderEvent
import com.FishingAddon.util.helper.Clock
import kotlin.random.Random
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.monster.Strider
import net.minecraft.util.Mth
import kotlin.math.atan2
import kotlin.math.sqrt

object SurfStriders : Module("SurfStriders Settings") {
  private val castDelay by RangeSetting(
    name = "Cast Delay",
    description = "Delay range before casting (in ms)",
    defaultValue = Pair(100.0, 200.0),
    min = 0.0,
    max = 1000.0
  )

  private val reelInDelay by RangeSetting(
    name = "Reel In Delay",
    description = "Delay range after reeling in (in ms)",
    defaultValue = Pair(200.0, 400.0),
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

  private val killStriderAt by SliderSetting(
    name = "read description im lazy",
    description = "The number of striders at which it will start killing",
    defaultValue = 5.0,
    min = 1.0,
    max = 30.0
  )
  private val killingMode by ModeSetting(
    name = "Killing Mode",
    description = "Method to kill Striders",
    defaultValue = 0,
    options = arrayOf("Foraging/Hunting axe", "Flaming flay/Soul whip swap with axe")
  )

  private var macroState = MacroState.IDLE
  private val clock = Clock()
  private val mc = Minecraft.getInstance()
  private var originalYaw = 0f
  private var originalPitch = 0f
  private var targetStrider: Strider? = null
  private var waitingStartTime = 0L

  private enum class MacroState {
    IDLE,
    SWAP_TO_ROD,

    // fishing (im not fucking ai)
    CASTING,
    WAITING,
    REELING,
    POST_REEL_DECIDE,

    // killing with flay/whip , done
    ROTATE_FLAY,
    SOUL_SWAP,
    SOUL_THROW,
    AXE_SWAP,

    // killing with foraging axe melee , done hopefully
    ROTATE_TO_SURFSTRIDER_MELEE,
    MELEE_ATTACK,

    // resetting
    RESET,
    RESETTING,
  }

  internal fun start() {
    macroState = MacroState.SWAP_TO_ROD
  }

  internal fun resetStates() {
    macroState = MacroState.IDLE
    targetStrider = null
  }
  private fun swapToAxe(){
    val slot = InventoryUtils.findItemInHotbar("figstone")
    if (slot != -1) {
      InventoryUtils.holdHotbarSlot(slot)
      clock.schedule(Random.nextInt(100, 200))
    } else {
      macroState = MacroState.RESET
    }
  }
  private fun countStriders(): Int {
    val player = mc.player ?: return 0
    val world = mc.level ?: return 0
    return world.entitiesForRendering()
      .filterIsInstance<Strider>()
      .count { it.isAlive() && it.distanceTo(player) <= 10.0 }
  }

  private fun findNearestStrider(): Strider? {
    val player = mc.player ?: return null
    val world = mc.level ?: return null
    return world.entitiesForRendering()
      .filterIsInstance<Strider>()
      .filter { it.distanceTo(player) <= 10.0 }
      .minByOrNull { it.distanceTo(player) }
  }


  private fun rotateTo(yaw: Float, pitch: Float, duration: Long = 150L) {
    RotationExecutor.rotateTo(
      Rotation(yaw, pitch),
      TimedEaseStrategy(EasingType.LINEAR, EasingType.LINEAR, duration)
    )
  }

  private fun rotateTo(target: Strider) {
    val player = mc.player ?: return

    val dx = target.x - player.x
    val dy = (target.y + target.bbHeight) - (player.y + player.getEyeHeight(player.pose))
    val dz = target.z - player.z

    val dist = sqrt(dx * dx + dz * dz)

    val yaw = Mth.wrapDegrees(
      Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
    )
    val pitch = Mth.wrapDegrees(
      (-Math.toDegrees(atan2(dy, dist))).toFloat()
    )

    rotateTo(yaw, pitch)
  }

  internal fun onTick() {
    if (!clock.passed()) return

    //check: ensure player, level, and gameMode exist
    if (mc.player == null || mc.level == null || mc.gameMode == null) return

    when (macroState) {
      MacroState.SWAP_TO_ROD -> {
        swapToFishingRod()
        clock.schedule(Random.nextInt(200, 500))
        macroState = MacroState.CASTING
      }

      MacroState.CASTING -> {
        MouseUtils.rightClick()
        waitingStartTime = System.currentTimeMillis()
        clock.schedule(Random.nextInt(castDelay.first.toInt(), castDelay.second.toInt()))
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
        macroState = MacroState.POST_REEL_DECIDE
        clock.schedule(Random.nextInt(100, 200))
      }

      MacroState.POST_REEL_DECIDE -> {
        if (countStriders() >= killStriderAt.toInt()) {
          // save current rotation before killing
          mc.player?.let {
            originalYaw = it.yRot
            originalPitch = it.xRot
          }

          if (killingMode == 1) {
            // Flaming flay/Soul whip mode
            targetStrider = findNearestStrider()
            macroState = MacroState.ROTATE_FLAY
            clock.schedule(Random.nextInt(100, 200))
          } else if (killingMode == 0) {
            // Foraging/Hunting axe melee mode
            targetStrider = findNearestStrider()
            macroState = MacroState.ROTATE_TO_SURFSTRIDER_MELEE
            clock.schedule(Random.nextInt(100, 200))
          }
        } else {
          // Not enough striders, continue fishing
          macroState = MacroState.CASTING
          clock.schedule(Random.nextInt(100, 200))
        }
      }

      MacroState.ROTATE_FLAY -> {
        targetStrider?.let { strider ->
          rotateTo(strider)
          clock.schedule(Random.nextInt(100, 200))
          macroState = MacroState.SOUL_SWAP
        } ?: run {
          macroState = MacroState.CASTING
        }
      }

      MacroState.SOUL_SWAP -> {
        val soulWhipSlot = InventoryUtils.findItemInHotbar("soul whip")
        val flamingFlaySlot = InventoryUtils.findItemInHotbar("flaming flay")

        val slot = if (soulWhipSlot != -1) soulWhipSlot else flamingFlaySlot
        if (slot != -1) {
          InventoryUtils.holdHotbarSlot(slot)
          clock.schedule(Random.nextInt(100, 200))
          macroState = MacroState.SOUL_THROW
        } else {
          macroState = MacroState.AXE_SWAP
        }
      }

      MacroState.SOUL_THROW -> {
        MouseUtils.rightClick()
        clock.schedule(Random.nextInt(100, 200))
        macroState = MacroState.AXE_SWAP
      }

      MacroState.AXE_SWAP -> {
        val slot = InventoryUtils.findItemInHotbar("figstone")
        if (slot != -1) {
          InventoryUtils.holdHotbarSlot(slot)
          clock.schedule(Random.nextInt(100, 200))
          macroState = MacroState.RESET
        } else {
          macroState = MacroState.RESET
        }
      }

      MacroState.ROTATE_TO_SURFSTRIDER_MELEE -> {
        if (targetStrider == null) {
          targetStrider = findNearestStrider()
        }

        targetStrider?.let { strider ->
          swapToAxe()
          rotateTo(strider)
          clock.schedule(Random.nextInt(200, 300))
          macroState = MacroState.MELEE_ATTACK
        } ?: run {
          // No strider found, go back to fishing
          macroState = MacroState.RESET
          clock.schedule(Random.nextInt(100, 200))
        }
      }

      MacroState.MELEE_ATTACK -> {
        if (targetStrider?.isAlive == true) {
          MouseUtils.leftClick()
          clock.schedule(Random.nextInt(100, 200))
          // Stay in MELEE_ATTACK state to keep attacking
        } else {
          // Strider is dead, reset and go back to fishing
          targetStrider = null
          macroState = MacroState.RESET
          clock.schedule(Random.nextInt(100, 200))
        }
      }

      MacroState.RESET -> {
        rotateTo(originalYaw, originalPitch)
        clock.schedule(Random.nextInt(100, 200))
        targetStrider = null
        macroState = MacroState.CASTING
      }

      MacroState.RESETTING -> {
        macroState = MacroState.CASTING
      }

      MacroState.IDLE -> {
      }
    }
  }
}
