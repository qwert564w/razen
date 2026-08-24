package org.ryzen.feature.impl.player;

import java.util.EnumSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.ryzen.event.events.input.MouseInputEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.InputBindSetting;
import org.ryzen.pve.AutomationOwner;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.utils.inventory.InventorySwap;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class ClickPearlFeature extends Feature implements AutomationOwner {
   private static final int HOTBAR_SIZE = 9;
   public final InputBindSetting key = this.register(new InputBindSetting("Key", -1));
   private boolean throwQueued;
   private boolean releasePending;
   private ClickPearlFeature.Stage stage = ClickPearlFeature.Stage.IDLE;
   private int restoreSlot = -1;

   public ClickPearlFeature() {
      super("ClickPearl", "Quickly throws an ender pearl", FeatureCategory.PLAYER, -1);
   }

   @Override
   protected void onDisable() {
      this.throwQueued = false;
      this.releasePending = false;
      this.finishHotbarThrow(MinecraftClient.getInstance().player);
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 1 && this.key.matches(event.getKey())) {
         this.queueThrow();
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 1 && this.key.matchesMouse(event.getButton()) && this.queueThrow()) {
         event.cancel();
      }
   }

   private boolean queueThrow() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.currentScreen == null
         && client.player != null
         && PveAutomationCoordinator.INSTANCE
            .acquire(this, AutomationPriority.EMERGENCY, EnumSet.of(AutomationResource.INVENTORY, AutomationResource.ROTATION))) {
         this.throwQueued = true;
         return true;
      } else {
         return false;
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.stage != ClickPearlFeature.Stage.IDLE) {
         this.advanceHotbarThrow(event.getClient());
      } else {
         if (this.releasePending && !InventorySwap.isBusy()) {
            this.releasePending = false;
            PveAutomationCoordinator.INSTANCE.release(this);
         }

         if (this.throwQueued) {
            this.throwQueued = false;
            boolean asyncSwap = false;

            try {
               MinecraftClient client = event.getClient();
               ClientPlayerEntity player = client.player;
               if (player == null || client.interactionManager == null) {
                  return;
               }

               if (player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
                  this.throwFromMainHand(client, player);
                  return;
               }

               int hotbarSlot = this.findPearlHotbarSlot(player);
               if (hotbarSlot == -1) {
                  int containerSlot = this.findPearlContainerSlot(player);
                  if (containerSlot != -1) {
                     InventorySwap.useFromSlot(containerSlot);
                     this.releasePending = true;
                     asyncSwap = true;
                  }

                  return;
               }

               this.restoreSlot = player.getInventory().getSelectedSlot();
               player.getInventory().setSelectedSlot(hotbarSlot);
               this.stage = ClickPearlFeature.Stage.THROW;
               asyncSwap = true;
            } finally {
               if (!asyncSwap) {
                  PveAutomationCoordinator.INSTANCE.release(this);
               }
            }
         }
      }
   }

   private void advanceHotbarThrow(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.interactionManager == null) {
         this.finishHotbarThrow(player);
      } else if (this.stage == ClickPearlFeature.Stage.THROW) {
         if (player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
            this.throwFromMainHand(client, player);
         }

         this.stage = ClickPearlFeature.Stage.RESTORE;
      } else {
         this.finishHotbarThrow(player);
      }
   }

   private void finishHotbarThrow(ClientPlayerEntity player) {
      if (player != null && this.restoreSlot != -1) {
         player.getInventory().setSelectedSlot(this.restoreSlot);
         player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(this.restoreSlot));
      }

      this.restoreSlot = -1;
      this.stage = ClickPearlFeature.Stage.IDLE;
      PveAutomationCoordinator.INSTANCE.release(this);
   }

   private void throwFromMainHand(MinecraftClient client, ClientPlayerEntity player) {
      client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      player.swingHand(Hand.MAIN_HAND);
   }

   private int findPearlHotbarSlot(ClientPlayerEntity player) {
      for (int slot = 0; slot < 9; slot++) {
         if (player.getInventory().getStack(slot).isOf(Items.ENDER_PEARL)) {
            return slot;
         }
      }

      return -1;
   }

   private int findPearlContainerSlot(ClientPlayerEntity player) {
      return InventoryUtil.findInventorySlot(player, stack -> stack.isOf(Items.ENDER_PEARL));
   }

   @Environment(EnvType.CLIENT)
   private static enum Stage {
      IDLE,
      THROW,
      RESTORE;
   }
}
