package org.ryzen.event.events.screen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.ryzen.event.CancellableEvent;

@Environment(EnvType.CLIENT)
public final class ScreenCloseEvent extends CancellableEvent {
   private MinecraftClient client;
   private Screen screen;

   public ScreenCloseEvent set(MinecraftClient client, Screen screen) {
      this.client = client;
      this.screen = screen;
      return this;
   }
   public MinecraftClient getClient() {
      return this.client;
   }
   public Screen getScreen() {
      return this.screen;
   }
}
