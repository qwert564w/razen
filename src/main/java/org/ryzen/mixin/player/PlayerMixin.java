package org.ryzen.mixin.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.ryzen.feature.impl.movement.NoPushFeature;
import org.ryzen.feature.impl.movement.NoWebFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({PlayerEntity.class})
public abstract class PlayerMixin {
   @Inject(
      method = {"slowMovement(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Vec3d;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelBlockPush(BlockState state, Vec3d multiplier, CallbackInfo ci) {
      PlayerEntity player = (PlayerEntity)(Object)this;
      if (NoPushFeature.shouldCancelBlockPush(player, state) || NoWebFeature.shouldCancelWeb(player, state)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"isPushedByFluids()Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelFluidPush(CallbackInfoReturnable<Boolean> cir) {
      if (NoPushFeature.shouldCancelFluidPush((PlayerEntity)(Object)this)) {
         cir.setReturnValue(false);
      }
   }
}
