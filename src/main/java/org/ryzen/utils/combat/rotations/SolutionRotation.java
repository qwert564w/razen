package org.ryzen.utils.combat.rotations;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

@Environment(EnvType.CLIENT)
public final class SolutionRotation implements AuraRotation {
   private static final float BASE_STEP = 24.0F;
   private static final float PITCH_DIVISOR = 1.9F;
   private static final float ACCEL_RISE = 0.06F;
   private static final float ACCEL_DECAY = 0.12F;
   private static final float ACCEL_MIN = 0.35F;
   private static final float ACCEL_MAX = 1.6F;
   private static final float JITTER_AMPLITUDE = 0.8F;
   private static final float JITTER_SMOOTHING = 4.0F;
   private static final float ATTACK_SHIFT = 1.4F;
   private static final float ATTACK_DECAY = 0.6F;
   private float accel = 0.35F;
   private float jitterYaw;
   private float jitterPitch;
   private float shiftYaw;
   private float shiftPitch;

   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      Vec3d aimPoint = target == null ? targetEyePos : target.getBoundingBox().getCenter();
      Vec3d delta = aimPoint.subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float wantedYaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float wantedPitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      float currentYaw = RotationContext.isActive() ? RotationContext.getFreeYaw() : player.getYaw();
      float currentPitch = RotationContext.isActive() ? RotationContext.getFreePitch() : player.getPitch();
      float yawDiff = MathHelper.wrapDegrees(wantedYaw - currentYaw);
      float pitchDiff = wantedPitch - currentPitch;
      boolean closing = Math.abs(yawDiff) > 24.0F * this.accel;
      this.accel = MathHelper.clamp(this.accel + (closing ? 0.06F : -0.12F), 0.35F, 1.6F);
      float yawStep = 24.0F * this.accel;
      float pitchStep = yawStep / 1.9F;
      float yaw = currentYaw + MathHelper.clamp(yawDiff, -yawStep, yawStep);
      float pitch = currentPitch + MathHelper.clamp(pitchDiff, -pitchStep, pitchStep);
      if (!attackLikely) {
         yaw += this.tickJitterYaw();
         pitch += this.tickJitterPitch();
      }

      yaw += this.shiftYaw;
      pitch += this.shiftPitch;
      this.shiftYaw *= 0.6F;
      this.shiftPitch *= 0.6F;
      RotationContext.setRotation(yaw, MathHelper.clamp(pitch, -89.0F, 89.0F));
   }

   private float tickJitterYaw() {
      float sample = ((float)Math.random() - 0.5F) * 2.0F * 0.8F;
      this.jitterYaw = MathHelper.lerp(0.25F, this.jitterYaw, sample);
      return this.jitterYaw;
   }

   private float tickJitterPitch() {
      float sample = ((float)Math.random() - 0.5F) * 2.0F * 0.8F;
      this.jitterPitch = MathHelper.lerp(0.25F, this.jitterPitch, sample);
      return this.jitterPitch;
   }

   @Override
   public void onAttack() {
      this.shiftYaw = ((float)Math.random() - 0.5F) * 2.0F * 1.4F;
      this.shiftPitch = ((float)Math.random() - 0.5F) * 1.4F;
   }

   @Override
   public void reset() {
      this.accel = 0.35F;
      this.jitterYaw = 0.0F;
      this.jitterPitch = 0.0F;
      this.shiftYaw = 0.0F;
      this.shiftPitch = 0.0F;
   }
}
