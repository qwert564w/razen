package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;

@Environment(EnvType.CLIENT)
public final class ElytraRecastFeature extends Feature implements PlayerContext {
   public ElytraRecastFeature() {
      super("ElytraRecast", "Holds jump and re-starts the glide whenever the elytra drops", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onPlayerInput(PlayerInputEvent event) {
      ClientPlayerEntity player = this.localPlayer();
      if (player != null && player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         if (player.isOnGround()) {
            event.setJump(true);
         } else if (!player.isGliding()) {
            if (player.networkHandler != null) {
               player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
            }

            player.startGliding();
         }
      }
   }
}
