package org.ryzen.context;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

@Environment(EnvType.CLIENT)
public interface ScreenContext extends MinecraftContext {
   default Screen currentScreen() {
      return this.screen();
   }

   default boolean hasScreen() {
      return this.currentScreen() != null;
   }

   default boolean hasContainerScreen() {
      return this.currentScreen() instanceof HandledScreen;
   }

   default HandledScreen<?> containerScreen() {
      return this.currentScreen() instanceof HandledScreen<?> containerScreen ? containerScreen : null;
   }
}
