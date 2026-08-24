package org.ryzen.event.events.lifecycle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class ClientStartEvent extends Event {
   private MinecraftClient client;

   public ClientStartEvent set(MinecraftClient client) {
      this.client = client;
      return this;
   }
   public MinecraftClient getClient() {
      return this.client;
   }
}
