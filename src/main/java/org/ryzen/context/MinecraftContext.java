package org.ryzen.context;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public interface MinecraftContext {
   MinecraftClient mc = MinecraftClient.getInstance();

   default MinecraftClient client() {
      return mc;
   }

   default ClientPlayerEntity player() {
      return mc.player;
   }

   default ClientWorld level() {
      return mc.world;
   }

   default ClientPlayerInteractionManager gameMode() {
      return mc.interactionManager;
   }

   default Screen screen() {
      return mc.currentScreen;
   }

   default boolean inGame() {
      return this.player() != null && this.level() != null;
   }
}
