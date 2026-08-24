package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.ryzen.utils.render.EmotionAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({BipedEntityModel.class})
public abstract class HumanoidModelMixin {
   @Inject(
      method = {"setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V"},
      at = {@At("TAIL")}
   )
   private void ryzen$applyEmotions(BipedEntityRenderState state, CallbackInfo ci) {
      EmotionAnimator.apply((BipedEntityModel<?>)(Object)this, state);
   }
}
