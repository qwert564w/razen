package org.ryzen.utils.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

@Environment(EnvType.CLIENT)
public final class AuraAttackController {
   private int restoreSlot = -1;
   private int temporarySlot = -1;
   private int useKeyRestoreTicks = -1;

   public void releaseShieldBeforeAttack(ClientPlayerEntity player) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      if (minecraft.interactionManager != null
         && this.useKeyRestoreTicks < 0
         && player.isUsingItem()
         && player.getBlockingItem() != null
         && minecraft.options.useKey.isPressed()) {
         minecraft.interactionManager.stopUsingItem(player);
         minecraft.options.useKey.setPressed(false);
         this.useKeyRestoreTicks = 0;
      }
   }

   public boolean attack(ClientPlayerEntity player, LivingEntity target, boolean breakShield) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      if (minecraft.interactionManager != null && player != null && target != null && target.isAlive()) {
         AttackWindow.attack(target);
         if (breakShield && target.isBlocking()) {
            this.axeFollowUp(minecraft, player, target);
         }

         return true;
      } else {
         return false;
      }
   }

   public void tick(ClientPlayerEntity player) {
      if (this.useKeyRestoreTicks >= 0 && --this.useKeyRestoreTicks < 0) {
         MinecraftClient.getInstance().options.useKey.setPressed(true);
      }

      if (this.restoreSlot >= 0 && player != null) {
         if (player.getInventory().getSelectedSlot() == this.temporarySlot) {
            player.getInventory().setSelectedSlot(this.restoreSlot);
         }

         this.clearRestore();
      }
   }

   public void reset(ClientPlayerEntity player) {
      if (player != null && this.restoreSlot >= 0 && player.getInventory().getSelectedSlot() == this.temporarySlot) {
         player.getInventory().setSelectedSlot(this.restoreSlot);
      }

      this.clearRestore();
      this.useKeyRestoreTicks = -1;
   }

   private void axeFollowUp(MinecraftClient minecraft, ClientPlayerEntity player, LivingEntity target) {
      int axeSlot = this.findHotbarAxe(player);
      if (axeSlot >= 0) {
         int selectedSlot = player.getInventory().getSelectedSlot();
         if (axeSlot != selectedSlot) {
            player.getInventory().setSelectedSlot(axeSlot);
            this.restoreSlot = selectedSlot;
            this.temporarySlot = axeSlot;
         }

         minecraft.interactionManager.attackEntity(player, target);
         player.swingHand(Hand.MAIN_HAND);
      }
   }

   private int findHotbarAxe(ClientPlayerEntity player) {
      int selectedSlot = player.getInventory().getSelectedSlot();
      if (this.isAxe(player.getInventory().getStack(selectedSlot))) {
         return selectedSlot;
      } else {
         for (int slot = 0; slot < 9; slot++) {
            if (this.isAxe(player.getInventory().getStack(slot))) {
               return slot;
            }
         }

         return -1;
      }
   }

   private boolean isAxe(ItemStack stack) {
      return !stack.isEmpty() && stack.isIn(ItemTags.AXES);
   }

   private void clearRestore() {
      this.restoreSlot = -1;
      this.temporarySlot = -1;
   }
}
