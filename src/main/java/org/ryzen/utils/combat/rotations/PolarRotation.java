package org.ryzen.utils.combat.rotations;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

@Environment(EnvType.CLIENT)
public final class PolarRotation implements AuraRotation {
   private static final long RAMP_MS = 120L;
   private LivingEntity lastTarget;
   private long rampStartMs;

   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      if (target != this.lastTarget) {
         this.lastTarget = target;
         this.rampStartMs = System.currentTimeMillis();
      }

      Vec3d aimPoint = target == null ? targetEyePos : target.getBoundingBox().getCenter();
      Vec3d delta = aimPoint.subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float wantedYaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float wantedPitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      float currentYaw = RotationContext.isActive() ? RotationContext.getFreeYaw() : player.getYaw();
      float currentPitch = RotationContext.isActive() ? RotationContext.getFreePitch() : player.getPitch();
      float factor = attackLikely ? 1.0F : this.ramp();
      float yaw = currentYaw + MathHelper.wrapDegrees(wantedYaw - currentYaw) * factor;
      float pitch = currentPitch + (wantedPitch - currentPitch) * factor;
      RotationContext.setRotation(yaw, MathHelper.clamp(pitch, -89.0F, 89.0F));
   }

   private float ramp() {
      long elapsed = System.currentTimeMillis() - this.rampStartMs;
      return MathHelper.clamp((float)elapsed / 120.0F, 0.35F, 1.0F);
   }

   @Override
   public void reset() {
      this.lastTarget = null;
      this.rampStartMs = 0L;
   }
}
