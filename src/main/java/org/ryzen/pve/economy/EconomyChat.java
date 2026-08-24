package org.ryzen.pve.economy;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class EconomyChat {
   private EconomyChat() {
   }

   public static String incomingText(Packet<?> packet) {
      if (packet instanceof GameMessageS2CPacket systemChat) {
         return systemChat.content().getString();
      } else if (packet instanceof ProfilelessChatMessageS2CPacket disguisedChat) {
         return disguisedChat.message().getString();
      } else if (packet instanceof ChatMessageS2CPacket playerChat) {
         Text unsigned = playerChat.unsignedContent();
         return unsigned == null ? playerChat.body().content() : unsigned.getString();
      } else {
         return null;
      }
   }
}
