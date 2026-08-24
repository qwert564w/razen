package org.ryzen.event.events.render;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class Render3DEvent extends Event {
   private MinecraftClient client;
   private GameRenderer gameRenderer;
   private RenderTickCounter deltaTracker;

   public Render3DEvent set(MinecraftClient client, GameRenderer gameRenderer, RenderTickCounter deltaTracker) {
      this.client = client;
      this.gameRenderer = gameRenderer;
      this.deltaTracker = deltaTracker;
      return this;
   }
   public MinecraftClient getClient() {
      return this.client;
   }
   public GameRenderer getGameRenderer() {
      return this.gameRenderer;
   }
   public RenderTickCounter getDeltaTracker() {
      return this.deltaTracker;
   }
}
