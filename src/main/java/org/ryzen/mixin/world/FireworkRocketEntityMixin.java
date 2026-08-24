package org.ryzen.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.ryzen.feature.impl.movement.SuperFireworkFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin({FireworkRocketEntity.class})
public abstract class FireworkRocketEntityMixin {
   @WrapOperation(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"
      )}
   )
   private void ryzen$superFireworkBoost(LivingEntity rider, Vec3d movement, Operation<Void> original) {
      SuperFireworkFeature feature = SuperFireworkFeature.getEnabled();
      if (feature != null && rider == MinecraftClient.getInstance().player) {
         original.call(new Object[]{rider, feature.boostedMovement(rider)});
      } else {
         original.call(new Object[]{rider, movement});
      }
   }
}
