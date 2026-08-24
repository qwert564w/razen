package org.ryzen.feature.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class NoPearlTeleportFeature extends Feature implements PlayerContext {
   public final BooleanSetting checkDistance = this.register(new BooleanSetting("Check Distance", true));
   public final NumberSetting maxDistance = this.register(
      new NumberSetting("Max Distance", 64.0, 8.0, 256.0, 1.0, " blocks").visibleWhen(this.checkDistance::getValue)
   );

   public NoPearlTeleportFeature() {
      super("NoPearlTeleport", "Cancels the teleport an ender pearl would do", FeatureCategory.PLAYER, -1);
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
         ClientPlayerEntity player = this.localPlayer();
         if (player != null && player.networkHandler != null) {
            if (this.checkDistance.getValue()) {
               Vec3d destination = packet.change().position();
               if (player.getEntityPos().distanceTo(destination) > this.maxDistance.getValue()) {
                  return;
               }
            }

            event.cancel();
            player.networkHandler.sendPacket(new TeleportConfirmC2SPacket(packet.teleportId()));
         }
      }
   }
}
