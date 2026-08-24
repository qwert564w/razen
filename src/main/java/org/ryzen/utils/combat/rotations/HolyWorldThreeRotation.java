package org.ryzen.utils.combat.rotations;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

@Environment(EnvType.CLIENT)
public final class HolyWorldThreeRotation implements AuraRotation {
   private static final float YAW_EASE = 0.45F;
   private static final float PITCH_EASE = 0.35F;
   private static final float SETTLE_DEGREES = 1.5F;

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
      float yaw = attackLikely ? wantedYaw : currentYaw + ease(yawDiff, 0.45F);
      float pitch = attackLikely ? wantedPitch : currentPitch + ease(pitchDiff, 0.35F);
      RotationContext.setRotation(yaw, MathHelper.clamp(pitch, -90.0F, 90.0F));
   }

   private static float ease(float difference, float factor) {
      return Math.abs(difference) <= 1.5F ? difference : difference * factor;
   }
}
