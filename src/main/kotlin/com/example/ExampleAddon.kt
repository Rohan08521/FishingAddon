package com.example

import com.example.command.ExampleCommand
import com.example.module.ExampleModule
import org.cobalt.api.addon.Addon
import org.cobalt.api.command.CommandManager
import org.cobalt.api.module.Module

object ExampleAddon : Addon() {

  override fun onLoad() {
    CommandManager.register(ExampleCommand)
    println("ExampleAddon loaded!")
  }

  override fun onUnload() {
    println("ExampleAddon unloaded!")
  }

  override fun getModules(): List<Module> {
    return listOf(ExampleModule)
  }

}
