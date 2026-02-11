package com.FishingAddon.util

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import net.minecraft.ChatFormatting

object EntityUtils {

  private val mc: Minecraft =
    Minecraft.getInstance()

  fun findMob(name: String, nearTo: Entity? = null): Entity? {
    val world = mc.level ?: return null

    return world.entitiesForRendering().filter {
      nearTo == null || it.distanceTo(nearTo) < 3
    }.find {
      ChatFormatting.stripFormatting(it.name.string)?.contains(name, ignoreCase = true) == true
    }
  }

  fun Entity.skullTextureMatch(texture: String): Boolean {
    if (this !is ArmorStand) return false

    val head = this.getItemBySlot(EquipmentSlot.HEAD)
    if (head.item != Items.PLAYER_HEAD) return false

    val profile = head.get(DataComponents.PROFILE) ?: return false
    val textures = profile.partialProfile().properties["textures"] ?: return false

    return textures.any { it.value == texture }
  }

}
