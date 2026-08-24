package org.ryzen.event.events.input;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.ryzen.event.CancellableEvent;

@Environment(EnvType.CLIENT)
public final class MouseInputEvent extends CancellableEvent {
   private final long window;
   private final int button;
   private final int action;
   private final int modifiers;

   public MouseInputEvent(long window, int button, int action, int modifiers) {
      this.window = window;
      this.button = button;
      this.action = action;
      this.modifiers = modifiers;
   }
   public long getWindow() {
      return this.window;
   }
   public int getButton() {
      return this.button;
   }
   public int getAction() {
      return this.action;
   }
   public int getModifiers() {
      return this.modifiers;
   }
}
