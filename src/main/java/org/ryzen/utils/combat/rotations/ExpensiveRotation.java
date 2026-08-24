package org.ryzen.utils.combat.rotations;

import java.security.SecureRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

@Environment(EnvType.CLIENT)
public final class ExpensiveRotation implements AuraRotation {
   private static final SecureRandom SECURE_RANDOM = new SecureRandom();
   private final float minFactor;
   private final float maxFactor;
   private final float recoilFactor;
   private final long recoilWindowMs;
   private final boolean snapOnAttack;
   private long lastAttackMs;

   public static ExpensiveRotation grim() {
      return new ExpensiveRotation(0.4F, 0.7F, -0.2F, 50L, false);
   }

   public static ExpensiveRotation funTime() {
      return new ExpensiveRotation(0.2F, 0.4F, -0.2F, 500L, true);
   }

   public static ExpensiveRotation spookyTime() {
      return new ExpensiveRotation(0.3F, 0.5F, -0.2F, 500L, true);
   }

   private ExpensiveRotation(float minFactor, float maxFactor, float recoilFactor, long recoilWindowMs, boolean snapOnAttack) {
      this.minFactor = minFactor;
      this.maxFactor = maxFactor;
      this.recoilFactor = recoilFactor;
      this.recoilWindowMs = recoilWindowMs;
      this.snapOnAttack = snapOnAttack;
   }

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
      if (attackLikely && this.snapOnAttack) {
         RotationContext.setRotation(wantedYaw, MathHelper.clamp(wantedPitch, -90.0F, 90.0F));
      } else {
         float factor = this.factor(attackLikely);
         RotationContext.setRotation(currentYaw + yawDiff * factor, MathHelper.clamp(currentPitch + pitchDiff * factor, -90.0F, 90.0F));
      }
   }

   private float factor(boolean attackLikely) {
      if (attackLikely) {
         return 1.0F;
      } else {
         return System.currentTimeMillis() - this.lastAttackMs < this.recoilWindowMs
            ? this.recoilFactor
            : MathHelper.lerp(SECURE_RANDOM.nextFloat(), this.minFactor, this.maxFactor);
      }
   }

   @Override
   public void onAttack() {
      this.lastAttackMs = System.currentTimeMillis();
   }

   @Override
   public void reset() {
      this.lastAttackMs = 0L;
   }
}
