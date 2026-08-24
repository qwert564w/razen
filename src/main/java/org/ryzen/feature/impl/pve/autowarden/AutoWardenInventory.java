package org.ryzen.feature.impl.pve.autowarden;

import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.ryzen.utils.inventory.ContainerLootService;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
final class AutoWardenInventory {
   private static final int PLAYER_INVENTORY_SIZE = 36;
   private static final Set<Item> RESTOCKABLE_FOOD = Set.of(
      Items.COOKED_BEEF,
      Items.COOKED_PORKCHOP,
      Items.COOKED_CHICKEN,
      Items.COOKED_MUTTON,
      Items.COOKED_RABBIT,
      Items.GOLDEN_CARROT,
      Items.BREAD,
      Items.BAKED_POTATO
   );

   private AutoWardenInventory() {
   }

   static int countInvisibility(ClientPlayerEntity player) {
      return count(player, AutoWardenInventory::isInvisibilityPotion);
   }

   static int countSpeed(ClientPlayerEntity player) {
      return count(player, AutoWardenInventory::isSpeedPotion);
   }

   static int countFood(ClientPlayerEntity player) {
      return count(player, AutoWardenInventory::isRestockableFood);
   }

   static int freeSlots(ClientPlayerEntity player) {
      int free = 0;

      for (int index = 0; index < 36; index++) {
         if (player.getInventory().getStack(index).isEmpty()) {
            free++;
         }
      }

      return free;
   }

   static int countValuables(ClientPlayerEntity player) {
      return count(player, AutoWardenInventory::isValuable);
   }

   static boolean carryingValuables(ClientPlayerEntity player) {
      for (int index = 0; index < 36; index++) {
         if (isValuable(player.getInventory().getStack(index))) {
            return true;
         }
      }

      return false;
   }

   static boolean isInvisibilityPotion(ItemStack stack) {
      return isPotionWith(stack, StatusEffects.INVISIBILITY, false);
   }

   static boolean isSpeedPotion(ItemStack stack) {
      return isPotionWith(stack, StatusEffects.SPEED, true);
   }

   static boolean isRestockableFood(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD) && RESTOCKABLE_FOOD.contains(stack.getItem());
   }

   static boolean isSupply(ItemStack stack) {
      return isInvisibilityPotion(stack) || isSpeedPotion(stack) || isRestockableFood(stack);
   }

   static boolean isValuable(ItemStack stack) {
      return stack != null && !stack.isEmpty() && !stack.isOf(Items.TRIPWIRE_HOOK) && !stack.isOf(Items.GLASS_BOTTLE) && !isSupply(stack);
   }

   static boolean quickMoveFirstContainerItem(ScreenHandler menu, Predicate<ItemStack> predicate) {
      return ContainerLootService.quickMoveFirst(menu, predicate);
   }

   static boolean quickMoveFirstPlayerItem(ScreenHandler menu, Predicate<ItemStack> predicate) {
      int containerSlots = ContainerLootService.containerSlotCount(menu);

      for (int slotId = containerSlots; slotId < menu.slots.size(); slotId++) {
         Slot slot = menu.getSlot(slotId);
         if (slot.hasStack() && predicate.test(slot.getStack())) {
            return InventoryUtil.quickMoveSlot(slotId);
         }
      }

      return false;
   }

   static int findPlayerMenuSlot(ClientPlayerEntity player, Predicate<ItemStack> predicate) {
      return InventoryUtil.findPlayerMenuSlot(player, predicate);
   }

   static int findEmptyHotbarSlot(ClientPlayerEntity player, int excludedSlot) {
      for (int slot = 0; slot < 9; slot++) {
         if (slot != excludedSlot && player.getInventory().getStack(slot).isEmpty()) {
            return slot;
         }
      }

      return -1;
   }

   static int findSafeHotbarSlot(ClientPlayerEntity player, int excludedSlot) {
      int empty = findEmptyHotbarSlot(player, excludedSlot);
      if (empty >= 0) {
         return empty;
      } else {
         for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (slot != excludedSlot && !stack.isOf(Items.TRIPWIRE_HOOK) && !isSupply(stack)) {
               return slot;
            }
         }

         return -1;
      }
   }

   static boolean dropFirstBottle(ClientPlayerEntity player) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.interactionManager != null && player.currentScreenHandler == player.playerScreenHandler) {
         int menuSlot = InventoryUtil.findPlayerMenuSlot(player, stack -> stack.isOf(Items.GLASS_BOTTLE));
         if (menuSlot < 0) {
            return false;
         } else {
            client.interactionManager.clickSlot(player.playerScreenHandler.syncId, menuSlot, 1, SlotActionType.THROW, player);
            return true;
         }
      } else {
         return false;
      }
   }

   private static int count(ClientPlayerEntity player, Predicate<ItemStack> predicate) {
      int count = 0;

      for (int index = 0; index < 36; index++) {
         ItemStack stack = player.getInventory().getStack(index);
         if (predicate.test(stack)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private static boolean isPotionWith(ItemStack stack, RegistryEntry<StatusEffect> wanted, boolean drinkableOnly) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else if (drinkableOnly && !stack.isOf(Items.POTION)) {
         return false;
      } else if (!stack.isOf(Items.POTION) && !stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) {
         return false;
      } else {
         PotionContentsComponent contents = (PotionContentsComponent)stack.get(DataComponentTypes.POTION_CONTENTS);
         if (contents == null) {
            return false;
         } else {
            int effects = 0;
            boolean found = false;

            for (StatusEffectInstance effect : contents.getEffects()) {
               effects++;
               if (effect.getEffectType().equals(wanted)) {
                  found = true;
               }
            }

            return found && (!drinkableOnly || effects >= 1);
         }
      }
   }
}
