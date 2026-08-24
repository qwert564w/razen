package org.ryzen.mixin.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.ryzen.event.EventManager;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.feature.impl.movement.InventoryMoveFeature;
import org.ryzen.feature.impl.movement.SprintFeature;
import org.ryzen.utils.combat.SprintManager;
import org.ryzen.utils.inventory.InventorySwap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({KeyboardInput.class})
public abstract class KeyboardInputMixin extends Input {
   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void onPlayerInputTick(CallbackInfo ci) {
      PlayerInput screenInput = InventoryMoveFeature.screenInput();
      if (screenInput != null) {
         this.playerInput = screenInput;
         this.movementVector = new Vec2f(impulse(screenInput.left(), screenInput.right()), impulse(screenInput.forward(), screenInput.backward())).normalize();
      }

      if (!this.playerInput.sprint() && SprintFeature.shouldForceSprintKey()) {
         this.playerInput = new PlayerInput(
            this.playerInput.forward(),
            this.playerInput.backward(),
            this.playerInput.left(),
            this.playerInput.right(),
            this.playerInput.jump(),
            this.playerInput.sneak(),
            true
         );
      }

      if (SprintManager.shouldFreezeMovementInput()) {
         this.movementVector = Vec2f.ZERO;
         this.playerInput = new PlayerInput(false, false, false, false, this.playerInput.jump(), this.playerInput.sneak(), false);
      }

      if (InventorySwap.shouldStopMovement() || InventoryMoveFeature.shouldStopMovement()) {
         this.movementVector = Vec2f.ZERO;
         this.playerInput = new PlayerInput(false, false, false, false, false, this.playerInput.sneak(), false);
      }

      if (EventManager.hasListeners(PlayerInputEvent.class)) {
         PlayerInputEvent event = EventManager.call(new PlayerInputEvent((KeyboardInput)(Object)this, this.playerInput, this.getMovementInput()));
         this.playerInput = event.getKeyPresses();
         this.movementVector = event.getMoveVector();
      }
   }

   private static float impulse(boolean positive, boolean negative) {
      if (positive == negative) {
         return 0.0F;
      } else {
         return positive ? 1.0F : -1.0F;
      }
   }
}
