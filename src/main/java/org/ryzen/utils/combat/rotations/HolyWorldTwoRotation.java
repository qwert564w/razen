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
public final class HolyWorldTwoRotation implements AuraRotation {
   private static final float YAW_SPEED_MIN = 50.0F;
   private static final float YAW_SPEED_MAX = 70.0F;
   private static final float PITCH_SPEED_MIN = 10.0F;
   private static final float PITCH_SPEED_MAX = 20.0F;
   private static final float ATTACK_YAW_SPEED = 70.0F;
   private static final float ATTACK_PITCH_SPEED = 24.0F;
   private static final float YAW_RANDOM = 2.0F;
   private static final float PITCH_RANDOM = 2.0F;
   private static final float OSCILLATE_X = 0.2F;
   private static final float OSCILLATE_Y = 0.3F;
   private static final float OSCILLATE_SPEED = 0.7F;
   private float oscillation;

   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      Vec3d aimPoint = target == null ? targetEyePos : target.getBoundingBox().getCenter();
      Vec3d delta = aimPoint.subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float wantedYaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float wantedPitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      this.oscillation += 0.7F;
      wantedYaw += (float)Math.sin((double)this.oscillation) * 0.2F + random(2.0F);
      wantedPitch += (float)Math.cos((double)this.oscillation) * 0.3F + random(2.0F);
      float currentYaw = RotationContext.isActive() ? RotationContext.getFreeYaw() : player.getYaw();
      float currentPitch = RotationContext.isActive() ? RotationContext.getFreePitch() : player.getPitch();
      float yawStep = attackLikely ? 70.0F : speed(50.0F, 70.0F);
      float pitchStep = attackLikely ? 24.0F : speed(10.0F, 20.0F);
      float yaw = currentYaw + clampStep(MathHelper.wrapDegrees(wantedYaw - currentYaw), yawStep);
      float pitch = MathHelper.clamp(currentPitch + clampStep(wantedPitch - currentPitch, pitchStep), -90.0F, 90.0F);
      RotationContext.setRotation(yaw, pitch);
   }

   @Override
   public void reset() {
      this.oscillation = 0.0F;
   }

   private static float clampStep(float difference, float limit) {
      return MathHelper.clamp(difference, -limit, limit);
   }

   private static float speed(float min, float max) {
      return min >= max ? min : (float)ThreadLocalRandom.current().nextDouble((double)min, (double)max);
   }

   private static float random(float range) {
      return range <= 0.0F ? 0.0F : (float)ThreadLocalRandom.current().nextDouble((double)(-range), (double)range);
   }
}
