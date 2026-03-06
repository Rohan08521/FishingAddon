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
    private val highlightWormfishSpot by CheckboxSetting(
        name = "Highlight Wormfish Spots",
        description = "Highlights potential wormfish fishing spots in the Crystal Hollows.",
        defaultValue = false
    )

    private val lavaEspRadius by SliderSetting(
        name = "Lava ESP Radius",
        description = "Horizontal scan radius for lava ESP (in blocks).",
        defaultValue = 30.0,
        min = 8.0,
        max = 64.0
    )

    private val lavaEspRefreshRate by SliderSetting(
        name = "Lava ESP Refresh",
        description = "How often lava ESP rescans nearby blocks (in ms).",
        defaultValue = 1000.0,
        min = 250.0,
        max = 5000.0
    )

    private val castDelay by RangeSetting(
        name = "Cast Delay",
        description = "Delay range before casting (in ms)",
        defaultValue = Pair(100.0, 200.0),
        min = 5.0,
        max = 1000.0
    )

    private val reelInDelay by RangeSetting(
        name = "Reel In Delay",
        description = "Delay range after reeling in (in ms)",
        defaultValue = Pair(200.0, 400.0),
        min = 5.0,
        max = 1000.0
    )

    private val bobberTimeout by SliderSetting(
        name = "Recast Bobber Delay",
        description = "Time to wait for bobber to enter water before recasting (in ms)",
        defaultValue = 20000.0,
        min = 5000.0,
        max = 60000.0
    )

    private val rodSwapDelay by RangeSetting(
        name = "Rod Swap Delay",
        description = "Delay after swapping to rod before casting (in ms)",
        defaultValue = Pair(200.0, 500.0),
        min = 50.0,
        max = 1500.0
    )

    private val transitionDelay by RangeSetting(
        name = "Transition Delay",
        description = "Shared short delay for recast/reel transitions (in ms)",
        defaultValue = Pair(100.0, 200.0),
        min = 10.0,
        max = 1000.0
    )

    private val killSilverfishAt by RangeSetting(
        name = "Worm Cap Range",
        description = "The range of Flaming Worms count to trigger killing",
        defaultValue = Pair(18.0, 20.0),
        min = 1.0,
        max = 20.0
    )

    private val hyperionSwapDelay by RangeSetting(
        name = "Hyperion Swap Delay",
        description = "Delay between swapping to Hyperion and using it (in ms)",
        defaultValue = Pair(150.0, 300.0),
        min = 10.0,
        max = 1000.0
    )

    private var macroState = MacroState.IDLE
    private val clock = Clock()
    private val mc = Minecraft.getInstance()
    private var waitingStartTime = 0L
    private var currentKillThreshold = 20
    private var cachedLavaBoxes: List<AABB> = emptyList()
    private var lastLavaScanCenter: BlockPos? = null
    private var lastLavaScanAt = 0L
    private var lastLavaScanLevelId = 0

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
        val isBobbing = mc.player?.fishing?.let { it.isInWater || it.isInLava } ?: false
        macroState = if (isBobbing) MacroState.WAITING else MacroState.SWAP_TO_ROD
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
                clock.schedule(Random.nextInt(rodSwapDelay.first.toInt(), rodSwapDelay.second.toInt()))
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

                    if (bobber == null) {
                        clock.schedule(Random.nextInt(transitionDelay.first.toInt(), transitionDelay.second.toInt()))
                        macroState = MacroState.CASTING
                        return
                    }

                    if (!isBobbing && System.currentTimeMillis() - waitingStartTime > bobberTimeout.toLong()) {
                        macroState = MacroState.REELING
                        clock.schedule(Random.nextInt(transitionDelay.first.toInt(), transitionDelay.second.toInt()))
                    }
                }
            }

            MacroState.REELING -> {
                MouseUtils.rightClick()
                clock.schedule(Random.nextInt(transitionDelay.first.toInt(), transitionDelay.second.toInt()))
                macroState = MacroState.POST_REEL_DECIDE
            }

            MacroState.POST_REEL_DECIDE -> {
                if (shouldKillSilverfish()) {
                    clock.schedule(Random.nextInt(transitionDelay.first.toInt(), transitionDelay.second.toInt()))
                    macroState = MacroState.HYPERION_SWAP
                } else {
                    clock.schedule(Random.nextInt(transitionDelay.first.toInt(), transitionDelay.second.toInt()))
                    macroState = MacroState.RESETTING
                }
            }

            MacroState.HYPERION_SWAP -> {
                val hypSlot = InventoryUtils.findItemInHotbar("hyperion")
                if (hypSlot != -1) {
                    InventoryUtils.holdHotbarSlot(hypSlot)
                    clock.schedule(Random.nextInt(hyperionSwapDelay.first.toInt(), hyperionSwapDelay.second.toInt()))
                    macroState = MacroState.HYPERION_USE
                } else {
                    macroState = MacroState.RESET
                }
            }
            MacroState.HYPERION_USE -> {
                MouseUtils.rightClick()
                macroState = MacroState.RESET
            }

            MacroState.RESET -> {
                generateNewThreshold()
                clock.schedule(Random.nextInt(transitionDelay.first.toInt(), transitionDelay.second.toInt()))
                macroState = MacroState.SWAP_TO_ROD
            }

            MacroState.RESETTING -> {
                macroState = MacroState.SWAP_TO_ROD
            }

            MacroState.IDLE -> {
            }
        }
    }

    private fun shouldRefreshLavaScan(center: BlockPos, level: Level): Boolean {
        val now = System.currentTimeMillis()
        val refreshInterval = lavaEspRefreshRate.toLong()
        val previousCenter = lastLavaScanCenter
        val levelId = System.identityHashCode(level)
        if (previousCenter == null || lastLavaScanLevelId != levelId) return true
        if (now - lastLavaScanAt >= refreshInterval) return true

        // Rescan sooner if player moved enough to expose new nearby lava.
        return previousCenter.distManhattan(center) >= 3
    }

    fun detectWormfishSpots(
        level: Level,
        center: BlockPos,
        radius: Int,
    ): List<AABB> {
        val minX = center.x - radius
        val maxX = center.x + radius
        val minZ = center.z - radius
        val maxZ = center.z + radius
        // Keep scan bounds mapping-safe and aligned with prior Crystal Hollows worm spot logic.
        val minY = 65
        val maxY = 320
        val lavaBoxes = mutableListOf<AABB>()
        val mutablePos = BlockPos.MutableBlockPos()

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in minY..maxY) {
                    mutablePos.set(x, y, z)
                    val blockState = level.getBlockState(mutablePos)

                    if (blockState.`is`(Blocks.LAVA)) {
                        lavaBoxes.add(
                            AABB.ofSize(
                                Vec3.atCenterOf(mutablePos),
                                1.0,
                                1.0,
                                1.0
                            )
                        )
                    }
                }
            }
        }

        return lavaBoxes
    }

    @SubscribeEvent
    fun  onWorldRender(event: WorldRenderEvent.Start) {
        if (!highlightWormfishSpot) return

        val player = mc.player ?: return
        val level = mc.level ?: return
        val playerPos = player.blockPosition()

        if (shouldRefreshLavaScan(playerPos, level)) {
            val radius = lavaEspRadius.toInt()
            cachedLavaBoxes = detectWormfishSpots(level, playerPos, radius)
            lastLavaScanCenter = playerPos.immutable()
            lastLavaScanAt = System.currentTimeMillis()
            lastLavaScanLevelId = System.identityHashCode(level)
        }

        for (lavaBox in cachedLavaBoxes) {
            Render3D.drawBox(event.context, lavaBox, Color(191, 70, 63), esp = true)
        }
    }
}
