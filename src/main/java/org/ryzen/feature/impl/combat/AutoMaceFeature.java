package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.AttackEvent;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;

@Environment(EnvType.CLIENT)
public final class AutoMaceFeature extends Feature {
   private static final int HOTBAR_SIZE = 9;
   private int previousSlot = -1;
   public final BooleanSetting damageBoost = this.register(new BooleanSetting("Damage Boost", true));
   public final BooleanSetting autoSwitch = this.register(new BooleanSetting("Auto Switch Mace", false));

   public AutoMaceFeature() {
      super("Mace Helper", "Automates mace switching for airborne smash attacks", FeatureCategory.COMBAT, -1);
      this.renamedFrom("AutoMace");
   }

   @Override
   protected void onDisable() {
      this.restore(MinecraftClient.getInstance().player);
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.previousSlot = -1;
   }

   @EventTarget
   public void onAttack(AttackEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (this.autoSwitch.getValue() && player != null && client.world != null && !player.isOnGround()) {
         if (!player.getMainHandStack().isOf(Items.MACE)) {
            if (!this.damageBoost.getValue() || !(player.fallDistance <= 1.5)) {
               int maceSlot = findMaceSlot(player);
               if (maceSlot != -1) {
                  this.previousSlot = player.getInventory().getSelectedSlot();
                  select(player, maceSlot);
               }
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && this.previousSlot != -1) {
         this.restore(player);
      }
   }

   private void restore(ClientPlayerEntity player) {
      if (this.previousSlot != -1) {
         int slot = this.previousSlot;
         this.previousSlot = -1;
         if (player != null) {
            select(player, slot);
         }
      }
   }

   private static void select(ClientPlayerEntity player, int slot) {
      player.getInventory().setSelectedSlot(slot);
      if (player.networkHandler != null) {
         player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
      }
   }

   private static int findMaceSlot(ClientPlayerEntity player) {
      for (int slot = 0; slot < 9; slot++) {
         if (player.getInventory().getStack(slot).isOf(Items.MACE)) {
            return slot;
         }
      }

      return -1;
   }
}
