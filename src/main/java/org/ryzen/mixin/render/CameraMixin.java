package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.ryzen.context.MinecraftContext;
import org.ryzen.context.RotationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({Camera.class})
public abstract class CameraMixin {
   @Inject(
      method = {"update"},
      at = {@At("HEAD")}
   )
   private void onSetup(World level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
      RotationContext.applyRenderInterpolation();
      RotationContext.syncFreeLook(entity.getYaw(partialTick), entity.getPitch(partialTick));
   }

   @Redirect(
      method = {"update"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;getYaw(F)F"
      )
   )
   private float redirectCameraYaw(Entity entity, float partialTick) {
      return this.cameraYaw(entity, partialTick);
   }

   @Redirect(
      method = {"update"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;getPitch(F)F"
      )
   )
   private float redirectCameraPitch(Entity entity, float partialTick) {
      return this.cameraPitch(entity, partialTick);
   }

   @Unique
   private float cameraYaw(Entity entity, float partialTick) {
      return entity == MinecraftContext.mc.player && RotationContext.isActive() ? RotationContext.getFreeYaw() : entity.getYaw(partialTick);
   }

   @Unique
   private float cameraPitch(Entity entity, float partialTick) {
      return entity == MinecraftContext.mc.player && RotationContext.isActive() ? RotationContext.getFreePitch() : entity.getPitch(partialTick);
   }
}
