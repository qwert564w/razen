package org.ryzen.utils.inventory;

import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.screen.ScreenCloseEvent;
import org.ryzen.feature.impl.movement.InventoryMoveFeature;

@Environment(EnvType.CLIENT)
public final class DropAllInventoryController implements MinecraftContext {
   public static final DropAllInventoryController INSTANCE = new DropAllInventoryController();
   private static final Text DROP_ALL = Text.literal("Выбросить всё");
   private static final Text STOP = Text.literal("Остановить");
   private final Deque<Integer> slots = new ArrayDeque<>();
   private InventoryScreen screen;
   private PlayerScreenHandler menu;
   private ButtonWidget button;
   private boolean running;

   private DropAllInventoryController() {
   }

   public static void bindButton(ButtonWidget button) {
      INSTANCE.button = button;
      INSTANCE.updateButton();
   }

   public static void toggle(InventoryScreen screen) {
      if (INSTANCE.running) {
         INSTANCE.cancel();
      } else {
         INSTANCE.start(screen);
      }
   }

   public static boolean blocksInventoryOperations() {
      return INSTANCE.running || InventoryMoveFeature.isClickPipelineBusy();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.running) {
         MinecraftClient client = event.getClient();
         if (client != null
            && client.player != null
            && client.currentScreen == this.screen
            && client.player.currentScreenHandler == this.menu
            && !InventoryUtil.hasCarriedItem()) {
            if (!InventorySwap.isBusy()) {
               while (!this.slots.isEmpty()) {
                  int slotId = this.slots.removeFirst();
                  if (this.menu.isValid(slotId) && this.menu.getSlot(slotId).hasStack()) {
                     InventoryUtil.dropStack(slotId);
                     return;
                  }
               }

               this.finish();
            }
         } else {
            this.cancel();
         }
      }
   }

   @EventTarget
   public void onScreenClose(ScreenCloseEvent event) {
      if (event.getScreen() == this.screen) {
         this.cancel();
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.cancel();
   }

   private void start(InventoryScreen screen) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null
         && client.player != null
         && screen != null
         && client.player.currentScreenHandler == screen.getScreenHandler()
         && !InventoryUtil.hasCarriedItem()
         && !InventorySwap.isBusy()) {
         this.screen = screen;
         this.menu = (PlayerScreenHandler)screen.getScreenHandler();
         this.slots.clear();

         for (int slotId = 0; slotId < this.menu.slots.size(); slotId++) {
            if (this.menu.getSlot(slotId).hasStack()) {
               this.slots.addLast(slotId);
            }
         }

         this.running = !this.slots.isEmpty();
         this.updateButton();
      }
   }

   private void finish() {
      this.running = false;
      this.slots.clear();
      this.updateButton();
   }

   private void cancel() {
      this.finish();
      this.screen = null;
      this.menu = null;
      this.button = null;
   }

   private void updateButton() {
      if (this.button != null) {
         this.button.setMessage(this.running ? STOP : DROP_ALL);
      }
   }
}
