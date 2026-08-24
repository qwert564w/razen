package org.ryzen.feature.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class AutoToolFeature extends Feature {
   private static final int HOTBAR_SIZE = 9;
   private static final int INVENTORY_SIZE = 36;
   public final BooleanSetting useInventory = this.register(new BooleanSetting("Use Inventory", true));
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Silent", "Silent", "Normal"));
   private int originalHotbarSlot = -1;
   private int swappedInventorySlot = -1;
   private int silentServerSlot = -1;
   private int lastClientSlot = -1;

   public AutoToolFeature() {
      super("AutoTool", "Picks the best tool for the targeted block", FeatureCategory.PLAYER, -1);
   }

   @Override
   protected void onDisable() {
      this.restore();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && !player.isCreative() && client.currentScreen == null) {
         this.dropSilentSlotOnManualSwitch(player);
         if (!this.isBreakingBlock(client)) {
            this.restore();
         } else {
            BlockHitResult hit = (BlockHitResult)client.crosshairTarget;
            BlockState state = client.world.getBlockState(hit.getBlockPos());
            int bestSlot = this.findBestToolSlot(player, state);
            if (bestSlot != -1) {
               if (this.originalHotbarSlot == -1) {
                  this.originalHotbarSlot = player.getInventory().getSelectedSlot();
               }

               if (bestSlot < 9) {
                  this.selectHotbarTool(player, bestSlot);
               } else {
                  if (this.swappedInventorySlot != bestSlot) {
                     this.restoreSilentServerSlot(player);
                     this.restoreSwappedItem();
                     InventoryUtil.swapWithHotbar(bestSlot, this.originalHotbarSlot);
                     this.swappedInventorySlot = bestSlot;
                  }

                  player.getInventory().setSelectedSlot(this.originalHotbarSlot);
                  this.lastClientSlot = player.getInventory().getSelectedSlot();
               }
            }
         }
      } else {
         this.restore();
      }
   }

   private void dropSilentSlotOnManualSwitch(ClientPlayerEntity player) {
      int clientSlot = player.getInventory().getSelectedSlot();
      if (this.lastClientSlot != clientSlot) {
         this.lastClientSlot = clientSlot;
         if (this.silentServerSlot != -1) {
            this.silentServerSlot = -1;
            this.originalHotbarSlot = clientSlot;
         }
      }
   }

   public boolean swapForBlock(BlockState state) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (this.isEnabled() && player != null && state != null) {
         int bestSlot = this.findBestToolSlot(player, state);
         if (bestSlot < 0) {
            return false;
         } else {
            if (this.originalHotbarSlot == -1) {
               this.originalHotbarSlot = player.getInventory().getSelectedSlot();
            }

            if (bestSlot < 9) {
               this.selectHotbarTool(player, bestSlot);
            } else if (this.useInventory.getValue()) {
               this.restoreSilentServerSlot(player);
               this.restoreSwappedItem();
               InventoryUtil.swapWithHotbar(bestSlot, this.originalHotbarSlot);
               this.swappedInventorySlot = bestSlot;
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private boolean isBreakingBlock(MinecraftClient client) {
      return client.options.attackKey.isPressed() && client.crosshairTarget != null && client.crosshairTarget.getType() == Type.BLOCK;
   }

   private int findBestToolSlot(ClientPlayerEntity player, BlockState state) {
      int limit = this.useInventory.getValue() ? 36 : 9;
      int bestSlot = -1;
      float bestSpeed = 1.0F;

      for (int slot = 0; slot < limit; slot++) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (!stack.isEmpty()) {
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
               bestSpeed = speed;
               bestSlot = slot;
            }
         }
      }

      return bestSlot;
   }

   private void selectHotbarTool(ClientPlayerEntity player, int slot) {
      if (this.mode.is("Normal")) {
         this.restoreSilentServerSlot(player);
         player.getInventory().setSelectedSlot(slot);
      } else if (slot == player.getInventory().getSelectedSlot()) {
         this.restoreSilentServerSlot(player);
      } else {
         if (this.silentServerSlot != slot) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            this.silentServerSlot = slot;
         }
      }
   }

   private void restore() {
      if (this.originalHotbarSlot != -1) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         this.restoreSwappedItem();
         if (player != null) {
            this.restoreSilentServerSlot(player);
            player.getInventory().setSelectedSlot(this.originalHotbarSlot);
            this.lastClientSlot = player.getInventory().getSelectedSlot();
         }

         this.originalHotbarSlot = -1;
      }
   }

   private void restoreSwappedItem() {
      if (this.swappedInventorySlot != -1) {
         InventoryUtil.swapWithHotbar(this.swappedInventorySlot, this.originalHotbarSlot);
         this.swappedInventorySlot = -1;
      }
   }

   private void restoreSilentServerSlot(ClientPlayerEntity player) {
      if (this.silentServerSlot != -1) {
         player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(player.getInventory().getSelectedSlot()));
         this.silentServerSlot = -1;
      }
   }
}
