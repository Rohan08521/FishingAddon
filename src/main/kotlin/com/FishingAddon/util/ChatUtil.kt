package com.FishingAddon.util

import net.minecraft.client.Minecraft

class ChatUtil() {
  fun sendChatMessage(message: String) {
    var message = message
    //message = TextUtil.colorize(message)


    if (Minecraft.getInstance().player == null) {
      //Logger.getAnonymousLogger().log(Level.INFO, TextUtil.toConsoleColors(message))
      return
    }
    Minecraft.getInstance().player!!.connection.sendChat(message)
  }

}
