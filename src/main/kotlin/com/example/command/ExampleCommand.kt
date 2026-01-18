package com.example.command

import org.cobalt.api.command.Command
import org.cobalt.api.command.annotation.DefaultHandler
import org.cobalt.api.command.annotation.SubCommand
import org.cobalt.api.util.ChatUtils

object ExampleCommand : Command(
  name = "example",
  aliases = arrayOf("exampleaddon", "ea"),
) {

  @DefaultHandler
  fun main() {
    println("ExampleAddon default handler!")
  }

  @SubCommand
  fun hello(name: String) {
    ChatUtils.sendMessage("Hello, $name!")
  }

}
