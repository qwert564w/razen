package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.ryzen.event.events.input.MouseInputEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.InputBindSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.utils.inventory.InventorySwap;

@Environment(EnvType.CLIENT)
public final class AutoSwapFeature extends Feature implements MinecraftContext {
   private static final int OFFHAND_SLOT = 45;
   private static final String ITEM_HEAD = "Head";
   private static final String ITEM_TOTEM = "Totem";
   private static final String ITEM_GAPPLE = "Gapple";
   private static final String ITEM_SHIELD = "Shield";
   private static final String CERB_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjA5NWE3ZmQ5MGRhYTFiYmU3MDY5MDg5NzQwZTA1ZDBiZmM2NjI5NmVlM2M0MGVlNzFhNGUwYTY2MTZiMmJiYyJ9fX0=";
   public final ModeSetting item = this.register(new ModeSetting("Item", "Totem", "Head", "Totem", "Gapple", "Shield"));
   public final ModeSetting swap = this.register(new ModeSetting("Swap", "Head", "Head", "Totem", "Gapple", "Shield"));
   public final InputBindSetting key = this.register(new InputBindSetting("Key", -1));
   public final InputBindSetting cerbKey = this.register(new InputBindSetting("Cerb Key", -1));
   private int pendingSlot = -1;

   public AutoSwapFeature() {
      super("AutoSwap", "Swap items to your offhand on key press", FeatureCategory.COMBAT, -1);
      this.renamedFrom("OffhandSwap");
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 1) {
         if (this.key.matches(event.getKey())) {
            event.cancel();
            this.requestSwap();
         } else if (this.cerbKey.matches(event.getKey())) {
            event.cancel();
            this.requestCerbSwap();
         }
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 1) {
         if (this.key.matchesMouse(event.getButton())) {
            event.cancel();
            this.requestSwap();
         } else if (this.cerbKey.matchesMouse(event.getButton())) {
            event.cancel();
            this.requestCerbSwap();
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.pendingSlot >= 0 && !InventorySwap.isBusy()) {
         ClientPlayerEntity player = event.getClient().player;
         if (player != null && player.currentScreenHandler == player.playerScreenHandler) {
            InventorySwap.equip(this.pendingSlot);
         }

         this.pendingSlot = -1;
      }
   }

   private void requestSwap() {
      ClientPlayerEntity player = this.player();
      if (player != null && mc.currentScreen == null && player.currentScreenHandler == player.playerScreenHandler) {
         Item primary = itemFor(this.item.getValue());
         Item secondary = itemFor(this.swap.getValue());
         Item offhandItem = player.getOffHandStack().getItem();
         Item target = offhandItem != primary ? primary : secondary;
         int slot = findBestSlot(player, target);
         if (slot >= 0) {
            if (InventorySwap.isBusy()) {
               this.pendingSlot = slot;
            } else {
               InventorySwap.equip(slot);
            }
         }
      }
   }

   private void requestCerbSwap() {
      ClientPlayerEntity player = this.player();
      if (player != null && mc.currentScreen == null && player.currentScreenHandler == player.playerScreenHandler) {
         int slot = findCerbHeadSlot(player);
         if (slot >= 0) {
            if (InventorySwap.isBusy()) {
               this.pendingSlot = slot;
            } else {
               InventorySwap.equip(slot);
            }
         }
      }
   }

   private static int findBestSlot(ClientPlayerEntity player, Item target) {
      if (target == Items.AIR) {
         return -1;
      } else {
         int fallback = -1;

         for (int slot = 9; slot < 45; slot++) {
            if (slot != 45) {
               ItemStack stack = player.playerScreenHandler.getSlot(slot).getStack();
               if (stack.isOf(target)) {
                  if (!stack.hasEnchantments()) {
                     return slot;
                  }

                  if (fallback < 0) {
                     fallback = slot;
                  }
               }
            }
         }

         return fallback;
      }
   }

   private static int findCerbHeadSlot(ClientPlayerEntity player) {
      for (int slot = 9; slot < 45; slot++) {
         if (slot != 45) {
            ItemStack stack = player.playerScreenHandler.getSlot(slot).getStack();
            if (isCerbHead(stack)) {
               return slot;
            }
         }
      }

      return -1;
   }

   private static Item itemFor(String mode) {
      return switch (mode) {
         case "Head" -> Items.PLAYER_HEAD;
         case "Totem" -> Items.TOTEM_OF_UNDYING;
         case "Gapple" -> Items.GOLDEN_APPLE;
         case "Shield" -> Items.SHIELD;
         default -> Items.AIR;
      };
   }

   private static boolean isCerbHead(ItemStack stack) {
      if (!stack.isOf(Items.PLAYER_HEAD)) {
         return false;
      } else {
         NbtComponent customData = (NbtComponent)stack.get(DataComponentTypes.CUSTOM_DATA);
         if (customData == null) {
            return false;
         } else {
            NbtCompound nbt = customData.copyNbt();
            return !nbt.contains("SkullOwner")
               ? false
               : nbt.get("SkullOwner")
                  .toString()
                  .contains(
                     "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjA5NWE3ZmQ5MGRhYTFiYmU3MDY5MDg5NzQwZTA1ZDBiZmM2NjI5NmVlM2M0MGVlNzFhNGUwYTY2MTZiMmJiYyJ9fX0="
                  );
         }
      }
   }

   @Override
   protected void onDisable() {
      this.pendingSlot = -1;
   }
}
