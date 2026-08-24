package org.ryzen.mixin.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.ryzen.feature.impl.movement.NoPushFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({FishingBobberEntity.class})
public abstract class FishingHookMixin {
   @Inject(
      method = {"pullHookedEntity(Lnet/minecraft/entity/Entity;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelFishingHookPull(Entity entity, CallbackInfo ci) {
      if (NoPushFeature.shouldCancelFishingHookPull((FishingBobberEntity)(Object)this, entity)) {
         ci.cancel();
      }
   }
}
