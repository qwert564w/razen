package org.ryzen.pve.mining;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

@Environment(EnvType.CLIENT)
public final class MiningSessionSnapshot {
   private UUID playerId;
   private int selectedSlot = -1;
   private float yaw;
   private float pitch;
   private boolean captured;

   public void capture(ClientPlayerEntity player) {
      if (player != null && !this.captured) {
         this.playerId = player.getUuid();
         this.selectedSlot = player.getInventory().getSelectedSlot();
         this.yaw = player.getYaw();
         this.pitch = player.getPitch();
         this.captured = true;
      }
   }

   public void restore(MinecraftClient client) {
      if (this.captured) {
         ClientPlayerEntity player = client == null ? null : client.player;
         if (player != null && player.getUuid().equals(this.playerId)) {
            if (this.selectedSlot >= 0 && this.selectedSlot < 9 && player.getInventory().getSelectedSlot() != this.selectedSlot) {
               player.getInventory().setSelectedSlot(this.selectedSlot);
               player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(this.selectedSlot));
            }

            player.setYaw(this.yaw);
            player.setPitch(this.pitch);
         }

         this.playerId = null;
         this.selectedSlot = -1;
         this.captured = false;
      }
   }
}
