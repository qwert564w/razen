package org.ryzen.event.events.render;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class Render2DEvent extends Event {
   private MinecraftClient client;
   private InGameHud gui;
   private DrawContext guiGraphicsExtractor;
   private RenderTickCounter deltaTracker;

   public Render2DEvent set(MinecraftClient client, InGameHud gui, DrawContext guiGraphicsExtractor, RenderTickCounter deltaTracker) {
      this.client = client;
      this.gui = gui;
      this.guiGraphicsExtractor = guiGraphicsExtractor;
      this.deltaTracker = deltaTracker;
      return this;
   }
   public MinecraftClient getClient() {
      return this.client;
   }
   public InGameHud getGui() {
      return this.gui;
   }
   public DrawContext getGuiGraphicsExtractor() {
      return this.guiGraphicsExtractor;
   }
   public RenderTickCounter getDeltaTracker() {
      return this.deltaTracker;
   }
}
