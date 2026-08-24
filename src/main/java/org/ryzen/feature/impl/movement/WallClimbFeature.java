package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class WallClimbFeature extends Feature {
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 0.6, 0.1, 1.0, 0.05, ""));

   public WallClimbFeature() {
      super("WallClimb", "Climb walls like a ladder", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && !player.isSpectator() && player.horizontalCollision) {
         Vec3d movement = player.getVelocity();
         player.setVelocity(movement.x, this.speed.getValue(), movement.z);
      }
   }
}
