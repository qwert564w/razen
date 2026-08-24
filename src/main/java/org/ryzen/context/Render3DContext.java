package org.ryzen.context;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;

@Environment(EnvType.CLIENT)
public interface Render3DContext extends MinecraftContext {
   default boolean isInRender3D() {
      return RenderContext.state3D().isActive();
   }

   default GameRenderer gameRenderer3D() {
      return RenderContext.state3D().getGameRenderer();
   }

   default RenderTickCounter deltaTracker() {
      return RenderContext.state3D().getDeltaTracker();
   }
}
