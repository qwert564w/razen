package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Box;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;

@Environment(EnvType.CLIENT)
public final class ParkourFeature extends Feature {
   private static final double EDGE_PROBE_DEPTH = 0.001;

   public ParkourFeature() {
      super("Parkour", "Automatically jumps at block edges", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && event.getClient().world != null) {
         if (player.isOnGround() && !player.isSneaking() && !player.getAbilities().flying) {
            if (this.isAtEdge(event, player)) {
               player.jump();
            }
         }
      }
   }

   private boolean isAtEdge(GameTickEvent event, ClientPlayerEntity player) {
      Box probe = player.getBoundingBox().offset(0.0, -0.001, 0.0);
      return !event.getClient().world.getBlockCollisions(player, probe).iterator().hasNext();
   }
}
