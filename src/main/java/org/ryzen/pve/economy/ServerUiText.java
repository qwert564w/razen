package org.ryzen.pve.economy;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.ryzen.mixin.accessor.PlayerTabOverlayAccessor;

@Environment(EnvType.CLIENT)
public final class ServerUiText {
   private ServerUiText() {
   }

   public static String tabHeader(MinecraftClient client) {
      if (client != null && client.inGameHud != null && client.inGameHud.getPlayerListHud() != null) {
         Text header = ((PlayerTabOverlayAccessor)client.inGameHud.getPlayerListHud()).getHeader();
         return header == null ? "" : EconomyTextParser.normalize(header.getString());
      } else {
         return "";
      }
   }

   public static String serverHost(MinecraftClient client) {
      ServerInfo server = client == null ? null : client.getCurrentServerEntry();
      if (server != null && server.address != null) {
         String host = server.address.trim().toLowerCase(Locale.ROOT);
         if (host.startsWith("[")) {
            int closing = host.indexOf(93);
            return closing > 0 ? host.substring(1, closing) : host;
         } else {
            int colon = host.indexOf(58);
            return colon < 0 ? host : host.substring(0, colon);
         }
      } else {
         return "";
      }
   }
}
