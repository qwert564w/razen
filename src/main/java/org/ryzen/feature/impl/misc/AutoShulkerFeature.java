package org.ryzen.feature.impl.misc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.utils.inventory.InventoryUtil;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class AutoShulkerFeature extends Feature implements PlayerContext {
   private static final int WORK_SLOT = 7;
   private static final int SHULKER_SLOTS = 27;
   private static final int FIRST_PLAYER_SLOT = 27;
   private static final int STALL_LIMIT = 25;
   private static final Map<String, Item> COLOURS = new LinkedHashMap<>();
   public final MultiSelectSetting colours = this.register(
      new MultiSelectSetting("Boxes", List.of("Magenta", "Red", "Light Blue", "Pink"), COLOURS.keySet().toArray(String[]::new))
   );
   private boolean opening;
   private int colourIndex;
   private int stallTicks;

   public AutoShulkerFeature() {
      super("AutoShulker", "Packs your inventory into shulker boxes", FeatureCategory.MISC, -1);
   }

   @Override
   protected void onEnable() {
      this.opening = false;
      this.colourIndex = 0;
      this.stallTicks = 0;
   }

   @Override
   protected void onDisable() {
      this.opening = false;
      this.closeScreen();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && client.interactionManager != null) {
         if (!this.opening) {
            this.openNextBox(client, player);
         } else if (client.currentScreen instanceof ShulkerBoxScreen) {
            if (this.isBoxFull(player)) {
               ChatUtil.info("AutoShulker: шалкер заполнен, ищем следующий");
               this.closeScreen();
               this.opening = false;
               this.colourIndex++;
            } else {
               this.fillBox(client, player);
            }
         }
      }
   }

   private void openNextBox(MinecraftClient client, ClientPlayerEntity player) {
      int slot = this.findBoxSlot(player);
      if (slot == -1) {
         ChatUtil.error("AutoShulker: шалкеров в инвентаре нет");
         this.setEnabled(false);
      } else {
         client.interactionManager.clickSlot(player.playerScreenHandler.syncId, slot, 7, SlotActionType.SWAP, player);
         if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(7));
         }

         player.getInventory().setSelectedSlot(7);
         client.interactionManager.interactItem(player, Hand.MAIN_HAND);
         this.opening = true;
         this.stallTicks = 0;
      }
   }

   private void fillBox(MinecraftClient client, ClientPlayerEntity player) {
      ScreenHandler menu = player.currentScreenHandler;
      ItemStack held = player.getInventory().getStack(7);
      if (!held.isEmpty() && !this.isShulkerBox(held)) {
         Slot slot = menu.slots.size() > 34 ? (Slot)menu.slots.get(34) : null;
         if (slot != null && slot.hasStack()) {
            InventoryUtil.quickMoveSlot(slot.id);
         }

         this.stallTicks = 0;
      } else {
         this.stallTicks++;
         if (this.stallTicks > 25) {
            this.closeScreen();
            this.setEnabled(false);
         }
      }
   }

   private boolean isBoxFull(ClientPlayerEntity player) {
      ScreenHandler menu = player.currentScreenHandler;
      if (menu.slots.size() < 27) {
         return false;
      } else {
         for (int index = 0; index < 27; index++) {
            if (((Slot)menu.slots.get(index)).getStack().isEmpty()) {
               return false;
            }
         }

         return true;
      }
   }

   private int findBoxSlot(ClientPlayerEntity player) {
      List<String> names = List.copyOf(COLOURS.keySet());

      for (int index = this.colourIndex; index < names.size(); index++) {
         String name = names.get(index);
         if (this.colours.isSelected(name)) {
            Item item = COLOURS.get(name);
            int slot = InventoryUtil.findPlayerMenuSlot(player, stack -> stack.isOf(item));
            if (slot != -1) {
               this.colourIndex = index;
               return slot;
            }
         }
      }

      return -1;
   }

   private boolean isShulkerBox(ItemStack stack) {
      return COLOURS.values().stream().anyMatch(stack::isOf);
   }

   private void closeScreen() {
      MinecraftClient client = MinecraftClient.getInstance();
      ClientPlayerEntity player = client.player;
      if (player != null && client.currentScreen instanceof ShulkerBoxScreen) {
         if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(player.currentScreenHandler.syncId));
         }

         player.closeHandledScreen();
      }
   }

   static {
      COLOURS.put("Magenta", Items.MAGENTA_SHULKER_BOX);
      COLOURS.put("Red", Items.RED_SHULKER_BOX);
      COLOURS.put("Light Blue", Items.LIGHT_BLUE_SHULKER_BOX);
      COLOURS.put("Pink", Items.PINK_SHULKER_BOX);
      COLOURS.put("White", Items.WHITE_SHULKER_BOX);
      COLOURS.put("Plain", Items.SHULKER_BOX);
   }
}
