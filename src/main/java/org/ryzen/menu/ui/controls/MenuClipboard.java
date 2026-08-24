package org.ryzen.menu.ui.controls;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import net.minecraft.util.Util.OperatingSystem;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class MenuClipboard {
   private MenuClipboard() {
   }

   public static boolean shortcutDown() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.getWindow() != null) {
         long window = client.getWindow().getHandle();
         return Util.getOperatingSystem() == OperatingSystem.OSX ? keyDown(window, 343) || keyDown(window, 347) : keyDown(window, 341) || keyDown(window, 345);
      } else {
         return false;
      }
   }

   public static String get() {
      MinecraftClient client = MinecraftClient.getInstance();
      return client == null ? "" : client.keyboard.getClipboard();
   }

   public static void set(String value) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         client.keyboard.setClipboard(value == null ? "" : value);
      }
   }

   private static boolean keyDown(long window, int key) {
      return GLFW.glfwGetKey(window, key) == 1;
   }
}
