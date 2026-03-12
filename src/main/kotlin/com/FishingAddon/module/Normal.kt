package com.FishingAddon.module

import com.FishingAddon.module.Main.detectFishbite
import com.FishingAddon.module.Main.swapToFishingRod
import com.FishingAddon.util.EntityUtils
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.RangeSetting
import org.cobalt.api.module.setting.impl.SliderSetting
import org.cobalt.api.module.setting.impl.ModeSetting
import org.cobalt.api.util.MouseUtils
import com.FishingAddon.util.helper.Clock
import java.awt.Color
import kotlin.random.Random
import net.minecraft.client.Minecraft
import org.cobalt.api.rotation.EasingType
import org.cobalt.api.rotation.RotationExecutor
import org.cobalt.api.rotation.RotationExecutor.rotateTo
import org.cobalt.api.rotation.strategy.TimedEaseStrategy
import org.cobalt.api.util.helper.Rotation
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.render.Render3D

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

//    private val killingMode by ModeSetting(
//        name = "Killing Mode",
//        description = "Method to kill sea creatures (if applicable)",
//        defaultValue = 0,
//        options = arrayOf("None", "Melee", "wither impact/yeti sword", "Frozen scythe/spiritsceptre")
//    )
//    private val weaponSlot by SliderSetting(
//        name = "Weapon Slot",
//        description = "Inventory slot of the weapon used for killing",
//        defaultValue = 1.0,
//        min = 1.0,
//        max = 8.0
//    )

    private var originalYaw = 0f
    private var originalPitch = 0f
    private var macroState = MacroState.IDLE
    private val clock = Clock()
    private var waitingStartTime = 0L
    private val mc = Minecraft.getInstance()
    private var isBobbing = false

    private val existingEntityIds = mutableSetOf<Int>()
    private var detectedSeaCreatureEntity: net.minecraft.world.entity.Entity? = null
    private var detectedSeaCreatureName: String? = null
    private var detectionComplete = false
    private var detectionAttempts = 0
    private val maxDetectionAttempts = 20

    private enum class MacroState {
        IDLE,
        SWAP_TO_ROD,
        CASTING,
        WAITING,
        REELING,
        POST_REEL_DECIDE,
        DETECTING_CREATURE,
        RESETTING,
        MOBKILL_MELEE,
        WITHERIMPACT_YETI,
        FROZEN_SPIRIT,
        RESET
    }

    internal fun start() {
        isBobbing = mc.player?.fishing?.let { it.isInWater || it.isInLava } ?: false
        if (!isBobbing) {
            macroState = MacroState.SWAP_TO_ROD
        } else macroState = MacroState.WAITING
    }

    internal fun resetStates() {
        macroState = MacroState.IDLE
    }

    private fun rotateTo(yaw: Float, pitch: Float, duration: Long = 300L) {
        RotationExecutor.rotateTo(
            Rotation(yaw, pitch),
            TimedEaseStrategy(EasingType.LINEAR, EasingType.LINEAR, duration)
        )
    }

    internal fun onTick() {
        if (!clock.passed()) return

        if (mc.player == null || mc.level == null || mc.gameMode == null) return
        isBobbing = mc.player?.fishing?.let { it.isInWater || it.isInLava } ?: false

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
                    val isBobbing = bobber?.let { it.isInWater || it.isInLava } ?: false
                 if (bobber == null && System.currentTimeMillis() - waitingStartTime > bobberTimeout.toLong()) {
                       clock.schedule(Random.nextInt(100, 200))
                       macroState = MacroState.CASTING
                       return
                 }

                    if (!isBobbing && bobber != null && System.currentTimeMillis() - waitingStartTime > bobberTimeout.toLong()) {
                        macroState = MacroState.REELING
                        clock.schedule(Random.nextInt(100, 200))
                    }
                }
            }

            MacroState.REELING -> {
                MouseUtils.rightClick()

                existingEntityIds.clear()
                mc.player?.let { player ->
                    mc.level?.entitiesForRendering()?.forEach { entity ->
                        if (entity.distanceTo(player) <= 20) {
                            existingEntityIds.add(entity.id)
                        }
                    }
                }

                clock.schedule(Random.nextInt(200, 400))
                macroState = MacroState.POST_REEL_DECIDE
            }

            MacroState.RESETTING -> {

                clock.schedule(Random.nextInt(castDelay.first.toInt(), castDelay.second.toInt()))
                macroState = MacroState.CASTING
            }

            MacroState.IDLE -> {
            }

            MacroState.POST_REEL_DECIDE -> {
                mc.player?.let {
                    originalYaw = it.yRot
                    originalPitch = it.xRot
                }
//        if (!shouldKillSeaCreatures()){
//          clock.schedule(Random.nextInt(100, 200))
//          macroState = MacroState.RESETTING
//        }

                detectionAttempts = 0
                clock.schedule(50)
                macroState = MacroState.RESETTING
            }

            MacroState.DETECTING_CREATURE -> {
//        detectionAttempts++
//
//        val newEntityName = detectSeaCreature()
//        if (newEntityName != null) {
//            detectedSeaCreatureName = newEntityName
//            val player = mc.player
//            if (player != null) {
//                detectedSeaCreatureEntity = mc.level?.entitiesForRendering()?.filter { entity ->
//                    entity.distanceTo(player) <= 20 &&
//                    !existingEntityIds.contains(entity.id) &&
//                    entity.hasCustomName() &&
//                    entity.name.string.contains("❤")
//                }?.minByOrNull { it.distanceTo(player) }
//
//                if (detectedSeaCreatureEntity != null) {
//                  detectionComplete = true
//                  findSeacreature(newEntityName)
//
//                  if (shouldKillSeaCreatures() && killingMode == 1) {
//                    macroState = MacroState.MOBKILL_MELEE
//                  } else if (shouldKillSeaCreatures() && killingMode == 2) {
//                    macroState = MacroState.WITHERIMPACT_YETI
//                  } else if (shouldKillSeaCreatures() && killingMode == 3) {
//                    macroState = MacroState.FROZEN_SPIRIT
//                  } else {
//                    // No killing mode enabled, reset
//                    detectedSeaCreatureEntity = null
//                    detectedSeaCreatureName = null
//                    detectionComplete = false
//                    macroState = MacroState.RESETTING
//                  }
//                  return
//                }
//            }
//        }
//
//
//        if (detectionAttempts >= maxDetectionAttempts) {
//            detectedSeaCreatureEntity = null
//            detectedSeaCreatureName = null
//            detectionComplete = false
//            macroState = MacroState.RESETTING
//        } else {
//            clock.schedule(20)
//        }
//        if(!detectionComplete){
//          ChatUtils.sendDebug("$newEntityName")
//        }
//
            }

            MacroState.MOBKILL_MELEE -> {
                // TODO: Implement melee killing
            }

            MacroState.WITHERIMPACT_YETI -> {
                // TODO: Implement wither impact/yeti sword killing
            }

            MacroState.FROZEN_SPIRIT -> {
                // TODO: Implement frozen scythe/spirit sceptre killing
            }

            MacroState.RESET -> {
                rotateTo(originalYaw, originalPitch, duration = 300L)
                clock.schedule(Random.nextInt(100, 200))
                macroState = MacroState.CASTING
            }
        }
    }


    fun detectSeaCreature(): String? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null

        val newEntities = level.entitiesForRendering().filter { entity ->
            entity.distanceTo(player) <= 20 &&
                entity.hasCustomName() &&
                !existingEntityIds.contains(entity.id) &&
                entity.name.string.contains("❤")  // Filter for heart symbol only
        }

        val sortedEntities = newEntities.sortedBy { it.distanceTo(player) }
        if (sortedEntities.isNotEmpty()) {
            return sortedEntities.first().name.string
        }

        return null
    }


//  fun seacreatureSpawned(): Boolean {
//    return detectSeaCreature() != null
//  }
//
//
//  fun shouldKillSeaCreatures(): Boolean {
//    if (killingMode != 0 && seacreatureSpawned()) {
//      return true
//    }
//    return false
//  }
//
//
//  fun findSeacreature(name: String? = null) {
//    if (shouldKillSeaCreatures()) {
//       val targetName = name ?: ""
//       if (targetName.isNotEmpty()) {
//           EntityUtils.findMob(targetName)
//       }
//       return
//    }
//  }
//
//
//  @SubscribeEvent
//  fun renderSeaCreature(event: WorldRenderEvent.Start) {
//    if (shouldKillSeaCreatures()) return
//
//    val player = mc.player ?: return
//    val entity = EntityUtils.findMob(detectedSeaCreatureName!!, nearTo = player)
//    ChatUtils.sendDebug("Rendering $entity")
//    if (entity != null && entity.isAlive) {
//       val entityBox = entity.boundingBox
//       Render3D.drawBox(event.context, entityBox, Color(128, 0, 255), esp = true)
//    }
//  }
}
