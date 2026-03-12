package com.FishingAddon.module

import com.FishingAddon.module.Main.detectFishbite
import com.FishingAddon.module.Main.swapToFishingRod
import com.FishingAddon.util.helper.Clock
import kotlin.random.Random
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.CheckboxSetting
import org.cobalt.api.module.setting.impl.ModeSetting
import org.cobalt.api.module.setting.impl.RangeSetting
import org.cobalt.api.module.setting.impl.SliderSetting
import org.cobalt.api.rotation.EasingType
import org.cobalt.api.rotation.RotationExecutor
import org.cobalt.api.rotation.strategy.TimedEaseStrategy
import org.cobalt.api.util.InventoryUtils
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.helper.Rotation

object WormFishing : Module("WormFishing Settings") {
    private val castDelay by RangeSetting(
        name = "Cast Delay",
        description = "Delay range before casting (in ms)",
        defaultValue = Pair(100.0, 200.0),
        min = 0.0,
        max = 1000.0
    )
    private val killingMode by ModeSetting(
        name = "Worm Killing Mode",
        description = "Killing mode",
        options = arrayOf("Wither impact", "Fire fury/ fire veil"),
        defaultValue = 0
    )

    private val reelInDelay by RangeSetting(
        name = "Reel In Delay",
        description = "Delay range after reeling in (in ms)",
        defaultValue = Pair(200.0, 400.0),
        min = 0.0,
        max = 1000.0
    )

    private val detectWormfishSpot by CheckboxSetting(
        name = "Detect Wormfish Spot",
        description = "Enable lava spot detection for wormfishing ESP.",
        defaultValue = true
    )

    private val delayInActions by RangeSetting(
        name = "Delay In Actions",
        description = "Delay range used for rod swaps and combat transitions (in ms)",
        defaultValue = Pair(300.0, 1000.0),
        min = 300.0,
        max = 1000.0
    )

    private val bobberTimeout by SliderSetting(
        name = "bobber timeout",
        description = "Time to wait for bobber to enter water before recasting (in ms)",
        defaultValue = 20000.0,
        min = 5000.0,
        max = 60000.0
    )

    private val killSilverfishAt by RangeSetting(
        name = "Sea Creature Cap",
        description = "The range of sea creatures to proceed",
        defaultValue = Pair(18.0, 20.0),
        min = 1.0,
        max = 20.0
    )

    private var macroState = MacroState.IDLE
    private val clock = Clock()
    private val mc = Minecraft.getInstance()
    private var waitingStartTime = 0L
    private var currentKillThreshold = 20
    private var originalYaw = 0f
    private var originalPitch = 0f

    private enum class MacroState {
        IDLE,
        SWAP_TO_ROD,
        CASTING,
        WAITING,
        REELING,
        POST_REEL_DECIDE,
        HYPERION_SWAP,
        HYPERION_ROTATE,
        HYPERION_USE,
        FIRE_SWAP,
        FIRE_USE,
        RESET,
        RESETTING,
    }

    internal fun start() {
        generateNewThreshold()
        macroState = MacroState.SWAP_TO_ROD
    }

    internal fun resetStates() {
        macroState = MacroState.IDLE
    }

    private fun generateNewThreshold() {
        currentKillThreshold = Random.nextInt(
            killSilverfishAt.first.toInt(),
            killSilverfishAt.second.toInt() + 1
        )
    }

    private fun getActionDelay(): Int =
        Random.nextInt(delayInActions.first.toInt(), delayInActions.second.toInt() + 1)

    private fun countSilverfish(): Int {
        val entities = mc.level?.entitiesForRendering() ?: return 0
        return entities.count {
            it is Silverfish && it.position().distanceTo(mc.player?.position() ?: Vec3.ZERO) <= 10.0
        }
    }

    private fun shouldKillSilverfish(): Boolean = countSilverfish() >= currentKillThreshold

    internal fun isWormfishSpotDetectionEnabled(): Boolean = detectWormfishSpot

    private fun rotateTo(yaw: Float, pitch: Float, duration: Long = 300L) {
        RotationExecutor.rotateTo(
            Rotation(yaw, pitch),
            TimedEaseStrategy(EasingType.LINEAR, EasingType.LINEAR, duration)
        )
    }

    internal fun onTick() {
        if (!clock.passed()) return
        if (mc.player == null || mc.level == null || mc.gameMode == null) return

        when (macroState) {
            MacroState.SWAP_TO_ROD -> {
                swapToFishingRod()
                clock.schedule(getActionDelay())
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
                    macroState = MacroState.REELING
                } else {
                    val bobber = mc.player?.fishing
                    val isBobbing = bobber?.let { it.isInWater || it.isInLava } ?: false

                    if (bobber == null) {
                        macroState = MacroState.SWAP_TO_ROD
                        return
                    }

                    if (!isBobbing && System.currentTimeMillis() - waitingStartTime > bobberTimeout.toLong()) {
                        macroState = MacroState.REELING
                    }
                }
            }

            MacroState.REELING -> {
                clock.schedule(Random.nextInt(reelInDelay.first.toInt(), reelInDelay.second.toInt()))
                MouseUtils.rightClick()
                macroState = MacroState.POST_REEL_DECIDE
            }

            MacroState.POST_REEL_DECIDE -> {
                mc.player?.let {
                    originalYaw = it.yRot
                    originalPitch = it.xRot
                }

                if (shouldKillSilverfish()) {
                    clock.schedule(getActionDelay())
                    if (killingMode == 0) {
                        macroState = MacroState.HYPERION_SWAP
                    }
                    if (killingMode == 1) {
                        macroState = MacroState.FIRE_SWAP
                    }
                } else {
                    macroState = MacroState.SWAP_TO_ROD
                }
            }

            MacroState.HYPERION_SWAP -> {
                val hypSlot = InventoryUtils.findItemInHotbar("hyperion")
                if (hypSlot != -1) {
                    InventoryUtils.holdHotbarSlot(hypSlot)
                    clock.schedule(getActionDelay())
                    macroState = MacroState.HYPERION_USE
                } else {
                    macroState = MacroState.RESET
                }
            }
            MacroState.HYPERION_ROTATE -> {
                rotateTo(originalYaw, originalPitch, duration = 300L)
            }

            MacroState.HYPERION_USE -> {
                MouseUtils.rightClick()
                macroState = MacroState.RESET
            }
            MacroState.FIRE_USE -> {
                MouseUtils.rightClick()
                macroState = MacroState.RESET
            }
            MacroState.FIRE_SWAP -> {
                val fireSlot = InventoryUtils.findItemInHotbar("fire")
                if (fireSlot != -1) {
                    InventoryUtils.holdHotbarSlot(fireSlot)
                    clock.schedule(getActionDelay())
                    macroState = MacroState.FIRE_USE
                }
            }

            MacroState.RESET -> {
                generateNewThreshold()
                rotateTo(originalYaw, originalPitch, duration = 300L)
                clock.schedule(getActionDelay())
                macroState = MacroState.SWAP_TO_ROD
            }

            MacroState.RESETTING -> {
                macroState = MacroState.SWAP_TO_ROD
            }

            MacroState.IDLE -> Unit
        }
    }

    fun detectWormfishSpots(
        level: Level,
        center: BlockPos,
        radius: Int,
    ): List<BlockPos> {
        if (!detectWormfishSpot) return emptyList()
        val minX = center.x - radius
        val maxX = center.x + radius
        val minZ = center.z - radius
        val maxZ = center.z + radius
        val spots = mutableListOf<BlockPos>()
        val mutablePos = BlockPos.MutableBlockPos()

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in 65..320) {
                    mutablePos.set(x, y, z)
                    val blockState = level.getBlockState(mutablePos)

                    if (blockState.`is`(Blocks.LAVA)) {
                        spots.add(mutablePos.immutable())
                    }
                }
            }
        }

        return spots
    }

}
