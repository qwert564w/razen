package org.ryzen.event.events.input;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class PlayerInputEvent extends Event {
   private final KeyboardInput keyboardInput;
   private Vec2f moveVector;
   private PlayerInput keyPresses;

   public PlayerInputEvent(KeyboardInput keyboardInput, PlayerInput keyPresses, Vec2f moveVector) {
      this.keyboardInput = keyboardInput;
      this.keyPresses = keyPresses;
      this.moveVector = moveVector;
   }

   public void setShift(boolean shift) {
      PlayerInput in = this.keyPresses;
      this.keyPresses = new PlayerInput(in.forward(), in.backward(), in.left(), in.right(), in.jump(), shift, in.sprint());
   }

   public void setJump(boolean jump) {
      PlayerInput in = this.keyPresses;
      this.keyPresses = new PlayerInput(in.forward(), in.backward(), in.left(), in.right(), jump, in.sneak(), in.sprint());
   }

   public void setSprint(boolean sprint) {
      PlayerInput in = this.keyPresses;
      this.keyPresses = new PlayerInput(in.forward(), in.backward(), in.left(), in.right(), in.jump(), in.sneak(), sprint);
   }

   public void setDirections(boolean forward, boolean backward, boolean left, boolean right) {
      PlayerInput in = this.keyPresses;
      this.keyPresses = new PlayerInput(forward, backward, left, right, in.jump(), in.sneak(), in.sprint());
   }

   public void setMoveVector(Vec2f moveVector) {
      this.moveVector = moveVector == null ? Vec2f.ZERO : moveVector;
   }

   public void clearMovement(boolean keepJump, boolean keepShift) {
      PlayerInput in = this.keyPresses;
      this.keyPresses = new PlayerInput(false, false, false, false, keepJump && in.jump(), keepShift && in.sneak(), false);
      this.moveVector = Vec2f.ZERO;
   }
   public KeyboardInput getKeyboardInput() {
      return this.keyboardInput;
   }
   public Vec2f getMoveVector() {
      return this.moveVector;
   }
   public PlayerInput getKeyPresses() {
      return this.keyPresses;
   }
}
