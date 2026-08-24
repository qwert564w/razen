package org.ryzen.utils.combat.rotations;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

@Environment(EnvType.CLIENT)
public final class GrimRotation extends MatrixVulcanRotation {
   private static final int PRE_ATTACK_TICKS = 2;
   private static final int POST_ATTACK_TICKS = 2;
   private int preAttackTicks;
   private int postAttackTicks;

   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      if (attackLikely) {
         this.preAttackTicks = 2;
      }

      if (this.preAttackTicks <= 0 && this.postAttackTicks <= 0) {
         RotationContext.clear();
      } else {
         this.rotateDirect(player, targetEyePos);
      }

      if (this.preAttackTicks > 0) {
         this.preAttackTicks--;
      }

      if (this.postAttackTicks > 0) {
         this.postAttackTicks--;
      }
   }

   @Override
   public void onAttack() {
      this.preAttackTicks = 0;
      this.postAttackTicks = 2;
   }

   @Override
   public void reset() {
      this.preAttackTicks = 0;
      this.postAttackTicks = 0;
   }
}
