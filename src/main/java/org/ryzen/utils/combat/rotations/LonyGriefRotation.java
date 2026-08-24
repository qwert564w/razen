package org.ryzen.utils.combat.rotations;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

@Environment(EnvType.CLIENT)
public final class LonyGriefRotation implements AuraRotation {
   private static final float BUDGET = 40.0F;

   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      Vec3d aimPoint = target == null ? targetEyePos : target.getBoundingBox().getCenter();
      Vec3d delta = aimPoint.subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float wantedYaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float wantedPitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      float currentYaw = RotationContext.isActive() ? RotationContext.getFreeYaw() : player.getYaw();
      float currentPitch = RotationContext.isActive() ? RotationContext.getFreePitch() : player.getPitch();
      float yaw = currentYaw + MathHelper.clamp(MathHelper.wrapDegrees(wantedYaw - currentYaw), -40.0F, 40.0F);
      float pitch = currentPitch + MathHelper.clamp(wantedPitch - currentPitch, -40.0F, 40.0F);
      RotationContext.setRotation(yaw, MathHelper.clamp(pitch, -89.0F, 89.0F));
   }
}
