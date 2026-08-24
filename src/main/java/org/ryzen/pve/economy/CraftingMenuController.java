package org.ryzen.pve.economy;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

@Environment(EnvType.CLIENT)
public final class CraftingMenuController {
   private static final int RESULT_SLOT = 0;
   private static final int GRID_FIRST = 1;
   private static final int GRID_LAST = 9;
   private static final int PLAYER_FIRST = 10;
   private int sourceSlot = -1;
   private int actionCount;
   private int resultWaitTicks;

   public CraftingMenuController.Result tick(MinecraftClient client, CraftingScreenHandler menu, CraftingMenuController.Recipe recipe) {
      if (EconomyMenus.isCurrent(client, menu) && menu.slots.size() > 9 && this.actionCount++ <= 120) {
         int invalidGrid = invalidGridSlot(menu, recipe);
         if (invalidGrid < 0) {
            int missingGrid = missingGridSlot(menu, recipe);
            if (missingGrid >= 0) {
               Item expected = recipe.ingredient(missingGrid - 1);
               ItemStack carried = menu.getCursorStack();
               if (!carried.isEmpty() && !carried.isOf(expected)) {
                  return this.returnCarried(client, menu) ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
               } else if (carried.isEmpty()) {
                  int ingredient = findIngredient(menu, expected);
                  if (ingredient < 0) {
                     return CraftingMenuController.Result.FAILED;
                  } else {
                     this.sourceSlot = ingredient;
                     return EconomyMenus.click(client, menu, ingredient, 0, SlotActionType.PICKUP)
                        ? CraftingMenuController.Result.IN_PROGRESS
                        : CraftingMenuController.Result.FAILED;
                  }
               } else {
                  return EconomyMenus.click(client, menu, missingGrid, 1, SlotActionType.PICKUP)
                     ? CraftingMenuController.Result.IN_PROGRESS
                     : CraftingMenuController.Result.FAILED;
               }
            } else if (!menu.getCursorStack().isEmpty()) {
               return this.returnCarried(client, menu) ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
            } else {
               ItemStack result = menu.getSlot(0).getStack();
               if (result.isOf(recipe.output())) {
                  this.resultWaitTicks = 0;
                  return EconomyMenus.quickMove(client, menu, 0) ? CraftingMenuController.Result.CRAFTED : CraftingMenuController.Result.FAILED;
               } else {
                  return ++this.resultWaitTicks <= 20 ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
               }
            }
         } else if (!menu.getCursorStack().isEmpty()) {
            return this.returnCarried(client, menu) ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
         } else {
            return EconomyMenus.quickMove(client, menu, invalidGrid) ? CraftingMenuController.Result.IN_PROGRESS : CraftingMenuController.Result.FAILED;
         }
      } else {
         return CraftingMenuController.Result.FAILED;
      }
   }

   public boolean cleanup(MinecraftClient client, CraftingScreenHandler menu) {
      return menu != null && !menu.getCursorStack().isEmpty() ? this.returnCarried(client, menu) : true;
   }

   public void reset() {
      this.sourceSlot = -1;
      this.actionCount = 0;
      this.resultWaitTicks = 0;
   }

   private boolean returnCarried(MinecraftClient client, CraftingScreenHandler menu) {
      ItemStack carried = menu.getCursorStack();
      if (carried.isEmpty()) {
         this.sourceSlot = -1;
         return true;
      } else if (canAccept(menu, this.sourceSlot, carried)) {
         boolean clicked = EconomyMenus.click(client, menu, this.sourceSlot, 0, SlotActionType.PICKUP);
         if (clicked) {
            this.sourceSlot = -1;
         }

         return clicked;
      } else {
         for (int slotId = 10; slotId < menu.slots.size(); slotId++) {
            if (canAccept(menu, slotId, carried)) {
               boolean clicked = EconomyMenus.click(client, menu, slotId, 0, SlotActionType.PICKUP);
               if (clicked) {
                  this.sourceSlot = -1;
               }

               return clicked;
            }
         }

         return false;
      }
   }

   private static boolean canAccept(CraftingScreenHandler menu, int slotId, ItemStack carried) {
      if (menu.isValid(slotId) && slotId >= 10) {
         Slot slot = menu.getSlot(slotId);
         ItemStack existing = slot.getStack();
         return slot.canInsert(carried)
            && (existing.isEmpty() || ItemStack.areItemsAndComponentsEqual(existing, carried) && existing.getCount() < existing.getMaxCount());
      } else {
         return false;
      }
   }

   private static int invalidGridSlot(CraftingScreenHandler menu, CraftingMenuController.Recipe recipe) {
      for (int slotId = 1; slotId <= 9; slotId++) {
         ItemStack stack = menu.getSlot(slotId).getStack();
         Item expected = recipe.ingredient(slotId - 1);
         if (!stack.isEmpty() && (!stack.isOf(expected) || stack.getCount() != 1)) {
            return slotId;
         }
      }

      return -1;
   }

   private static int missingGridSlot(CraftingScreenHandler menu, CraftingMenuController.Recipe recipe) {
      for (int slotId = 1; slotId <= 9; slotId++) {
         if (!menu.getSlot(slotId).getStack().isOf(recipe.ingredient(slotId - 1))) {
            return slotId;
         }
      }

      return -1;
   }

   private static int findIngredient(CraftingScreenHandler menu, Item item) {
      for (int slotId = 10; slotId < menu.slots.size(); slotId++) {
         if (menu.getSlot(slotId).getStack().isOf(item)) {
            return slotId;
         }
      }

      return -1;
   }

   @Environment(EnvType.CLIENT)
   public static enum Recipe {
      ENCHANTED_GOLDEN_APPLE(
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.APPLE,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK,
         Items.GOLD_BLOCK
      ),
      GOLD_BLOCK(
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT,
         Items.GOLD_INGOT
      );

      private final Item[] ingredients;

      private Recipe(Item... ingredients) {
         this.ingredients = ingredients;
      }

      Item ingredient(int index) {
         return this.ingredients[index];
      }

      Item output() {
         return this == ENCHANTED_GOLDEN_APPLE ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLD_BLOCK;
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum Result {
      IN_PROGRESS,
      CRAFTED,
      FAILED;
   }
}
