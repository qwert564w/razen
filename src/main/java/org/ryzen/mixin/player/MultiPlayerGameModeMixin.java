package org.ryzen.mixin.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.ryzen.context.MinecraftContext;
import org.ryzen.feature.impl.player.NoInteractFeature;
import org.ryzen.feature.impl.visual.HitParticlesFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({ClientPlayerInteractionManager.class})
public abstract class MultiPlayerGameModeMixin {
   @Inject(
      method = {"interactBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ryzen$blockInteraction(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
      if (NoInteractFeature.shouldCancel(hit)) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }

   @Inject(
      method = {"attackEntity"},
      at = {@At("HEAD")}
   )
   private void onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (player == MinecraftContext.mc.player) {
         HitParticlesFeature hitParticles = HitParticlesFeature.getEnabled();
         if (hitParticles != null) {
            hitParticles.onAttack(target);
         }
      }
   }
}
