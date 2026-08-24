package org.ryzen.menu.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import org.ryzen.event.events.screen.ScreenMouseButtonEvent;

@Environment(EnvType.CLIENT)
public final class MenuOverlay {
   private static final MenuOverlayState STATE = new MenuOverlayState();
   private static final MenuDragController DRAG = new MenuDragController();
   private static final MenuOverlayRenderer RENDERER = new MenuOverlayRenderer();

   private MenuOverlay() {
   }

   public static boolean toggle(MinecraftClient minecraft) {
      if (STATE.isInteractive()) {
         close(minecraft);
         return true;
      } else {
         open(minecraft);
         return true;
      }
   }

   public static void close(MinecraftClient minecraft) {
      if (STATE.isOpen() && !STATE.isClosing()) {
         boolean restoreMouse = shouldReturnMouseToGame(minecraft);
         STATE.beginClose();
         DRAG.reset();
         RENDERER.releasePointer();
         if (restoreMouse) {
            minecraft.mouse.lockCursor();
         }
      }
   }

   public static boolean suspendForReload(MinecraftClient minecraft) {
      if (!STATE.isInteractive()) {
         return false;
      } else {
         boolean restoreMouse = STATE.grabbedMouseBeforeOpen() && shouldReturnMouseToGame(minecraft);
         STATE.suspend();
         DRAG.reset();
         RENDERER.releasePointer();
         if (restoreMouse) {
            minecraft.mouse.lockCursor();
         }

         return true;
      }
   }

   public static void resumeAfterReload(MinecraftClient minecraft) {
      if (!STATE.isOpen()) {
         STATE.resume();
         DRAG.reset();
         KeyBinding.unpressAll();
         if (minecraft.mouse.isCursorLocked()) {
            minecraft.mouse.unlockCursor();
         }
      }
   }

   public static boolean isOpen() {
      return STATE.isInteractive();
   }

   public static MenuOverlayState state() {
      return STATE;
   }

   public static boolean isVisible() {
      return STATE.isOpen();
   }

   public static boolean blocksInput() {
      return STATE.isInteractive();
   }

   public static void focusNextHeaderAction(int direction) {
      STATE.focusNextHeaderAction(direction);
   }

   public static void activateFocusedHeaderAction() {
      STATE.activateFocusedHeaderAction();
   }

   public static void closePageOrOverlay(MinecraftClient minecraft) {
      if (STATE.isHudLayoutMode()) {
         STATE.setHudLayoutMode(false);
      } else if (STATE.page() != MenuPage.NONE) {
         STATE.openPage(MenuPage.NONE);
      } else {
         close(minecraft);
      }
   }

   public static void openSearch() {
      if (STATE.isInteractive() && STATE.page() != MenuPage.SEARCH) {
         STATE.openPage(MenuPage.SEARCH);
      }
   }

   public static boolean isSearchOpen() {
      return STATE.isInteractive() && RENDERER.isSearchOpen();
   }

   public static boolean isSearchFocused() {
      return STATE.isInteractive() && RENDERER.isSearchFocused();
   }

   public static boolean isCapturingBind() {
      return STATE.isInteractive() && RENDERER.isCapturingBind();
   }

   public static void backspaceSearch() {
      RENDERER.backspaceSearch();
   }

   public static boolean handleKey(int key) {
      return STATE.isInteractive() && RENDERER.handleKey(key);
   }

   public static boolean handleCharacter(int codePoint) {
      if (!STATE.isInteractive()) {
         return false;
      } else if (RENDERER.handleCharacter(codePoint)) {
         return true;
      } else if (!isSearchFocused()) {
         return false;
      } else {
         RENDERER.appendSearchCodePoint(codePoint);
         return true;
      }
   }

   public static void handleScroll(double vertical) {
      if (STATE.isInteractive()) {
         RENDERER.handleScroll(mouseX(MinecraftClient.getInstance()), mouseY(MinecraftClient.getInstance()), vertical);
      }
   }

   public static boolean handleMouseButton(MinecraftClient minecraft, int button, int action) {
      if (!STATE.isInteractive()) {
         return false;
      } else {
         int screenWidth = minecraft.getWindow().getScaledWidth();
         int screenHeight = minecraft.getWindow().getScaledHeight();
         int mouseX = mouseX(minecraft);
         int mouseY = mouseY(minecraft);
         RENDERER.layout(minecraft, STATE, screenWidth, screenHeight, mouseX, mouseY);
         if (action == 1) {
            if (RENDERER.handleMouseButton(mouseX, mouseY, button, STATE)) {
               DRAG.onRelease();
               return true;
            }

            if (button == 0) {
               DRAG.onPress((float)mouseX, (float)mouseY, STATE, RENDERER);
            }
         } else if (action == 0) {
            RENDERER.releasePointer();
            if (button == 0) {
               DRAG.onRelease();
            }
         }

         return true;
      }
   }

   public static boolean handleScreenMouseButton(MinecraftClient minecraft, int button, ScreenMouseButtonEvent.Action action) {
      return action == ScreenMouseButtonEvent.Action.DRAG
         ? STATE.isInteractive()
         : handleMouseButton(minecraft, button, action == ScreenMouseButtonEvent.Action.RELEASE ? 0 : 1);
   }

   public static void render(MinecraftClient minecraft, DrawContext guiGraphicsExtractor, int screenWidth, int screenHeight) {
      if (STATE.isOpen()) {
         if (STATE.isClosing() && STATE.openProgress() <= 0.001F) {
            STATE.close();
         } else {
            MenuDimensions dimensions = MenuDimensions.resolve(minecraft, STATE);
            int mouseX = mouseX(minecraft);
            int mouseY = mouseY(minecraft);
            DRAG.update((float)mouseX, (float)mouseY, STATE, dimensions, (float)screenWidth, (float)screenHeight);
            RENDERER.layout(minecraft, STATE, screenWidth, screenHeight, mouseX, mouseY);
            RENDERER.drag(mouseX, mouseY);
            RENDERER.render(minecraft, guiGraphicsExtractor);
         }
      }
   }

   private static void open(MinecraftClient minecraft) {
      boolean grabbedMouseBeforeOpen = minecraft.mouse.isCursorLocked();
      STATE.open(grabbedMouseBeforeOpen);
      DRAG.reset();
      KeyBinding.unpressAll();
      if (grabbedMouseBeforeOpen) {
         minecraft.mouse.unlockCursor();
      }
   }

   private static int mouseX(MinecraftClient minecraft) {
      return (int)Math.round(minecraft.mouse.getScaledX(minecraft.getWindow()));
   }

   private static int mouseY(MinecraftClient minecraft) {
      return (int)Math.round(minecraft.mouse.getScaledY(minecraft.getWindow()));
   }

   private static boolean shouldReturnMouseToGame(MinecraftClient minecraft) {
      return minecraft.world != null && minecraft.player != null && minecraft.currentScreen == null;
   }
}
