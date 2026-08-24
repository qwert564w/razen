package org.ryzen.pve.economy;

import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class EconomyInventory {
   private EconomyInventory() {
   }

   public static int count(ClientPlayerEntity player, Item item) {
      if (player != null && item != null) {
         int count = 0;

         for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(item)) {
               count += stack.getCount();
            }
         }

         return count;
      } else {
         return 0;
      }
   }

   public static int findHotbar(ClientPlayerEntity player, Predicate<ItemStack> predicate) {
      if (player == null) {
         return -1;
      } else {
         for (int slot = 0; slot < 9; slot++) {
            if (predicate.test(player.getInventory().getStack(slot))) {
               return slot;
            }
         }

         return -1;
      }
   }

   public static boolean selectHotbar(ClientPlayerEntity player, int slot) {
      if (player != null && slot >= 0 && slot <= 8) {
         if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean moveFirstToSelectedHotbar(ClientPlayerEntity player, Predicate<ItemStack> predicate) {
      if (player != null && player.currentScreenHandler == player.playerScreenHandler) {
         int menuSlot = InventoryUtil.findPlayerMenuSlot(player, predicate);
         if (menuSlot < 0) {
            return false;
         } else {
            int selected = player.getInventory().getSelectedSlot();
            return menuSlot >= 36 && menuSlot <= 44 ? selectHotbar(player, menuSlot - 36) : InventoryUtil.swapWithHotbar(menuSlot, selected);
         }
      } else {
         return false;
      }
   }

   public static boolean hasFreeSlot(ClientPlayerEntity player) {
      if (player == null) {
         return false;
      } else {
         for (int slot = 0; slot < 36; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
               return true;
            }
         }

         return false;
      }
   }
}
