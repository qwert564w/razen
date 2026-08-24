package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.ryzen.feature.impl.visual.NameTagsFeature;
import org.ryzen.utils.render.ClientCape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({PlayerEntityRenderer.class})
public abstract class AvatarRendererMixin {
   @Inject(
      method = {"updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V"},
      at = {@At("TAIL")}
   )
   private void forceCapeVisible(PlayerLikeEntity avatar, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
      if (ClientCape.shouldForceCape(avatar.getUuid())) {
         state.capeVisible = true;
      }
   }

   @Inject(
      method = {"hasLabel(Lnet/minecraft/entity/PlayerLikeEntity;D)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void hideVanillaNameTag(PlayerLikeEntity avatar, double distanceSqr, CallbackInfoReturnable<Boolean> cir) {
      if (NameTagsFeature.shouldHideVanillaTag()) {
         cir.setReturnValue(false);
      }
   }
}
