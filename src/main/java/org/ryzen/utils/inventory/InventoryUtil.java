package org.ryzen.utils.inventory;

import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ClickType;
import org.ryzen.context.InventoryContext;

@Environment(EnvType.CLIENT)
public final class InventoryUtil implements InventoryContext {
   private static final InventoryUtil CONTEXT = new InventoryUtil();
   private static final int INVENTORY_SLOTS_START = 9;

   private InventoryUtil() {
   }

   public static int findPlayerMenuSlot(ClientPlayerEntity player, Predicate<ItemStack> predicate) {
      for (int slot = 36; slot < 45; slot++) {
         if (predicate.test(player.playerScreenHandler.getSlot(slot).getStack())) {
            return slot;
         }
      }

      for (int slotx = 9; slotx < 36; slotx++) {
         if (predicate.test(player.playerScreenHandler.getSlot(slotx).getStack())) {
            return slotx;
         }
      }

      return -1;
   }

   public static int findInventorySlot(ClientPlayerEntity player, Predicate<ItemStack> predicate) {
      for (int slot = 9; slot < 36; slot++) {
         if (predicate.test(player.playerScreenHandler.getSlot(slot).getStack())) {
            return slot;
         }
      }

      return -1;
   }

   public static Screen getCurrentScreen() {
      return CONTEXT.screen();
   }

   public static boolean isContainerScreenOpen() {
      return CONTEXT.hasContainerScreen();
   }

   public static HandledScreen<?> getContainerScreen() {
      return CONTEXT.containerScreen();
   }

   public static ScreenHandler getOpenMenu() {
      return CONTEXT.menu();
   }

   public static boolean hasOpenMenu() {
      return CONTEXT.hasMenu();
   }

   public static boolean clickSlot(int slotId, ClickType clickAction, SlotActionType input) {
      return clickSlot(slotId, clickButton(clickAction), input);
   }

   public static boolean clickSlot(int slotId, int button, SlotActionType input) {
      ScreenHandler menu = getOpenMenu();
      return menu != null && clickMenu(menu, slotId, button, input);
   }

   public static boolean leftClickSlot(int slotId) {
      return clickSlot(slotId, ClickType.LEFT, SlotActionType.PICKUP);
   }

   public static boolean rightClickSlot(int slotId) {
      return clickSlot(slotId, ClickType.RIGHT, SlotActionType.PICKUP);
   }

   public static boolean quickMoveSlot(int slotId) {
      return clickSlot(slotId, 0, SlotActionType.QUICK_MOVE);
   }

   public static boolean swapWithHotbar(int slotId, int hotbarSlot) {
      return hotbarSlot >= 0 && hotbarSlot <= 8 ? clickSlot(slotId, hotbarSlot, SlotActionType.SWAP) : false;
   }

   public static boolean dropOne(int slotId) {
      return clickSlot(slotId, 0, SlotActionType.THROW);
   }

   public static boolean dropStack(int slotId) {
      return clickSlot(slotId, 1, SlotActionType.THROW);
   }

   public static boolean dropPlayerStack(ClientPlayerEntity player, int slotId) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.interactionManager != null
         && player != null
         && player.currentScreenHandler == player.playerScreenHandler
         && player.playerScreenHandler.isValid(slotId)
         && !player.playerScreenHandler.getSlot(slotId).getStack().isEmpty()) {
         client.interactionManager.clickSlot(player.playerScreenHandler.syncId, slotId, 1, SlotActionType.THROW, player);
         return true;
      } else {
         return false;
      }
   }

   public static boolean pickupAll(int slotId) {
      return clickSlot(slotId, 0, SlotActionType.PICKUP_ALL);
   }

   public static boolean pickupOutside() {
      ScreenHandler menu = getOpenMenu();
      return menu != null && clickMenu(menu, -999, 0, SlotActionType.PICKUP);
   }

   public static boolean moveStack(int fromSlotId, int toSlotId) {
      ScreenHandler menu = getOpenMenu();
      if (menu == null || fromSlotId == toSlotId) {
         return false;
      } else if (!menu.isValid(fromSlotId) || !menu.isValid(toSlotId)) {
         return false;
      } else if (!menu.getCursorStack().isEmpty()) {
         return false;
      } else {
         Slot fromSlot = menu.getSlot(fromSlotId);
         if (fromSlot == null || !fromSlot.hasStack()) {
            return false;
         } else if (!clickMenu(menu, fromSlotId, 0, SlotActionType.PICKUP)) {
            return false;
         } else if (menu.getCursorStack().isEmpty()) {
            return false;
         } else if (!clickMenu(menu, toSlotId, 0, SlotActionType.PICKUP)) {
            clickMenu(menu, fromSlotId, 0, SlotActionType.PICKUP);
            return false;
         } else {
            if (!menu.getCursorStack().isEmpty()) {
               clickMenu(menu, fromSlotId, 0, SlotActionType.PICKUP);
            }

            return true;
         }
      }
   }

   public static boolean hasCarriedItem() {
      ScreenHandler menu = getOpenMenu();
      return menu != null && !menu.getCursorStack().isEmpty();
   }

   public static ItemStack getCarriedItem() {
      ScreenHandler menu = getOpenMenu();
      return menu != null ? menu.getCursorStack() : ItemStack.EMPTY;
   }

   public static ItemStack getSlotItem(int slotId) {
      ScreenHandler menu = getOpenMenu();
      return menu != null && menu.isValid(slotId) ? menu.getSlot(slotId).getStack() : ItemStack.EMPTY;
   }

   private static boolean clickMenu(ScreenHandler menu, int slotId, int button, SlotActionType input) {
      ClientPlayerInteractionManager gameMode = CONTEXT.gameMode();
      ClientPlayerEntity player = CONTEXT.player();
      if (gameMode != null && player != null) {
         gameMode.clickSlot(menu.syncId, slotId, button, input, player);
         return true;
      } else {
         return false;
      }
   }

   private static int clickButton(ClickType clickAction) {
      return clickAction == ClickType.RIGHT ? 1 : 0;
   }
}
