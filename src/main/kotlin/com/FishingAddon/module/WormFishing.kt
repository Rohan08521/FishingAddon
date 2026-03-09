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
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.RangeSetting
import org.cobalt.api.module.setting.impl.SliderSetting
import org.cobalt.api.util.InventoryUtils
import org.cobalt.api.util.MouseUtils

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

    private val hyperionSwapDelay by RangeSetting(
        name = "Hyperion Delay",
        description = "Delay between swapping to Hyperion and using it (in ms)",
        defaultValue = Pair(150.0, 300.0),
        min = 0.0,
        max = 1000.0
    )

    private val fishingSwapDelay by RangeSetting(
        name = "Rod Hotbar Delay",
        description = "Delay between swapping to Rod",
        defaultValue = Pair(150.0, 300.0),
        min = 0.0,
        max = 1000.0
    )

    private val stateTransitionDelay by RangeSetting(
        name = "Transition Delay",
        description = "Small delays between logic steps (in ms)",
        defaultValue = Pair(100.0, 200.0),
        min = 0.0,
        max = 500.0
    )

    private val bobberTimeout by SliderSetting(
        name = "Fishing Rod Failsafe",
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

    private fun getTransitionDelay(): Int =
        Random.nextInt(stateTransitionDelay.first.toInt(), stateTransitionDelay.second.toInt() + 1)

    private fun countSilverfish(): Int {
        val entities = mc.level?.entitiesForRendering() ?: return 0
        return entities.count {
            it is Silverfish && it.position().distanceTo(mc.player?.position() ?: Vec3.ZERO) <= 10.0
        }
    }

    private fun shouldKillSilverfish(): Boolean = countSilverfish() >= currentKillThreshold

    internal fun onTick() {
        if (!clock.passed()) return
        if (mc.player == null || mc.level == null || mc.gameMode == null) return

        when (macroState) {
            MacroState.SWAP_TO_ROD -> {
                swapToFishingRod()
                clock.schedule(Random.nextInt(fishingSwapDelay.first.toInt(), fishingSwapDelay.second.toInt() + 1))
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
                if (shouldKillSilverfish()) {
                    clock.schedule(getTransitionDelay())
                    macroState = MacroState.HYPERION_SWAP
                } else {
                    clock.schedule(getTransitionDelay())
                    macroState = MacroState.SWAP_TO_ROD
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
                macroState = MacroState.RESET
            }

            MacroState.RESET -> {
                generateNewThreshold()
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
        val spots = mutableListOf<BlockPos>()
        val centerX = center.x
        val centerZ = center.z
        val radiusSq = radius * radius
        val mutablePos = BlockPos.MutableBlockPos()
        val topPos = BlockPos.MutableBlockPos()
        val minChunkX = (centerX - radius) shr 4
        val maxChunkX = (centerX + radius) shr 4
        val minChunkZ = (centerZ - radius) shr 4
        val maxChunkZ = (centerZ + radius) shr 4
        val minY = 65
        val maxY = 320

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                if (!level.hasChunk(chunkX, chunkZ)) continue

                val startX = maxOf(chunkX shl 4, centerX - radius)
                val endX = minOf((chunkX shl 4) + 15, centerX + radius)
                val startZ = maxOf(chunkZ shl 4, centerZ - radius)
                val endZ = minOf((chunkZ shl 4) + 15, centerZ + radius)

                for (x in startX..endX) {
                    val dx = x - centerX
                    val dxSq = dx * dx

                    for (z in startZ..endZ) {
                        val dz = z - centerZ
                        if (dxSq + dz * dz > radiusSq) continue

                        for (y in maxY downTo minY) {
                            mutablePos.set(x, y, z)
                            if (!level.getBlockState(mutablePos).`is`(Blocks.LAVA)) continue

                            topPos.set(x, y + 1, z)
                            val blockAbove = if (y >= maxY) null else level.getBlockState(topPos)
                            if (isExposedLavaSurface(blockAbove)) {
                                spots.add(mutablePos.immutable())
                                break
                            }
                        }
                    }
                }
            }
        }

        return spots
    }

    private fun isExposedLavaSurface(blockAbove: BlockState?): Boolean {
        if (blockAbove == null) return true
        return !blockAbove.`is`(Blocks.LAVA) && !blockAbove.blocksMotion()
    }

}
