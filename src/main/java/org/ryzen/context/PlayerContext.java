package org.ryzen.context;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public interface PlayerContext extends WorldContext {
   double MOVEMENT_EPSILON = 1.0E-4;

   default ClientPlayerEntity localPlayer() {
      return this.player();
   }

   default PlayerInventory inventory() {
      ClientPlayerEntity player = this.localPlayer();
      return player != null ? player.getInventory() : null;
   }

   default boolean hasPlayer() {
      return this.localPlayer() != null;
   }

   default boolean isMoving() {
      ClientPlayerEntity player = this.localPlayer();
      if (player == null) {
         return false;
      } else {
         Vec3d movement = player.getVelocity();
         return movement.horizontalLengthSquared() > 1.0E-4;
      }
   }

   default boolean hasMovementInput() {
      ClientPlayerEntity player = this.localPlayer();
      return player != null
         && (player.input.playerInput.forward() || player.input.playerInput.backward() || player.input.playerInput.left() || player.input.playerInput.right());
   }
}
