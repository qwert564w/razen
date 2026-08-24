package org.ryzen.event.events.screen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.ryzen.event.CancellableEvent;

@Environment(EnvType.CLIENT)
public final class ScreenKeyEvent extends CancellableEvent {
   private Screen screen;
   private KeyInput keyEvent;
   private ScreenKeyEvent.Action action;

   public ScreenKeyEvent set(Screen screen, KeyInput keyEvent, ScreenKeyEvent.Action action) {
      this.screen = screen;
      this.keyEvent = keyEvent;
      this.action = action;
      return this;
   }
   public Screen getScreen() {
      return this.screen;
   }
   public KeyInput getKeyEvent() {
      return this.keyEvent;
   }
   public ScreenKeyEvent.Action getAction() {
      return this.action;
   }

   @Environment(EnvType.CLIENT)
   public static enum Action {
      PRESS,
      RELEASE;
   }
}
