package com.FishingAddon

import org.cobalt.api.addon.Addon
import org.cobalt.api.event.EventBus
import org.cobalt.api.module.Module
import com.FishingAddon.module.Main
import com.FishingAddon.module.Normal
import com.FishingAddon.module.HotspotFishing
import com.FishingAddon.module.SurfStriders
import com.FishingAddon.module.WormFishing

object FishingAddon : Addon() {

  override fun onLoad() {
    listOf(
      Main,
      Normal,
      SurfStriders,
      WormFishing,
      HotspotFishing
    ).forEach(EventBus::register)
    println("FishingAddon loaded!")
  }

  override fun onUnload() {
    println("FishingAddon unloaded!")
  }

  override fun getModules(): List<Module> {
    return listOf(Main, Normal, SurfStriders, WormFishing, HotspotFishing)
  }
}
