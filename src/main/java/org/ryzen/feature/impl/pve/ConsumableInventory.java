package org.ryzen.feature.impl.pve;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

@Environment(EnvType.CLIENT)
final class ConsumableInventory {
   private ConsumableInventory() {
   }

   static List<ConsumableSelector.Candidate<ItemStack>> collect(ClientPlayerEntity player, Function<ItemStack, ConsumableInventory.Profile> classifier) {
      List<ConsumableSelector.Candidate<ItemStack>> candidates = new ArrayList<>();
      int selectedHotbar = player.getInventory().getSelectedSlot();
      int selectedContainer = 36 + selectedHotbar;
      add(candidates, player.getOffHandStack(), ConsumableSelector.Location.OFF_HAND, 45, classifier);
      add(candidates, player.getMainHandStack(), ConsumableSelector.Location.MAIN_HAND, selectedContainer, classifier);

      for (int slot = 36; slot < 45; slot++) {
         if (slot != selectedContainer) {
            add(candidates, player.playerScreenHandler.getSlot(slot).getStack(), ConsumableSelector.Location.HOTBAR, slot, classifier);
         }
      }

      for (int slotx = 9; slotx < 36; slotx++) {
         add(candidates, player.playerScreenHandler.getSlot(slotx).getStack(), ConsumableSelector.Location.INVENTORY, slotx, classifier);
      }

      return List.copyOf(candidates);
   }

   private static void add(
      List<ConsumableSelector.Candidate<ItemStack>> candidates,
      ItemStack stack,
      ConsumableSelector.Location location,
      int containerSlot,
      Function<ItemStack, ConsumableInventory.Profile> classifier
   ) {
      if (!stack.isEmpty()) {
         ConsumableInventory.Profile profile = classifier.apply(stack);
         if (profile != null) {
            candidates.add(
               new ConsumableSelector.Candidate<>(
                  stack.copy(), profile.kind(), location, containerSlot, profile.nutrition(), profile.saturation(), profile.safe()
               )
            );
         }
      }
   }

   @Environment(EnvType.CLIENT)
   static record Profile(ConsumableSelector.Kind kind, int nutrition, float saturation, boolean safe) {
   }
}
