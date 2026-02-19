package com.FishingAddon.module

import net.minecraft.client.Minecraft
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.CheckboxSetting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import org.cobalt.api.event.EventBus
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.PacketEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.module.setting.impl.KeyBindSetting
import org.cobalt.api.module.setting.impl.ModeSetting
import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.helper.KeyBind
object QOL : Module(
  name = "QoL tab",
){
  val AutoContinueChat by CheckboxSetting(
    name = "Auto Continue Chat",
    description = "Auto Continue Chat with hypixel skyblock npc",
    defaultValue = false
  )

  fun sendChatMessage(message: String) {
    var message = message
    //message = TextUtil.colorize(message)


    if (Minecraft.getInstance().player == null) {
      //Logger.getAnonymousLogger().log(Level.INFO, TextUtil.toConsoleColors(message))
      return
    }
    Minecraft.getInstance().player!!.connection.sendChat(message)
  }

  @SubscribeEvent
  fun onPacket(event: PacketEvent.Incoming) {
    if (AutoContinueChat) {

      val packet = event.packet
      val mc = net.minecraft.client.Minecraft.getInstance()

      if (packet is net.minecraft.network.protocol.game.ClientboundSystemChatPacket) {
        val content = packet.content
        if (content == null) return

        val optionText: Component? = content.getSiblings().getOrNull(0)
        if (optionText == null) return
        val clickEvent: ClickEvent? = optionText.getStyle().getClickEvent()
        if (clickEvent == null) return
        if (clickEvent.action() !== ClickEvent.Action.RUN_COMMAND) return
        if ((clickEvent as ClickEvent.RunCommand).command().startsWith("/selectnpcoption")) {
          mc.execute({
            sendChatMessage((clickEvent as ClickEvent.RunCommand).command())
          })
        }
      }
    }
  }
}
