package org.ryzen.event.events.game;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.ryzen.event.CancellableEvent;

@Environment(EnvType.CLIENT)
public final class AttackEvent extends CancellableEvent {
   private MinecraftClient client;

   public AttackEvent set(MinecraftClient client) {
      this.client = client;
      return this;
   }
   public MinecraftClient getClient() {
      return this.client;
   }
}
