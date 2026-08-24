package org.ryzen.event.events.screen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import org.ryzen.event.CancellableEvent;

@Environment(EnvType.CLIENT)
public final class ScreenMouseButtonEvent extends CancellableEvent {
   private Screen screen;
   private Click mouseButtonEvent;
   private ScreenMouseButtonEvent.Action action;
   private double dragX;
   private double dragY;

   public ScreenMouseButtonEvent set(Screen screen, Click mouseButtonEvent, ScreenMouseButtonEvent.Action action, double dragX, double dragY) {
      this.screen = screen;
      this.mouseButtonEvent = mouseButtonEvent;
      this.action = action;
      this.dragX = dragX;
      this.dragY = dragY;
      return this;
   }
   public Screen getScreen() {
      return this.screen;
   }
   public Click getMouseButtonEvent() {
      return this.mouseButtonEvent;
   }
   public ScreenMouseButtonEvent.Action getAction() {
      return this.action;
   }
   public double getDragX() {
      return this.dragX;
   }
   public double getDragY() {
      return this.dragY;
   }

   @Environment(EnvType.CLIENT)
   public static enum Action {
      CLICK,
      RELEASE,
      DRAG;
   }
}
