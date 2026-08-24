package org.ryzen.utils.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;

@Environment(EnvType.CLIENT)
public final class InventorySwap implements MinecraftContext {
   public static final InventorySwap INSTANCE = new InventorySwap();
   private static final int HOTBAR_START = 36;
   private static final int HOTBAR_END = 45;
   private static final int OFFHAND_BUTTON = 40;
   private InventorySwap.State state = InventorySwap.State.IDLE;
   private int sourceSlot = -1;
   private int hotbarButton = 40;
   private boolean useAndReturn;
   private boolean waitForUseCompletion;
   private boolean immediateReturn;
   private boolean directUse;
   private SlotActionType clickInput = SlotActionType.SWAP;
   private boolean syntheticUseHeld;
   private int useWaitTicks;
   private static final int MAX_USE_WAIT_TICKS = 200;

   private InventorySwap() {
   }

   public static void equip(int containerSlot) {
      ClientPlayerEntity player = INSTANCE.player();
      if (player != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && player.currentScreenHandler == player.playerScreenHandler) {
         if (containerSlot >= 36 && containerSlot < 45) {
            mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, containerSlot, 40, SlotActionType.SWAP, player);
         } else {
            INSTANCE.begin(containerSlot, 40, false, false, false, false, SlotActionType.SWAP);
         }
      }
   }

   public static void moveToHotbar(int containerSlot, int hotbarSlot) {
      if (INSTANCE.player() != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && hotbarSlot >= 0
         && hotbarSlot <= 8) {
         INSTANCE.begin(containerSlot, hotbarSlot, false, false, false, false, SlotActionType.SWAP);
      }
   }

   public static void useFromSlot(int containerSlot) {
      useFromSlot(containerSlot, false);
   }

   public static void useFromSlot(int containerSlot, boolean waitForUseCompletion) {
      useFromSlot(containerSlot, waitForUseCompletion, false);
   }

   public static void useFromSlot(int containerSlot, boolean waitForUseCompletion, boolean immediateReturn) {
      ClientPlayerEntity player = INSTANCE.player();
      if (player != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && player.currentScreenHandler == player.playerScreenHandler) {
         INSTANCE.begin(containerSlot, player.getInventory().getSelectedSlot(), true, waitForUseCompletion, immediateReturn, false, SlotActionType.SWAP);
      }
   }

   public static void useSelected(boolean waitForUseCompletion) {
      ClientPlayerEntity player = INSTANCE.player();
      if (player != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && player.currentScreenHandler == player.playerScreenHandler) {
         INSTANCE.begin(-1, player.getInventory().getSelectedSlot(), true, waitForUseCompletion, false, true, SlotActionType.SWAP);
      }
   }

   public static boolean dropStack(int containerSlot) {
      ClientPlayerEntity player = INSTANCE.player();
      if (player != null
         && INSTANCE.state == InventorySwap.State.IDLE
         && !DropAllInventoryController.blocksInventoryOperations()
         && player.currentScreenHandler == player.playerScreenHandler
         && player.playerScreenHandler.isValid(containerSlot)
         && !player.playerScreenHandler.getSlot(containerSlot).getStack().isEmpty()) {
         INSTANCE.begin(containerSlot, 1, false, false, false, false, SlotActionType.THROW);
         return true;
      } else {
         return false;
      }
   }

   public static boolean isBusy() {
      return INSTANCE.state != InventorySwap.State.IDLE;
   }

   public static boolean shouldStopMovement() {
      return INSTANCE.state != InventorySwap.State.IDLE;
   }

   public static void abort() {
      INSTANCE.finish();
   }

   private void begin(
      int containerSlot,
      int hotbarButton,
      boolean useAndReturn,
      boolean waitForUseCompletion,
      boolean immediateReturn,
      boolean directUse,
      SlotActionType clickInput
   ) {
      this.sourceSlot = containerSlot;
      this.hotbarButton = hotbarButton;
      this.useAndReturn = useAndReturn;
      this.waitForUseCompletion = waitForUseCompletion;
      this.immediateReturn = immediateReturn;
      this.directUse = directUse;
      this.clickInput = clickInput == null ? SlotActionType.SWAP : clickInput;
      this.syntheticUseHeld = false;
      this.useWaitTicks = 0;
      this.state = InventorySwap.State.PREPARING;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.state != InventorySwap.State.IDLE) {
         ClientPlayerEntity player = this.player();
         if (player != null
            && (this.directUse || player.playerScreenHandler.isValid(this.sourceSlot))
            && player.currentScreenHandler == player.playerScreenHandler) {
            switch (this.state) {
               case PREPARING:
                  this.state = InventorySwap.State.MOVING;
                  break;
               case MOVING:
                  if (!this.directUse) {
                     if (player.playerScreenHandler.getSlot(this.sourceSlot).getStack().isEmpty()) {
                        this.finish();
                        return;
                     }

                     this.click(player);
                  }

                  this.state = this.useAndReturn ? InventorySwap.State.USING : InventorySwap.State.SETTLING;
                  break;
               case USING:
                  int selected = player.getInventory().getSelectedSlot();
                  if (selected != this.hotbarButton) {
                     player.getInventory().setSelectedSlot(this.hotbarButton);
                  }

                  mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
                  player.swingHand(Hand.MAIN_HAND);
                  if (selected != this.hotbarButton) {
                     player.getInventory().setSelectedSlot(selected);
                  }

                  if (this.waitForUseCompletion && player.isUsingItem()) {
                     mc.options.useKey.setPressed(true);
                     this.syntheticUseHeld = true;
                     this.state = InventorySwap.State.WAITING_FOR_USE;
                  } else if (this.immediateReturn && !this.directUse) {
                     this.click(player);
                     this.state = InventorySwap.State.SETTLING;
                  } else {
                     this.state = InventorySwap.State.RETURNING;
                  }
                  break;
               case WAITING_FOR_USE:
                  this.useWaitTicks++;
                  if (!player.isUsingItem() || this.useWaitTicks >= 200) {
                     this.releaseSyntheticUse();
                     this.state = InventorySwap.State.RETURNING;
                  }
                  break;
               case RETURNING:
                  if (!this.directUse) {
                     this.click(player);
                  }

                  this.state = InventorySwap.State.SETTLING;
                  break;
               case SETTLING:
                  this.finish();
                  break;
               default:
                  this.finish();
            }
         } else {
            this.finish();
         }
      }
   }

   private void click(ClientPlayerEntity player) {
      mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, this.sourceSlot, this.hotbarButton, this.clickInput, player);
   }

   private void finish() {
      this.releaseSyntheticUse();
      this.state = InventorySwap.State.IDLE;
      this.sourceSlot = -1;
      this.hotbarButton = 40;
      this.useAndReturn = false;
      this.waitForUseCompletion = false;
      this.immediateReturn = false;
      this.directUse = false;
      this.clickInput = SlotActionType.SWAP;
      this.useWaitTicks = 0;
   }

   private void releaseSyntheticUse() {
      if (this.syntheticUseHeld) {
         mc.options.useKey.setPressed(false);
         this.syntheticUseHeld = false;
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum State {
      IDLE,
      PREPARING,
      MOVING,
      USING,
      WAITING_FOR_USE,
      RETURNING,
      SETTLING;
   }
}
