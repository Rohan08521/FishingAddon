package com.FishingAddon.module

import java.awt.Color
import kotlin.math.floor
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.CheckboxSetting
import org.cobalt.api.module.setting.impl.KeyBindSetting
import org.cobalt.api.module.setting.impl.SliderSetting
import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.render.Render3D
import org.cobalt.api.util.helper.KeyBind
import org.lwjgl.glfw.GLFW

object Visuals : Module("Visuals") {
    private val renderStartBlockBox by CheckboxSetting(
        name = "Render Start Block Box",
        description = "Render the box under your feet when the macro is toggled.",
        defaultValue = true
    )

    private val highlightWormfishSpot by CheckboxSetting(
        name = "Highlight Wormfish Spots",
        description = "Highlights potential wormfish fishing spots in the Crystal Hollows.",
        defaultValue = false
    )

    private val wormfishScannerKeyBind by KeyBindSetting(
        name = "Wormfish Scanner Keybind",
        description = "Keybind to toggle the Wormfish ESP scanner.",
        defaultValue = KeyBind(GLFW.GLFW_KEY_UNKNOWN)
    )

    private val wormfishEspRadius by SliderSetting(
        name = "Wormfish ESP Radius",
        description = "Radius (in blocks) to scan for lava blocks to highlight.",
        defaultValue = 192.0,
        min = 1.0,
        max = 256.0
    )

    private val wormfishEspRescanDelay by SliderSetting(
        name = "Wormfish ESP Rescan Delay",
        description = "Delay between lava rescans for ESP (in ms).",
        defaultValue = 10000.0,
        min = 0.0,
        max = 10000.0
    )

    private val mc = Minecraft.getInstance()
    private var savedBlockX = 0
    private var savedBlockY = 0
    private var savedBlockZ = 0
    private var hasSavedStartBlock = false
    private val cachedLavaPositions = mutableListOf<BlockPos>()
    private var lastLavaScanTime = 0L
    private var lastLavaScanCenter: BlockPos? = null
    private var lastLavaDimension = ""
    private var wormfishScannerOverride: Boolean? = null
    private var wasWormfishScannerKeyPressed = false

    internal fun captureStartBlock() {
        val player = mc.player ?: return
        val playerPos = player.position()
        savedBlockX = floor(playerPos.x).toInt()
        savedBlockY = floor(playerPos.y - 1).toInt()
        savedBlockZ = floor(playerPos.z).toInt()
        hasSavedStartBlock = true
    }

    internal fun clearStartBlock() {
        hasSavedStartBlock = false
    }

    private fun clearWormfishCache() {
        cachedLavaPositions.clear()
        lastLavaScanTime = 0L
        lastLavaScanCenter = null
        lastLavaDimension = ""
    }

    private fun isWormfishScannerActive(): Boolean {
        return wormfishScannerOverride ?: highlightWormfishSpot
    }

    @SubscribeEvent
    fun onTick(event: TickEvent) {
        val isPressed = wormfishScannerKeyBind.isPressed()
        if (isPressed && !wasWormfishScannerKeyPressed) {
            val nextState = !isWormfishScannerActive()
            wormfishScannerOverride = if (nextState == highlightWormfishSpot) null else nextState
            if (!nextState) {
                clearWormfishCache()
            }

            ChatUtils.sendMessage(
                "Wormfish Scanner is now "
                    + if (nextState) "${ChatFormatting.GREEN}Enabled" else "${ChatFormatting.RED}Disabled"
                    + ChatFormatting.RESET
            )
        }
        wasWormfishScannerKeyPressed = isPressed
    }

    @SubscribeEvent
    fun onWorldRender(event: WorldRenderEvent.Start) {
        if (Main.isToggled() && renderStartBlockBox && hasSavedStartBlock) {
            val blockBox = AABB(
                savedBlockX.toDouble(), savedBlockY.toDouble(), savedBlockZ.toDouble(),
                (savedBlockX + 1).toDouble(), (savedBlockY + 1).toDouble(), (savedBlockZ + 1).toDouble()
            )
            Render3D.drawBox(event.context, blockBox, Color(0, 150, 255, 100), esp = true)
        }

        if (!isWormfishScannerActive()) {
            clearWormfishCache()
            return
        }

        val player = mc.player ?: return
        val level = mc.level ?: return
        val playerPos = player.blockPosition()
        val now = System.currentTimeMillis()
        val scanRadius = wormfishEspRadius.toInt()
        val scanDelayMs = wormfishEspRescanDelay.toLong()
        val currentDimension = level.dimension().toString()
        val previousCenter = lastLavaScanCenter
        val movedEnoughToRescan = previousCenter == null || horizontalDistanceSquared(playerPos, previousCenter) >= 32 * 32
        val dimensionChanged = lastLavaDimension != currentDimension
        val shouldRescan = cachedLavaPositions.isEmpty() || dimensionChanged || movedEnoughToRescan || now - lastLavaScanTime >= scanDelayMs

        if (shouldRescan) {
            cachedLavaPositions.clear()
            cachedLavaPositions.addAll(WormFishing.detectWormfishSpots(level, playerPos, scanRadius))
            lastLavaScanTime = now
            lastLavaScanCenter = playerPos.immutable()
            lastLavaDimension = currentDimension
        }

        if (cachedLavaPositions.isEmpty()) return

        val renderRadiusSq = scanRadius * scanRadius
        for (lavaPos in cachedLavaPositions) {
            if (horizontalDistanceSquared(playerPos, lavaPos) > renderRadiusSq) continue

            val blockBox = AABB.ofSize(
                Vec3.atCenterOf(lavaPos),
                1.0,
                1.0,
                1.0
            )
            Render3D.drawBox(event.context, blockBox, Color(0, 150, 255), esp = true)
        }
    }

    private fun horizontalDistanceSquared(first: BlockPos, second: BlockPos): Int {
        val dx = first.x - second.x
        val dz = first.z - second.z
        return dx * dx + dz * dz
    }
}
