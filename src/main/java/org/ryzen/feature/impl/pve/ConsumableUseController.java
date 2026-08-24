package org.ryzen.feature.impl.pve;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseRemainderComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.ryzen.utils.inventory.DropAllInventoryController;
import org.ryzen.utils.inventory.InventorySwap;

@Environment(EnvType.CLIENT)
final class ConsumableUseController {
   private static final int MAX_USE_TICKS = 200;
   private ConsumableUseController.Mode mode = ConsumableUseController.Mode.IDLE;
   private Hand directHand = Hand.MAIN_HAND;
   private ItemStack expectedStack = ItemStack.EMPTY;
   private ItemStack expectedRemainder = ItemStack.EMPTY;
   private boolean waitForCompletion;
   private boolean useIssued;
   private boolean sawExpectedUse;
   private boolean syntheticUseHeld;
   private int directDelayTicks;
   private int activeTicks;
   private int sourceSlot = -1;
   private int destinationSlot = -1;
   private int destinationHotbarSlot = -1;
   private ItemStack sourceBefore = ItemStack.EMPTY;
   private ItemStack destinationBefore = ItemStack.EMPTY;

   boolean start(
      MinecraftClient client, ClientPlayerEntity player, ConsumableSelector.Candidate<ItemStack> candidate, boolean waitForCompletion, int directDelayTicks
   ) {
      if (!this.isActive()
         && client.interactionManager != null
         && player != null
         && player.currentScreenHandler == player.playerScreenHandler
         && player.playerScreenHandler.getCursorStack().isEmpty()
         && !DropAllInventoryController.blocksInventoryOperations()
         && !InventorySwap.isBusy()) {
         this.expectedStack = candidate.value().copy();
         UseRemainderComponent useRemainder = (UseRemainderComponent)this.expectedStack.get(DataComponentTypes.USE_REMAINDER);
         this.expectedRemainder = useRemainder == null ? ItemStack.EMPTY : useRemainder.convertInto().copy();
         this.waitForCompletion = waitForCompletion;
         this.directDelayTicks = Math.max(0, directDelayTicks);
         this.activeTicks = 0;
         this.useIssued = false;
         this.sawExpectedUse = false;
         if (candidate.location() == ConsumableSelector.Location.OFF_HAND) {
            this.mode = ConsumableUseController.Mode.DIRECT_HAND;
            this.directHand = Hand.OFF_HAND;
            if (this.directDelayTicks == 0) {
               this.issueDirectUse(client, player);
            }

            return true;
         } else {
            this.mode = ConsumableUseController.Mode.INVENTORY_SWAP;
            this.destinationHotbarSlot = player.getInventory().getSelectedSlot();
            this.destinationSlot = 36 + this.destinationHotbarSlot;
            if (candidate.location() == ConsumableSelector.Location.MAIN_HAND) {
               this.sourceSlot = -1;
               InventorySwap.useSelected(waitForCompletion);
            } else {
               this.sourceSlot = candidate.containerSlot();
               if (!player.playerScreenHandler.isValid(this.sourceSlot) || this.sourceSlot == this.destinationSlot) {
                  this.clear();
                  return false;
               }

               this.sourceBefore = player.playerScreenHandler.getSlot(this.sourceSlot).getStack().copy();
               this.destinationBefore = player.playerScreenHandler.getSlot(this.destinationSlot).getStack().copy();
               InventorySwap.useFromSlot(this.sourceSlot, waitForCompletion);
            }

            if (!InventorySwap.isBusy()) {
               this.clear();
               return false;
            } else {
               return true;
            }
         }
      } else {
         return false;
      }
   }

   boolean tick(MinecraftClient client, ClientPlayerEntity player) {
      if (!this.isActive()) {
         return false;
      } else if (player != null && client.interactionManager != null) {
         this.activeTicks++;
         if (this.activeTicks >= 200) {
            this.cancel(client, player);
            return false;
         } else if (this.mode == ConsumableUseController.Mode.DIRECT_HAND) {
            return this.tickDirect(client, player);
         } else {
            if (this.isExpectedUse(player)) {
               this.sawExpectedUse = true;
            }

            if (!InventorySwap.isBusy()) {
               if (this.restoreIfNeeded(client, player)) {
                  this.clear();
                  return false;
               } else {
                  return true;
               }
            } else if (this.isExpectedUse(player) || !this.positionsRestored(player) || !this.sawExpectedUse && this.activeTicks < 5) {
               return true;
            } else {
               this.clear();
               return false;
            }
         }
      } else {
         this.cancel(client, player);
         return false;
      }
   }

   boolean isActive() {
      return this.mode != ConsumableUseController.Mode.IDLE;
   }

   void cancel(MinecraftClient client, ClientPlayerEntity player) {
      if (this.isActive()) {
         if (this.mode == ConsumableUseController.Mode.DIRECT_HAND) {
            this.releaseExpectedUse(client, player);
            this.releaseSyntheticUse(client);
            this.clear();
         } else {
            boolean alreadyReturned = player != null
               && !this.isExpectedUse(player)
               && this.positionsRestored(player)
               && (this.sawExpectedUse || this.activeTicks >= 5);
            if (!alreadyReturned && InventorySwap.isBusy()) {
               InventorySwap.abort();
            }

            this.releaseExpectedUse(client, player);
            this.restoreIfNeeded(client, player);
            this.clear();
         }
      }
   }

   private boolean tickDirect(MinecraftClient client, ClientPlayerEntity player) {
      if (!this.useIssued) {
         if (this.directDelayTicks > 0) {
            this.directDelayTicks--;
         }

         if (this.directDelayTicks == 0) {
            this.issueDirectUse(client, player);
         }
      }

      if (!this.useIssued) {
         return true;
      } else if (this.waitForCompletion && this.isExpectedUse(player)) {
         this.sawExpectedUse = true;
         client.options.useKey.setPressed(true);
         this.syntheticUseHeld = true;
         return true;
      } else {
         this.releaseSyntheticUse(client);
         this.clear();
         return false;
      }
   }

   private void issueDirectUse(MinecraftClient client, ClientPlayerEntity player) {
      ItemStack held = player.getStackInHand(this.directHand);
      if (!sameItemAndComponents(held, this.expectedStack)) {
         this.useIssued = true;
      } else {
         client.interactionManager.interactItem(player, this.directHand);
         player.swingHand(this.directHand);
         this.useIssued = true;
         if (this.waitForCompletion && this.isExpectedUse(player)) {
            this.sawExpectedUse = true;
            client.options.useKey.setPressed(true);
            this.syntheticUseHeld = true;
         }
      }
   }

   private void releaseExpectedUse(MinecraftClient client, ClientPlayerEntity player) {
      if (client.interactionManager != null && player != null && this.isExpectedUse(player)) {
         client.interactionManager.stopUsingItem(player);
      }
   }

   private void releaseSyntheticUse(MinecraftClient client) {
      if (this.syntheticUseHeld) {
         client.options.useKey.setPressed(false);
         this.syntheticUseHeld = false;
      }
   }

   private boolean restoreIfNeeded(MinecraftClient client, ClientPlayerEntity player) {
      if (this.sourceSlot < 0) {
         return true;
      } else if (player == null
         || client.interactionManager == null
         || player.currentScreenHandler != player.playerScreenHandler
         || !player.playerScreenHandler.isValid(this.sourceSlot)
         || !player.playerScreenHandler.isValid(this.destinationSlot)) {
         return false;
      } else if (this.positionsRestored(player)) {
         return true;
      } else {
         ItemStack sourceNow = player.playerScreenHandler.getSlot(this.sourceSlot).getStack();
         ItemStack destinationNow = player.playerScreenHandler.getSlot(this.destinationSlot).getStack();
         if (ItemStack.areEqual(sourceNow, this.destinationBefore) && this.matchesConsumedRemainder(destinationNow, this.sourceBefore)) {
            client.interactionManager.clickSlot(player.playerScreenHandler.syncId, this.sourceSlot, this.destinationHotbarSlot, SlotActionType.SWAP, player);
            return this.positionsRestored(player);
         } else {
            return false;
         }
      }
   }

   private boolean positionsRestored(ClientPlayerEntity player) {
      if (this.sourceSlot < 0) {
         return true;
      } else if (player != null && player.playerScreenHandler.isValid(this.sourceSlot) && player.playerScreenHandler.isValid(this.destinationSlot)) {
         ItemStack sourceNow = player.playerScreenHandler.getSlot(this.sourceSlot).getStack();
         ItemStack destinationNow = player.playerScreenHandler.getSlot(this.destinationSlot).getStack();
         return this.matchesConsumedRemainder(sourceNow, this.sourceBefore) && ItemStack.areEqual(destinationNow, this.destinationBefore);
      } else {
         return false;
      }
   }

   private boolean isExpectedUse(ClientPlayerEntity player) {
      return player != null && player.isUsingItem() && sameItemAndComponents(player.getActiveItem(), this.expectedStack);
   }

   private boolean matchesConsumedRemainder(ItemStack current, ItemStack original) {
      if (current.isEmpty()) {
         return original.getCount() == 1 && this.expectedRemainder.isEmpty();
      } else {
         return sameItemAndComponents(current, original)
            ? current.getCount() > 0 && current.getCount() <= original.getCount()
            : original.getCount() == 1 && sameItemAndComponents(current, this.expectedRemainder);
      }
   }

   private static boolean sameItemAndComponents(ItemStack first, ItemStack second) {
      return !first.isEmpty() && !second.isEmpty() && ItemStack.areItemsAndComponentsEqual(first, second);
   }

   private void clear() {
      this.mode = ConsumableUseController.Mode.IDLE;
      this.directHand = Hand.MAIN_HAND;
      this.expectedStack = ItemStack.EMPTY;
      this.expectedRemainder = ItemStack.EMPTY;
      this.waitForCompletion = false;
      this.useIssued = false;
      this.sawExpectedUse = false;
      this.syntheticUseHeld = false;
      this.directDelayTicks = 0;
      this.activeTicks = 0;
      this.sourceSlot = -1;
      this.destinationSlot = -1;
      this.destinationHotbarSlot = -1;
      this.sourceBefore = ItemStack.EMPTY;
      this.destinationBefore = ItemStack.EMPTY;
   }

   @Environment(EnvType.CLIENT)
   private static enum Mode {
      IDLE,
      DIRECT_HAND,
      INVENTORY_SWAP;
   }
}
