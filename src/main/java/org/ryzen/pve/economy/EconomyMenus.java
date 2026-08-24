package org.ryzen.pve.economy;

import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.ryzen.utils.inventory.ContainerLootService;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class EconomyMenus {
   private EconomyMenus() {
   }

   public static String title(MinecraftClient client) {
      return client != null && client.currentScreen instanceof HandledScreen<?> screen ? screen.getTitle().getString() : "";
   }

   public static boolean titleContains(MinecraftClient client, String... markers) {
      return EconomyTextParser.containsAny(title(client), markers);
   }

   public static int currentContainerId(MinecraftClient client) {
      return client != null && client.player != null && client.player.currentScreenHandler != null ? client.player.currentScreenHandler.syncId : -1;
   }

   public static boolean isCurrent(MinecraftClient client, ScreenHandler menu) {
      return client != null && client.player != null && menu != null && client.player.currentScreenHandler == menu && currentContainerId(client) == menu.syncId;
   }

   public static int findContainerSlot(ScreenHandler menu, Predicate<ItemStack> predicate) {
      Objects.requireNonNull(menu, "menu");
      Objects.requireNonNull(predicate, "predicate");
      int count = ContainerLootService.containerSlotCount(menu);

      for (int slotId = 0; slotId < count; slotId++) {
         if (menu.isValid(slotId)) {
            Slot slot = menu.getSlot(slotId);
            if (slot != null && slot.hasStack() && predicate.test(slot.getStack())) {
               return slotId;
            }
         }
      }

      return -1;
   }

   public static boolean click(MinecraftClient client, ScreenHandler menu, int slotId, int button, SlotActionType input) {
      return isCurrent(client, menu) && menu.isValid(slotId) && input != null ? InventoryUtil.clickSlot(slotId, button, input) : false;
   }

   public static boolean quickMove(MinecraftClient client, ScreenHandler menu, int slotId) {
      return click(client, menu, slotId, 0, SlotActionType.QUICK_MOVE);
   }

   public static boolean closeOwned(MinecraftClient client, int containerId) {
      if (client != null
         && client.player != null
         && containerId >= 0
         && currentContainerId(client) == containerId
         && client.player.currentScreenHandler != client.player.playerScreenHandler) {
         client.player.closeHandledScreen();
         return true;
      } else {
         return false;
      }
   }
}
