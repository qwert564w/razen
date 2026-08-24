package org.ryzen.event.events.input;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.ryzen.event.CancellableEvent;

@Environment(EnvType.CLIENT)
public final class CharacterInputEvent extends CancellableEvent {
   private final long window;
   private final int codePoint;

   public CharacterInputEvent(long window, int codePoint) {
      this.window = window;
      this.codePoint = codePoint;
   }
   public long getWindow() {
      return this.window;
   }
   public int getCodePoint() {
      return this.codePoint;
   }
}
