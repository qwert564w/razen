package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.event.events.lifecycle.WorldJoinEvent;
import org.ryzen.event.events.packet.PacketSendEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;

@Environment(EnvType.CLIENT)
public final class AirStuckFeature extends Feature implements PlayerContext {
   public AirStuckFeature() {
      super("AirStuck", "Holds you in place and stops sending movement", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPhase() == PacketSendEvent.Phase.PRE && event.getPacket() instanceof PlayerMoveC2SPacket) {
         event.cancel();
      }
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      ClientPlayerEntity player = event.getPlayer();
      if (player != null) {
         player.setVelocity(Vec3d.ZERO);
         player.fallDistance = 0.0;
      }
   }

   @EventTarget
   public void onWorldJoin(WorldJoinEvent event) {
      this.setEnabled(false);
   }
}
