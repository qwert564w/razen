package org.ryzen.event.events.render;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class FinalGuiRenderEvent extends Event {
   private MinecraftClient client;
   private InGameHud gui;
   private DrawContext guiGraphicsExtractor;
   private RenderTickCounter deltaTracker;
   private boolean renderHud;
   private boolean renderScreen;
   private int mouseX;
   private int mouseY;

   public FinalGuiRenderEvent set(
      MinecraftClient client,
      InGameHud gui,
      DrawContext guiGraphicsExtractor,
      RenderTickCounter deltaTracker,
      boolean renderHud,
      boolean renderScreen,
      int mouseX,
      int mouseY
   ) {
      this.client = client;
      this.gui = gui;
      this.guiGraphicsExtractor = guiGraphicsExtractor;
      this.deltaTracker = deltaTracker;
      this.renderHud = renderHud;
      this.renderScreen = renderScreen;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
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
   public boolean isRenderHud() {
      return this.renderHud;
   }
   public boolean isRenderScreen() {
      return this.renderScreen;
   }
   public int getMouseX() {
      return this.mouseX;
   }
   public int getMouseY() {
      return this.mouseY;
   }
}
