package org.ryzen.event.events.game;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class GameTickEvent extends Event {
   private MinecraftClient client;
   private TickContext context;

   public GameTickEvent set(MinecraftClient client, TickContext context) {
      this.client = client;
      this.context = context;
      return this;
   }
   public MinecraftClient getClient() {
      return this.client;
   }
   public TickContext getContext() {
      return this.context;
   }
}
