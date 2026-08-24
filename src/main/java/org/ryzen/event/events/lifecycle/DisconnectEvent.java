package org.ryzen.event.events.lifecycle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class DisconnectEvent extends Event {
   private MinecraftClient client;
   private Screen screen;
   private boolean transferring;
   private boolean resetting;

   public DisconnectEvent set(MinecraftClient client, Screen screen, boolean transferring, boolean resetting) {
      this.client = client;
      this.screen = screen;
      this.transferring = transferring;
      this.resetting = resetting;
      return this;
   }
   public MinecraftClient getClient() {
      return this.client;
   }
   public Screen getScreen() {
      return this.screen;
   }
   public boolean isTransferring() {
      return this.transferring;
   }
   public boolean isResetting() {
      return this.resetting;
   }
}
