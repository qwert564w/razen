package org.ryzen.event.events.screen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.ryzen.event.CancellableEvent;

@Environment(EnvType.CLIENT)
public final class ScreenRenderEvent extends CancellableEvent {
   private Screen screen;
   private DrawContext guiGraphicsExtractor;
   private int mouseX;
   private int mouseY;
   private float partialTick;

   public ScreenRenderEvent set(Screen screen, DrawContext guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
      this.screen = screen;
      this.guiGraphicsExtractor = guiGraphicsExtractor;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.partialTick = partialTick;
      return this;
   }
   public Screen getScreen() {
      return this.screen;
   }
   public DrawContext getGuiGraphicsExtractor() {
      return this.guiGraphicsExtractor;
   }
   public int getMouseX() {
      return this.mouseX;
   }
   public int getMouseY() {
      return this.mouseY;
   }
   public float getPartialTick() {
      return this.partialTick;
   }
}
