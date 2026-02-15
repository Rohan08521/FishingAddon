package com.FishingAddon.util.helper

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity

class MobCache(
  val predicate: (Entity) -> Boolean,
) {

  private val entities = mutableMapOf<Int, Entity>()
  private var tickCounter = 0

  fun update() {
    val world = mc.level ?: return

    if (tickCounter % 10 == 0) {
      for (entity in world.entitiesForRendering()) {
        if (predicate(entity)) {
          entities[entity.id] = entity
        }
      }

      val worldIds = world.entitiesForRendering().mapTo(HashSet()) { it.id }
      entities.keys.removeIf { it !in worldIds }
    }

    tickCounter++
  }

  fun getClosestEntity(): Entity? {
    val player = mc.player ?: return null

    return entities.values.minByOrNull { it.distanceToSqr(player.eyePosition) }
  }

  fun getEntities(): Collection<Entity> {
    return entities.values
  }

  companion object {
    private val mc: Minecraft =
      Minecraft.getInstance()
  }

}
