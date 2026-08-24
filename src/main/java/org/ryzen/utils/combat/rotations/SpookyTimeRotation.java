package org.ryzen.utils.combat.rotations;

import java.security.SecureRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

@Environment(EnvType.CLIENT)
public final class SpookyTimeRotation implements AuraRotation {
   private static final SecureRandom SECURE_RANDOM = new SecureRandom();
   private static final float YAW_JITTER = 1.5F;

   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      float fromYaw = player.getYaw();
      float fromPitch = player.getPitch();
      Box box = target.getBoundingBox();
      double centerX = (box.minX + box.maxX) * 0.5;
      double centerZ = (box.minZ + box.maxZ) * 0.5;
      double bodyY = box.minY + (box.maxY - box.minY) * 0.4;
      SpookyTimeRotation.Rotation goal = toPoint(player, new Vec3d(centerX, bodyY, centerZ));
      float goalYaw = goal.yaw() + legitRandom(-1.5F, 1.5F);
      float yaw = moveTowardsAngle(fromYaw, goalYaw, legitRandom(65.0F, 95.0F));
      float pitch = fromPitch;
      if (!onTarget(player, target, yaw, fromPitch)) {
         pitch = fromPitch + MathHelper.clamp((goal.pitch() - fromPitch) * 0.3F, -10.0F, 10.0F);
      }

      if (!onTarget(player, target, yaw, pitch)) {
         yaw = goal.yaw();
         pitch = goal.pitch();
      }

      SpookyTimeRotation.Rotation corrected = correctRotation(fromYaw, fromPitch, yaw, pitch);
      RotationContext.setRotation(corrected.yaw(), corrected.pitch());
   }

   private static SpookyTimeRotation.Rotation toPoint(ClientPlayerEntity player, Vec3d point) {
      Vec3d eye = player.getEyePos();
      double dx = point.x - eye.x;
      double dy = point.y - eye.y;
      double dz = point.z - eye.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
      float pitch = MathHelper.clamp((float)(-Math.toDegrees(Math.atan2(dy, horizontal))), -90.0F, 90.0F);
      return new SpookyTimeRotation.Rotation(yaw, pitch);
   }

   private static float moveTowardsAngle(float current, float target, float speed) {
      float difference = MathHelper.wrapDegrees(target - current);
      return Math.abs(difference) <= speed ? target : current + Math.signum(difference) * speed;
   }

   private static boolean onTarget(ClientPlayerEntity player, LivingEntity target, float yaw, float pitch) {
      Vec3d eye = player.getEyePos();
      Box box = target.getBoundingBox().expand((double)target.getTargetingMargin() + 0.02);
      double reach = eye.distanceTo(box.getCenter()) + 1.0;
      Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(reach));
      return box.raycast(eye, end).isPresent();
   }

   private static float legitRandom(float min, float max) {
      return switch (SECURE_RANDOM.nextInt(4)) {
         case 0 -> averageRandom(min, max);
         case 1 -> smoothRandom(min, max);
         case 2 -> min + (max - min) * (float)Math.random();
         default -> min + (max - min) * SECURE_RANDOM.nextFloat();
      };
   }

   private static float averageRandom(float min, float max) {
      float first = SECURE_RANDOM.nextFloat();
      float second = SECURE_RANDOM.nextFloat();
      return min + (max - min) * ((first + second) / 2.0F);
   }

   private static float smoothRandom(float min, float max) {
      double randomA = SECURE_RANDOM.nextDouble();
      double randomB = SECURE_RANDOM.nextDouble();
      double randomC = SECURE_RANDOM.nextGaussian() * 0.02F;
      double smoothFactor = Math.pow(randomA, 1.0 + SECURE_RANDOM.nextDouble() * 0.7);
      double mixFactor = (randomB * 0.8 + 0.1) * (Math.log1p(randomA * 3.0) * 0.5 + 0.5);
      return (float)((double)min + (double)(max - min) * smoothFactor * mixFactor + randomC);
   }

   private static SpookyTimeRotation.Rotation correctRotation(float fromYaw, float fromPitch, float toYaw, float toPitch) {
      float step = gcdStep();
      float deltaYaw = MathHelper.wrapDegrees(toYaw - fromYaw);
      float deltaPitch = toPitch - fromPitch;
      deltaYaw = (float)Math.round(deltaYaw / step) * step;
      deltaPitch = (float)Math.round(deltaPitch / step) * step;
      return new SpookyTimeRotation.Rotation(fromYaw + deltaYaw, MathHelper.clamp(fromPitch + deltaPitch, -90.0F, 90.0F));
   }

   private static float gcdStep() {
      double sensitivity = (Double)MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
      double factor = sensitivity * 0.6 + 0.2;
      return (float)(factor * factor * factor * 1.2);
   }

   @Environment(EnvType.CLIENT)
   private static record Rotation(float yaw, float pitch) {
   }
}
