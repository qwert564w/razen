package org.ryzen.utils.render.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public record WorldEffectContext(WorldRenderState levelRenderState, CameraRenderState cameraRenderState, float tickDelta, Vector4f skyColor) {
   public WorldEffectContext(WorldRenderState levelRenderState, CameraRenderState cameraRenderState, float tickDelta) {
      this(levelRenderState, cameraRenderState, tickDelta, null);
   }
}
