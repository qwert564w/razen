package org.ryzen.mixin.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import org.ryzen.feature.impl.combat.HitBoxesFeature;
import org.ryzen.feature.impl.movement.NoPushFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({Entity.class})
public abstract class EntityMixin {
   @Inject(
      method = {"pushAwayFrom(Lnet/minecraft/entity/Entity;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelEntityPush(Entity entity, CallbackInfo ci) {
      if (NoPushFeature.shouldCancelEntityPush((Entity)(Object)this, entity)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"getTargetingMargin"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void expandPickRadius(CallbackInfoReturnable<Float> cir) {
      HitBoxesFeature hitBoxes = HitBoxesFeature.getEnabled();
      if (hitBoxes != null && hitBoxes.appliesTo((Entity)(Object)this)) {
         cir.setReturnValue((Float)cir.getReturnValue() + (float)hitBoxes.getHorizontalExpansion());
      }
   }
}
