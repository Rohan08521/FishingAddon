package com.riftaddons.util

import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import kotlin.math.absoluteValue

object KeyBindingUtils {
  private val mc = Minecraft.getInstance()

  fun setKeyBindState(key: KeyMapping, pressed: Boolean) {
    key.setDown(pressed)
  }

  fun releaseAllExcept(vararg except: KeyMapping) {
    val keys = arrayOf(
      mc.options.keyUp,
      mc.options.keyDown,
      mc.options.keyLeft,
      mc.options.keyRight,
      mc.options.keyJump,
      mc.options.keySprint


    )

    for (key in keys) {
      if (key !in except) {
        key.setDown(false)
      }
    }
  }

  @JvmStatic
  fun calculateMovement(yawDiff: Float, shouldSprint: Boolean = true): MovementState {
    val normalizedYaw = normalizeYaw(yawDiff)

    val forward: Boolean
    val back: Boolean
    val left: Boolean
    val right: Boolean

    when {
      normalizedYaw.absoluteValue <= 10 -> {
        forward = true
        back = false
        left = false
        right = false
      }

      normalizedYaw.absoluteValue <= 55 -> {
        forward = true
        back = false
        left = normalizedYaw < 0
        right = normalizedYaw > 0
      }

      normalizedYaw.absoluteValue <= 125 -> {
        forward = false
        back = false
        left = normalizedYaw < 0
        right = normalizedYaw > 0
      }

      else -> {
        forward = false
        back = true
        left = normalizedYaw > 0
        right = normalizedYaw < 0
      }
    }

    return MovementState(forward, back, left, right, shouldSprint)
  }

  private fun normalizeYaw(yaw: Float): Float {
    var normalized = yaw % 360
    if (normalized > 180) normalized -= 360
    if (normalized < -180) normalized += 360
    return normalized
  }

  @JvmStatic
  fun applyMovement(state: MovementState) {
    setKeyBindState(mc.options.keyUp, state.forward)
    setKeyBindState(mc.options.keyDown, state.back)
    setKeyBindState(mc.options.keyLeft, state.left)
    setKeyBindState(mc.options.keyRight, state.right)
    setKeyBindState(mc.options.keySprint, state.sprint)
  }

  @JvmStatic
  fun jump() {
    setKeyBindState(mc.options.keyJump, true)
  }

  @JvmStatic
  fun releaseAll() {
    releaseAllExcept()
  }

  data class MovementState(
    val forward: Boolean,
    val back: Boolean,
    val left: Boolean,
    val right: Boolean,
    val sprint: Boolean
  ) {
    fun isForward() = forward
    fun isBack() = back
    fun isLeft() = left
    fun isRight() = right
    fun isSprint() = sprint
  }
}
