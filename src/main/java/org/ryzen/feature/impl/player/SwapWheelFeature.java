package org.ryzen.feature.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.ryzen.event.events.input.MouseInputEvent;
import org.ryzen.event.events.render.Render2DEvent;
import org.ryzen.event.events.screen.ScreenCloseEvent;
import org.ryzen.event.events.screen.ScreenMouseButtonEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.InputBindSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.menu.core.MenuConfigStore;
import org.ryzen.menu.core.MenuOverlay;
import org.ryzen.mixin.accessor.AbstractContainerScreenAccessor;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.inventory.InventorySwap;
import org.ryzen.utils.inventory.InventoryUtil;
import org.ryzen.utils.render.Theme;
import org.ryzen.utils.render.gui.Render2DUtil;

@Environment(EnvType.CLIENT)
public final class SwapWheelFeature extends Feature implements MinecraftContext {
   private static final Identifier HOTBAR_SPRITE = Identifier.ofVanilla("hud/hotbar");
   private static final Identifier HOTBAR_SELECTION_SPRITE = Identifier.ofVanilla("hud/hotbar_selection");
   private static final int HOTBAR_TEXTURE_WIDTH = 182;
   private static final int HOTBAR_TEXTURE_HEIGHT = 22;
   private static final int SLOT_SIZE = 22;
   private static final int SLOT_GAP = 10;
   private static final int INVENTORY_SLOTS_START = 9;
   private static final int INVENTORY_SLOTS_END = 45;
   private static final float DIRECTION_DEAD_ZONE = 10.0F;
   private static final int MAX_SLOTS = 8;
   private static final long TAP_MS = 250L;
   private static final int PENDING_MAX_TICKS = 40;
   private static final String CONFIG_KEY_PREFIX = "autoswap.item.";
   public final InputBindSetting key = this.register(new InputBindSetting("Key", 82));
   public final NumberSetting slots = this.register(new NumberSetting("Slots", 4.0, 2.0, 8.0, 1.0, ""));
   public final BooleanSetting strict = this.register(new BooleanSetting("Strict", false));
   private final Item[] assignedItems = new Item[8];
   private boolean assignedLoaded;
   private boolean spaceOpen;
   private boolean editMode;
   private long pressTimeMs;
   private int hoveredSlot = -1;
   private int pickTargetSlot = -1;
   private int pendingContainerSlot = -1;
   private int pendingTicks;

   public SwapWheelFeature() {
      super("SwapWheel", "Pick a hotbar item on screen while holding a key", FeatureCategory.PLAYER, -1);
      this.renamedFrom("AutoSwap");
   }

   @Override
   protected void onDisable() {
      this.closeSpace();
      this.pickTargetSlot = -1;
      this.pendingContainerSlot = -1;
      this.pendingTicks = 0;
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (this.key.matches(event.getKey()) && this.handleBindAction(event.getAction())) {
         event.cancel();
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (this.key.matchesMouse(event.getButton()) && this.handleBindAction(event.getAction())) {
         event.cancel();
      } else if (this.spaceOpen) {
         if (this.editMode && event.getAction() == 1 && this.hoveredSlot >= 0) {
            if (event.getButton() == 0) {
               this.beginAssign(this.hoveredSlot);
            } else if (event.getButton() == 1) {
               this.setAssignedItem(this.hoveredSlot, null);
            }
         }

         event.cancel();
      }
   }

   private boolean handleBindAction(int action) {
      if (action == 1) {
         if (this.spaceOpen && this.editMode) {
            this.closeSpace();
            return true;
         } else {
            this.pressTimeMs = System.currentTimeMillis();
            return this.openSpace();
         }
      } else if (action == 0 && this.spaceOpen) {
         if (this.editMode) {
            return true;
         } else if (System.currentTimeMillis() - this.pressTimeMs < 250L) {
            this.editMode = true;
            return true;
         } else {
            int hovered = this.hoveredSlot;
            this.closeSpace();
            this.applyHoveredSlot(hovered);
            return true;
         }
      } else {
         return action == 2 && this.spaceOpen;
      }
   }

   @EventTarget
   public void onScreenMouseButton(ScreenMouseButtonEvent event) {
      if (this.pickTargetSlot >= 0 && event.getAction() == ScreenMouseButtonEvent.Action.CLICK && event.getScreen() instanceof InventoryScreen screen) {
         Slot hovered = ((AbstractContainerScreenAccessor)screen).getHoveredSlot();
         if (hovered != null && hovered.hasStack() && hovered.id >= 9 && hovered.id < 45) {
            this.setAssignedItem(this.pickTargetSlot, hovered.getStack().getItem());
            this.pickTargetSlot = -1;
            event.cancel();
            mc.setScreen(null);
         }
      }
   }

   @EventTarget
   public void onScreenClose(ScreenCloseEvent event) {
      if (event.getScreen() instanceof InventoryScreen) {
         this.pickTargetSlot = -1;
      }
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      if (this.spaceOpen) {
         MinecraftClient mc = event.getClient();
         ClientPlayerEntity player = mc.player;
         if (player != null && mc.currentScreen == null && !MenuOverlay.isOpen()) {
            DrawContext extractor = event.getGuiGraphicsExtractor();
            int count = this.slotCount();
            float mouseX = (float)mc.mouse.getScaledX(mc.getWindow());
            float mouseY = (float)mc.mouse.getScaledY(mc.getWindow());
            this.hoveredSlot = -1;
            int[][] offsets = slotOffsets(count);
            int centerX = extractor.getScaledWindowWidth() / 2;
            int centerY = extractor.getScaledWindowHeight() / 2;
            this.hoveredSlot = this.findHoveredSlot(offsets, count, centerX, centerY, mouseX, mouseY);

            for (int index = 0; index < count; index++) {
               int x = centerX + offsets[index][0] - 11;
               int y = centerY + offsets[index][1] - 11;
               this.drawSlot(extractor, player, index, x, y, this.hoveredSlot == index);
            }
         } else {
            this.closeSpace();
         }
      }
   }

   private int findHoveredSlot(int[][] offsets, int count, int centerX, int centerY, float mouseX, float mouseY) {
      for (int index = 0; index < count; index++) {
         int x = centerX + offsets[index][0] - 11;
         int y = centerY + offsets[index][1] - 11;
         if (mouseX >= (float)x && mouseX < (float)(x + 22) && mouseY >= (float)y && mouseY < (float)(y + 22)) {
            return index;
         }
      }

      if (!this.strict.getValue() && !this.editMode) {
         float dragX = mouseX - (float)centerX;
         float dragY = mouseY - (float)centerY;
         if (dragX * dragX + dragY * dragY < 100.0F) {
            return -1;
         } else {
            int best = -1;
            double bestAlignment = -Double.MAX_VALUE;
            double dragLength = Math.sqrt((double)(dragX * dragX + dragY * dragY));

            for (int indexx = 0; indexx < count; indexx++) {
               double slotLength = Math.sqrt((double)offsets[indexx][0] * (double)offsets[indexx][0] + (double)offsets[indexx][1] * (double)offsets[indexx][1]);
               if (!(slotLength < 0.001)) {
                  double alignment = (double)(dragX * (float)offsets[indexx][0] + dragY * (float)offsets[indexx][1]) / (dragLength * slotLength);
                  if (alignment > bestAlignment) {
                     bestAlignment = alignment;
                     best = indexx;
                  }
               }
            }

            return best;
         }
      } else {
         return -1;
      }
   }

   private static int[][] slotOffsets(int count) {
      int step = 32;

      return switch (count) {
         case 2 -> new int[][]{{-step, 0}, {step, 0}};
         case 3 -> ring(3, Math.round((float)step * 1.3F), -90.0F);
         case 4 -> new int[][]{{-step, -step}, {step, -step}, {-step, step}, {step, step}};
         case 5, 6 -> ring(count, Math.round((float)step * 1.55F), -90.0F);
         case 7 -> ring(7, Math.round((float)step * 1.85F), -90.0F);
         default -> new int[][]{
         {0, -step * 2 - 8},
         {step + 4, -step - 4},
         {step * 2 + 8, 0},
         {step + 4, step + 4},
         {0, step * 2 + 8},
         {-step - 4, step + 4},
         {-step * 2 - 8, 0},
         {-step - 4, -step - 4}
      };
      };
   }

   private static int[][] ring(int count, int radius, float startAngleDeg) {
      int[][] offsets = new int[count][2];

      for (int index = 0; index < count; index++) {
         double angle = Math.toRadians((double)startAngleDeg + (double)index * 360.0 / (double)count);
         offsets[index][0] = (int)Math.round(Math.cos(angle) * (double)radius);
         offsets[index][1] = (int)Math.round(Math.sin(angle) * (double)radius);
      }

      return offsets;
   }

   private void drawSlot(DrawContext extractor, ClientPlayerEntity player, int slot, int x, int y, boolean hovered) {
      int half = 11;
      extractor.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, 182, 22, 0, 0, x, y, half, 22);
      extractor.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, 182, 22, 182 - half, 0, x + half, y, half, 22);
      if (hovered) {
         int accent = Theme.getAccent();
         Render2DUtil.rect((float)(x + 1), (float)(y + 1), 20.0F, 20.0F).color(ColorUtil.withAlpha(accent, 90)).draw();
         Render2DUtil.flush();
         extractor.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION_SPRITE, x - 1, y - 1, 24, 23, accent);
      }

      Item item = this.assignedItem(slot);
      if (item != null) {
         ItemStack stack = new ItemStack(item);
         extractor.drawItem(stack, x + 3, y + 3);
         extractor.drawStackOverlay(mc.textRenderer, stack, x + 3, y + 3);
      }
   }

   private boolean openSpace() {
      if (!this.spaceOpen && this.inGame() && this.screen() == null && !MenuOverlay.isOpen() && mc.mouse.isCursorLocked()) {
         mc.mouse.unlockCursor();
         this.spaceOpen = true;
         this.hoveredSlot = -1;
         this.pickTargetSlot = -1;
         return true;
      } else {
         return false;
      }
   }

   private void applyHoveredSlot(int hovered) {
      ClientPlayerEntity player = this.player();
      if (player != null && hovered >= 0 && hovered < this.slotCount() && !PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.INVENTORY)) {
         Item item = this.assignedItem(hovered);
         if (item == null) {
            this.beginAssign(hovered);
         } else if (!player.getOffHandStack().isOf(item)) {
            int containerSlot = findItemSlot(player, item);
            if (containerSlot >= 0) {
               if (InventorySwap.isBusy()) {
                  this.pendingContainerSlot = containerSlot;
               } else {
                  InventorySwap.equip(containerSlot);
               }
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.pendingContainerSlot >= 0) {
         if (InventorySwap.isBusy()) {
            if (++this.pendingTicks > 40) {
               this.pendingContainerSlot = -1;
               this.pendingTicks = 0;
            }
         } else {
            ClientPlayerEntity player = event.getClient().player;
            if (player != null && player.currentScreenHandler == player.playerScreenHandler) {
               InventorySwap.equip(this.pendingContainerSlot);
            }

            this.pendingContainerSlot = -1;
            this.pendingTicks = 0;
         }
      }
   }

   private static int findItemSlot(ClientPlayerEntity player, Item item) {
      return InventoryUtil.findPlayerMenuSlot(player, stack -> stack.isOf(item));
   }

   private void beginAssign(int slot) {
      ClientPlayerEntity player = this.player();
      if (player != null) {
         this.pickTargetSlot = slot;
         this.closeSpace();
         mc.setScreen(new InventoryScreen(player));
      }
   }

   private void closeSpace() {
      if (this.spaceOpen) {
         this.spaceOpen = false;
         this.editMode = false;
         this.hoveredSlot = -1;
         if (this.inGame() && this.screen() == null && !MenuOverlay.isOpen()) {
            mc.mouse.lockCursor();
         }
      }
   }

   private int slotCount() {
      return (int)Math.round(this.slots.getValue());
   }

   private Item assignedItem(int slot) {
      this.ensureAssignedLoaded();
      return slot >= 0 && slot < 8 ? this.assignedItems[slot] : null;
   }

   private void setAssignedItem(int slot, Item item) {
      this.ensureAssignedLoaded();
      if (slot >= 0 && slot < 8) {
         this.assignedItems[slot] = item;
         String id = item == null ? "" : Registries.ITEM.getId(item).toString();
         MenuConfigStore.save(data -> data.addProperty("autoswap.item." + slot, id));
      }
   }

   private void ensureAssignedLoaded() {
      if (!this.assignedLoaded) {
         this.assignedLoaded = true;

         for (int slot = 0; slot < 8; slot++) {
            String id = MenuConfigStore.getString("autoswap.item." + slot, "");
            if (!id.isEmpty()) {
               Item item = (Item)Registries.ITEM.get(Identifier.of(id));
               this.assignedItems[slot] = item == Items.AIR ? null : item;
            }
         }
      }
   }
}
