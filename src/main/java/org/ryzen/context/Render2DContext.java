package org.ryzen.context;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

@Environment(EnvType.CLIENT)
public interface Render2DContext extends MinecraftContext {
   default boolean isInRender2D() {
      return RenderContext.state2D().isActive();
   }

   default InGameHud gui() {
      return RenderContext.state2D().getGui();
   }

   default DrawContext guiGraphicsExtractor() {
      return RenderContext.state2D().getGuiGraphicsExtractor();
   }

   default RenderTickCounter deltaTracker() {
      return RenderContext.state2D().getDeltaTracker();
   }
}
