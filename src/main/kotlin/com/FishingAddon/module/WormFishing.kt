package com.FishingAddon.module

import com.FishingAddon.module.Main.detectFishbite
import com.FishingAddon.module.Main.swapToFishingRod
import com.FishingAddon.util.helper.Clock
import java.awt.Color
import kotlin.random.Random
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.CheckboxSetting
import org.cobalt.api.module.setting.impl.RangeSetting
import org.cobalt.api.module.setting.impl.SliderSetting
import org.cobalt.api.util.InventoryUtils
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.render.Render3D

object WormFishing : Module("WormFishing Settings") {
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

    private val killSilverfishAt by RangeSetting(
        name = "Kill Count Range",
        description = "The range of silverfish count to trigger killing",
        defaultValue = Pair(18.0, 20.0),
        min = 1.0,
        max = 20.0
    )

    private val highlightWormfishSpot by CheckboxSetting(
        name = "Highlight Wormfish Spots",
        description = "Highlights potential wormfish fishing spots in the Crystal Hollows.",
        defaultValue = false
    )

    private val hyperionSwapDelay by RangeSetting(
        name = "Hyperion Swap Delay",
        description = "Delay between swapping to Hyperion and using it (in ms)",
        defaultValue = Pair(150.0, 300.0),
        min = 0.0,
        max = 1000.0
    )

    private val stateTransitionDelay by RangeSetting(
        name = "State Transition Delay",
        description = "Small delays between logic steps (in ms)",
        defaultValue = Pair(100.0, 200.0),
        min = 0.0,
        max = 500.0
    )

    private var macroState = MacroState.IDLE
    private val clock = Clock()
    private val mc = Minecraft.getInstance()
    private var waitingStartTime = 0L
    private var currentKillThreshold = 20

    private enum class MacroState {
        IDLE,
        SWAP_TO_ROD,
        CASTING,
        WAITING,
        REELING,
        POST_REEL_DECIDE,
        HYPERION_SWAP,
        HYPERION_USE,
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

    private fun getTransitionDelay(): Int {
        return Random.nextInt(stateTransitionDelay.first.toInt(), stateTransitionDelay.second.toInt() + 1)
    }

    private fun countSilverfish(): Int {
        val entities = mc.level?.entitiesForRendering() ?: return 0
        return entities.count { it is Silverfish && it.position().distanceTo(mc.player?.position() ?: Vec3.ZERO) <= 10.0 }
    }

    private fun shouldKillSilverfish(): Boolean {
        return countSilverfish() >= currentKillThreshold
    }

    internal fun onTick() {
        if (!clock.passed()) return
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
                    val isBobbing = bobber?.let { it.isInWater || it.isInLava } ?: false

                    if (!isBobbing && System.currentTimeMillis() - waitingStartTime > bobberTimeout.toLong()) {
                        macroState = MacroState.REELING
                        clock.schedule(Random.nextInt(100, 200))
                    }
                }
            }

            MacroState.REELING -> {
                MouseUtils.rightClick()
                macroState = MacroState.POST_REEL_DECIDE
                clock.schedule(getTransitionDelay())
            }

            MacroState.POST_REEL_DECIDE -> {
                if (shouldKillSilverfish()) {
                    macroState = MacroState.HYPERION_SWAP
                    clock.schedule(getTransitionDelay())
                } else {
                    macroState = MacroState.CASTING
                    clock.schedule(getTransitionDelay())
                }
            }

            MacroState.HYPERION_SWAP -> {
                val hypSlot = InventoryUtils.findItemInHotbar("hyperion")
                if (hypSlot != -1) {
                    InventoryUtils.holdHotbarSlot(hypSlot)
                    clock.schedule(Random.nextInt(hyperionSwapDelay.first.toInt(), hyperionSwapDelay.second.toInt() + 1))
                    macroState = MacroState.HYPERION_USE
                } else {
                    macroState = MacroState.RESET
                }
            }

            MacroState.HYPERION_USE -> {
                MouseUtils.rightClick()
                clock.schedule(getTransitionDelay())
                macroState = MacroState.RESET
            }

            MacroState.RESET -> {
                generateNewThreshold()
                clock.schedule(getTransitionDelay())
                macroState = MacroState.SWAP_TO_ROD
            }

            MacroState.RESETTING -> {
                macroState = MacroState.SWAP_TO_ROD
            }

            MacroState.IDLE -> {
            }
        }
    }

    fun detectWormfishSpot(
        level: Level,
        center: BlockPos,
        radius: Int,
    ): BlockPos? {
        val minX = center.x - radius
        val maxX = center.x + radius
        val minZ = center.z - radius
        val maxZ = center.z + radius

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in 65..320) {
                    val pos = BlockPos(x, y, z)
                    val blockState = level.getBlockState(pos)

                    if (blockState.`is`(Blocks.LAVA)) {
                        return pos
                    }
                }
            }
        }

        return null
    }

    @SubscribeEvent
    fun onWorldRender(event: WorldRenderEvent.Start) {
        if (!highlightWormfishSpot) return

        val player = mc.player ?: return
        val level = mc.level ?: return
        val playerPos = player.blockPosition()
        val lavaPos = detectWormfishSpot(level, playerPos, 10) ?: return

        val blockBox = AABB.ofSize(
            Vec3.atCenterOf(lavaPos),
            1.0,
            1.0,
            1.0
        )

        Render3D.drawBox(event.context, blockBox, Color(0, 150, 255), esp = true)
    }
}
