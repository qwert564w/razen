package org.ryzen.event.events.lifecycle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class WorldLeaveEvent extends Event {
   private MinecraftClient client;
   private ClientWorld level;

   public WorldLeaveEvent set(MinecraftClient client, ClientWorld level) {
      this.client = client;
      this.level = level;
      return this;
   }
   public MinecraftClient getClient() {
      return this.client;
   }
   public ClientWorld getLevel() {
      return this.level;
   }
}
