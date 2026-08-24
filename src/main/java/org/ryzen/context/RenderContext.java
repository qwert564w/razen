package org.ryzen.context;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;

@Environment(EnvType.CLIENT)
public final class RenderContext {
   public static long overlayStartTime = -1L;
   private static final RenderContext.State2D STATE_2D = new RenderContext.State2D();
   private static final RenderContext.State3D STATE_3D = new RenderContext.State3D();

   private RenderContext() {
   }

   public static void enter2D(InGameHud gui, DrawContext guiGraphicsExtractor, RenderTickCounter deltaTracker) {
      RenderContext.State2D state = STATE_2D;
      state.active = true;
      state.gui = gui;
      state.guiGraphicsExtractor = guiGraphicsExtractor;
      state.deltaTracker = deltaTracker;
   }

   public static void exit2D() {
      STATE_2D.clear();
   }

   public static boolean isIn2D() {
      return STATE_2D.isActive();
   }

   public static DrawContext currentGuiGraphicsExtractor() {
      return STATE_2D.getGuiGraphicsExtractor();
   }

   public static InGameHud currentGui() {
      return STATE_2D.getGui();
   }

   public static RenderTickCounter currentDeltaTracker() {
      return STATE_2D.getDeltaTracker();
   }

   public static void enter3D(GameRenderer gameRenderer, RenderTickCounter deltaTracker) {
      RenderContext.State3D state = STATE_3D;
      state.active = true;
      state.gameRenderer = gameRenderer;
      state.deltaTracker = deltaTracker;
   }

   public static void exit3D() {
      STATE_3D.clear();
   }

   static RenderContext.State2D state2D() {
      return STATE_2D;
   }

   static RenderContext.State3D state3D() {
      return STATE_3D;
   }

   @Environment(EnvType.CLIENT)
   static final class State2D {
      private boolean active;
      private InGameHud gui;
      private DrawContext guiGraphicsExtractor;
      private RenderTickCounter deltaTracker;

      boolean isActive() {
         return this.active;
      }

      InGameHud getGui() {
         return this.gui;
      }

      DrawContext getGuiGraphicsExtractor() {
         return this.guiGraphicsExtractor;
      }

      RenderTickCounter getDeltaTracker() {
         return this.deltaTracker;
      }

      private void clear() {
         this.active = false;
         this.gui = null;
         this.guiGraphicsExtractor = null;
         this.deltaTracker = null;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class State3D {
      private boolean active;
      private GameRenderer gameRenderer;
      private RenderTickCounter deltaTracker;

      boolean isActive() {
         return this.active;
      }

      GameRenderer getGameRenderer() {
         return this.gameRenderer;
      }

      RenderTickCounter getDeltaTracker() {
         return this.deltaTracker;
      }

      private void clear() {
         this.active = false;
         this.gameRenderer = null;
         this.deltaTracker = null;
      }
   }
}
