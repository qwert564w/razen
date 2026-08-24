package org.ryzen.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

@Environment(EnvType.CLIENT)
public final class BuilderRotation implements AuraRotation {
   private static final double POINT_HEIGHT = 0.72;
   private static final double SHOULDER_OFFSET = 1.0;
   private static final double SIDE_SPREAD = 0.38;
   private static final double DEPTH_SPREAD = 0.42;
   private static final long POINT_CHANGE_MIN_MS = 140L;
   private static final long POINT_CHANGE_MAX_MS = 380L;
   private static final float YAW_INERTIA = 0.78F;
   private static final float PITCH_INERTIA = 0.74F;
   private static final float YAW_TRACKING = 0.38F;
   private static final float PITCH_TRACKING = 0.3F;
   private static final float MAX_YAW_STEP = 6.0F;
   private static final float MAX_PITCH_STEP = 2.9F;
   private static final float YAW_ACCELERATION = 0.42F;
   private static final float PITCH_ACCELERATION = 0.28F;
   private static final float PITCH_DEAD_ZONE = 0.65F;
   private static final float YAW_JITTER = 0.14F;
   private static final float PITCH_JITTER = 0.11F;
   private static final float JITTER_RESPONSE = 0.48F;
   private static final float CHAOS = 0.14F;
   private static final float MICRO_PAUSE_CHANCE = 0.06F;
   private static final float JERK_SMOOTHING_YAW = 0.22F;
   private static final float JERK_SMOOTHING_PITCH = 0.14F;
   private float yawVelocity;
   private float pitchVelocity;
   private float yawAcceleration;
   private float pitchAcceleration;
   private long nextPointChangeAt;
   private double pointHeightFactor = 0.72;
   private double pointSide;
   private double pointDepth;

   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      float currentYaw = RotationContext.isActive() ? RotationContext.getFreeYaw() : player.getYaw();
      float currentPitch = RotationContext.isActive() ? RotationContext.getFreePitch() : player.getPitch();
      if (!attackLikely && ThreadLocalRandom.current().nextFloat() < 0.06F) {
         RotationContext.setRotation(currentYaw + this.yawVelocity, clampPitch(currentPitch + this.pitchVelocity));
      } else {
         Vec3d aim = this.aimPoint(target, targetEyePos);
         Vec3d delta = aim.subtract(player.getEyePos());
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         float wantedYaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
         float wantedPitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
         float yawError = MathHelper.wrapDegrees(wantedYaw - currentYaw);
         float pitchError = wantedPitch - currentPitch;
         if (Math.abs(pitchError) < 0.65F) {
            pitchError = 0.0F;
         }

         this.yawVelocity = this.step(this.yawVelocity, yawError, 0.38F, 0.78F, 0.42F, 6.0F, 0.22F, true);
         this.pitchVelocity = this.step(this.pitchVelocity, pitchError, 0.3F, 0.74F, 0.28F, 2.9F, 0.14F, false);
         float yaw = currentYaw + this.yawVelocity + jitter(0.14F, 1.8);
         float pitch = currentPitch + this.pitchVelocity + jitter(0.11F, 2.2);
         RotationContext.setRotation(yaw, clampPitch(pitch));
      }
   }

   private float step(float velocity, float error, float tracking, float inertia, float acceleration, float maxStep, float jerkSmoothing, boolean yawAxis) {
      float wanted = MathHelper.clamp(error * tracking, -maxStep, maxStep);
      float blended = velocity * inertia + wanted * (1.0F - inertia);
      float requestedAcceleration = blended - velocity;
      float previousAcceleration = yawAxis ? this.yawAcceleration : this.pitchAcceleration;
      float smoothed = MathHelper.lerp(jerkSmoothing, previousAcceleration, requestedAcceleration);
      smoothed = MathHelper.clamp(smoothed, -acceleration, acceleration);
      if (yawAxis) {
         this.yawAcceleration = smoothed;
      } else {
         this.pitchAcceleration = smoothed;
      }

      float next = velocity + smoothed;
      float ceiling = maxStep * (1.0F + (ThreadLocalRandom.current().nextFloat() - 0.5F) * 0.14F);
      next = MathHelper.clamp(next, -ceiling, ceiling);
      if (error > 0.0F && next > error) {
         next = error;
      } else if (error < 0.0F && next < error) {
         next = error;
      }

      return next;
   }

   private Vec3d aimPoint(LivingEntity target, Vec3d targetEyePos) {
      long now = System.currentTimeMillis();
      if (now >= this.nextPointChangeAt) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         this.nextPointChangeAt = now + random.nextLong(140L, 381L);
         this.pointHeightFactor = 0.72 + (random.nextDouble() - 0.5) * 0.2;
         this.pointSide = (random.nextDouble() * 2.0 - 1.0) * 0.38 * 1.0;
         this.pointDepth = (random.nextDouble() * 2.0 - 1.0) * 0.42;
      }

      double width = (double)target.getWidth();
      Vec3d feet = target.getEntityPos();
      Vec3d base = feet.add(0.0, (targetEyePos.y - feet.y) * this.pointHeightFactor, 0.0);
      double yawRadians = Math.toRadians((double)target.getYaw());
      double rightX = Math.cos(yawRadians);
      double rightZ = Math.sin(yawRadians);
      return base.add(
         rightX * this.pointSide * width - rightZ * this.pointDepth * width, 0.0, rightZ * this.pointSide * width + rightX * this.pointDepth * width
      );
   }

   private static float jitter(float amount, double rate) {
      if (amount <= 0.0F) {
         return 0.0F;
      } else {
         double phase = (double)System.currentTimeMillis() / 1000.0 * rate;
         double wave = Math.sin(phase) * 0.04 + Math.sin(phase * 2.7) * 0.015;
         double noise = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02;
         return (float)((wave + noise) * (double)amount * 0.48F * 100.0);
      }
   }

   private static float clampPitch(float pitch) {
      return MathHelper.clamp(pitch, -90.0F, 90.0F);
   }

   @Override
   public void onAttack() {
      this.yawVelocity *= 0.5F;
      this.pitchVelocity *= 0.5F;
      this.yawAcceleration = 0.0F;
      this.pitchAcceleration = 0.0F;
   }

   @Override
   public void reset() {
      this.yawVelocity = 0.0F;
      this.pitchVelocity = 0.0F;
      this.yawAcceleration = 0.0F;
      this.pitchAcceleration = 0.0F;
      this.nextPointChangeAt = 0L;
   }
}
