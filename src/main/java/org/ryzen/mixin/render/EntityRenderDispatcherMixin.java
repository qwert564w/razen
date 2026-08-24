package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.ryzen.utils.render.EntityEspDispatcherBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin({EntityRenderManager.class})
public abstract class EntityRenderDispatcherMixin implements EntityEspDispatcherBridge {
   @Shadow
   public abstract <S extends EntityRenderState> EntityRenderer<?, ? super S> getRenderer(S var1);

   @Unique
   @Override
   public <S extends EntityRenderState> void submitForGlow(
      S state, CameraRenderState cameraState, double x, double y, double z, MatrixStack poseStack, OrderedRenderCommandQueue submitNodeCollector
   ) {
      EntityRenderer<?, ? super S> renderer = this.getRenderer(state);
      Vec3d renderOffset = renderer.getPositionOffset(state);
      poseStack.push();
      poseStack.translate(x + renderOffset.getX(), y + renderOffset.getY(), z + renderOffset.getZ());
      Text nameTag = state.displayName;
      state.displayName = null;

      try {
         renderer.render(state, poseStack, submitNodeCollector, cameraState);
      } finally {
         state.displayName = nameTag;
         poseStack.pop();
      }
   }
}
