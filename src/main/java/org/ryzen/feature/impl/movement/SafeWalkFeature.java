package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;

@Environment(EnvType.CLIENT)
public final class SafeWalkFeature extends Feature implements MinecraftContext {
   public SafeWalkFeature() {
      super("SafeWalk", "Prevents walking off block edges", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onInput(PlayerInputEvent event) {
      ClientPlayerEntity player = this.player();
      if (player != null && this.level() != null && player.isOnGround()) {
         BlockPos below = player.getBlockPos().down();
         if (this.level().getBlockState(below).getCollisionShape(this.level(), below).isEmpty()) {
            event.setShift(true);
         }
      }
   }
}
