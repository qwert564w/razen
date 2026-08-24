package org.ryzen.utils.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;

@Environment(EnvType.CLIENT)
public interface EntityEspDispatcherBridge {
   <S extends EntityRenderState> void submitForGlow(
      S var1, CameraRenderState var2, double var3, double var5, double var7, MatrixStack var9, OrderedRenderCommandQueue var10
   );
}
