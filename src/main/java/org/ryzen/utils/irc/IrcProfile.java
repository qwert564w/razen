package org.ryzen.utils.irc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class IrcProfile {
   private static final String UNKNOWN = "Unknown";
   private static volatile IrcRole role = IrcRole.MEMBER;

   private IrcProfile() {
   }

   public static String name() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.getSession() == null) {
         return "Unknown";
      } else {
         String name = mc.getSession().getUsername();
         return name != null && !name.isBlank() ? name : "Unknown";
      }
   }

   public static IrcRole role() {
      return role;
   }

   public static void setRole(IrcRole newRole) {
      role = newRole == null ? IrcRole.MEMBER : newRole;
   }
}
