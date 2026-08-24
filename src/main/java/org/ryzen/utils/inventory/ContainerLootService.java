package org.ryzen.utils.inventory;

import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

@Environment(EnvType.CLIENT)
public final class ContainerLootService {
   private static final int PLAYER_INVENTORY_SLOTS = 36;

   private ContainerLootService() {
   }

   public static int containerSlotCount(ScreenHandler menu) {
      return Math.max(0, menu.slots.size() - 36);
   }

   public static int findFirst(ScreenHandler menu, Predicate<ItemStack> predicate) {
      Objects.requireNonNull(menu, "menu");
      Objects.requireNonNull(predicate, "predicate");
      int slots = containerSlotCount(menu);

      for (int index = 0; index < slots; index++) {
         Slot slot = menu.getSlot(index);
         if (slot.hasStack() && predicate.test(slot.getStack())) {
            return index;
         }
      }

      return -1;
   }

   public static boolean quickMoveFirst(ScreenHandler menu, Predicate<ItemStack> predicate) {
      int slot = findFirst(menu, predicate);
      if (slot < 0) {
         return false;
      } else {
         InventoryUtil.quickMoveSlot(slot);
         return true;
      }
   }
}
