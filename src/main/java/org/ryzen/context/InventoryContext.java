package org.ryzen.context;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;

@Environment(EnvType.CLIENT)
public interface InventoryContext extends ScreenContext {
   default ScreenHandler menu() {
      HandledScreen<?> containerScreen = this.containerScreen();
      return containerScreen != null ? containerScreen.getScreenHandler() : null;
   }

   default boolean hasMenu() {
      return this.menu() != null;
   }
}
