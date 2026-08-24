package org.ryzen.utils.combat.rotations;

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

@Environment(EnvType.CLIENT)
public final class SmoothRotation implements AuraRotation {
   private static final SecureRandom SECURE_RANDOM = new SecureRandom();
   private static final float YAW_BUDGET = 60.0F;
   private static final float PITCH_BUDGET = 40.0F;
   private static final float WOBBLE_MIN = 2.0F;
   private static final float WOBBLE_MAX = 5.0F;

   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      float currentYaw = RotationContext.isActive() ? RotationContext.getFreeYaw() : player.getYaw();
      float currentPitch = RotationContext.isActive() ? RotationContext.getFreePitch() : player.getPitch();
      Vec3d delta = targetEyePos.subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float wantedYaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float wantedPitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      float yawDiff = MathHelper.wrapDegrees(wantedYaw - currentYaw);
      float pitchDiff = wantedPitch - currentPitch;
      float hypot = (float)Math.hypot((double)Math.abs(yawDiff), (double)Math.abs(pitchDiff));
      if (hypot <= 1.0E-4F) {
         RotationContext.setRotation(wantedYaw, MathHelper.clamp(wantedPitch, -90.0F, 90.0F));
      } else {
         float yawCap = Math.abs(yawDiff / hypot) * 60.0F;
         float pitchCap = Math.abs(pitchDiff / hypot) * 40.0F;
         float steppedYaw = MathHelper.clamp(yawDiff, -yawCap, yawCap) + (attackLikely ? 0.0F : wobble());
         float steppedPitch = MathHelper.clamp(pitchDiff, -pitchCap, pitchCap);
         float yaw = (float)MathHelper.lerp(0.6 + ThreadLocalRandom.current().nextDouble() * 0.6, (double)currentYaw, (double)(currentYaw + steppedYaw));
         float pitch = (float)MathHelper.lerp(0.5 + ThreadLocalRandom.current().nextDouble() * 0.5, (double)currentPitch, (double)(currentPitch + steppedPitch));
         RotationContext.setRotation(yaw, MathHelper.clamp(pitch, -90.0F, 90.0F));
      }
   }

   private static float wobble() {
      float amplitude = MathHelper.lerp(SECURE_RANDOM.nextFloat(), 2.0F, 5.0F);
      return (float)Math.ceil((double)amplitude * Math.sin((double)System.currentTimeMillis() / 40.0));
   }
}
