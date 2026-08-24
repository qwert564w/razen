package org.ryzen.pve.server;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

@Environment(EnvType.CLIENT)
public enum ServerProfile {
   GENERIC,
   FUNTIME,
   HOLYWORLD,
   REALLYWORLD;

   public static ServerProfile detect(MinecraftClient client) {
      ServerInfo server = client.getCurrentServerEntry();
      return server == null ? GENERIC : detect(server.name, server.address);
   }

   public static ServerProfile detect(String name, String address) {
      String identity = ((name == null ? "" : name) + " " + (address == null ? "" : address)).toLowerCase(Locale.ROOT);
      if (identity.contains("funtime")) {
         return FUNTIME;
      } else if (identity.contains("holyworld") || identity.contains("holy-world")) {
         return HOLYWORLD;
      } else {
         return !identity.contains("reallyworld") && !identity.contains("spookytime") ? GENERIC : REALLYWORLD;
      }
   }
}
