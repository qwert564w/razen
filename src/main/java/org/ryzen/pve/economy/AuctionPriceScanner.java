package org.ryzen.pve.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.OptionalLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.ryzen.utils.inventory.ContainerLootService;

@Environment(EnvType.CLIENT)
public final class AuctionPriceScanner {
   private AuctionPriceScanner() {
   }

   public static OptionalLong competitivePrice(ScreenHandler menu, Item target, String query, int saleCount) {
      if (menu != null && target != null && saleCount > 0) {
         ArrayList<Long> unitPrices = new ArrayList<>();
         int containerSlots = ContainerLootService.containerSlotCount(menu);

         for (int slotId = 0; slotId < containerSlots; slotId++) {
            if (menu.isValid(slotId)) {
               ItemStack stack = menu.getSlot(slotId).getStack();
               if (!stack.isEmpty() && (stack.isOf(target) || EconomyItemText.containsAny(stack, query))) {
                  EconomyItemText.listingUnitPrice(stack).ifPresent(unitPrices::add);
               }
            }
         }

         if (unitPrices.isEmpty()) {
            return OptionalLong.empty();
         } else {
            Collections.sort(unitPrices);
            long unitMedian = unitPrices.get(unitPrices.size() / 2);

            long total;
            try {
               total = Math.multiplyExact(unitMedian, saleCount);
            } catch (ArithmeticException var12) {
               total = 2147483647L;
            }

            long competitive = Math.max(1L, Math.min(2147483647L, total - 1L));
            return OptionalLong.of(competitive);
         }
      } else {
         return OptionalLong.empty();
      }
   }
}
