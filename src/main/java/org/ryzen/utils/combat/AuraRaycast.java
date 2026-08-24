package org.ryzen.utils.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class AuraRaycast {
   private static final float PARTIAL_TICK = 1.0F;
   private static final double EPSILON = 1.0E-7;

   private AuraRaycast() {
   }

   public static boolean rotationIntersectsTarget(
      ClientPlayerEntity player, LivingEntity target, float yaw, float pitch, double maxReach, double minReach, double margin, boolean throughWalls
   ) {
      Vec3d eye = player.getCameraPosVec(1.0F);
      Vec3d direction = Vec3d.fromPolar(pitch, yaw);
      double castDistance = maxReach + margin;
      Vec3d end = eye.add(direction.multiply(castDistance));
      Box box = target.getBoundingBox().expand((double)target.getTargetingMargin());
      if (box.squaredMagnitude(eye) <= 1.0E-7) {
         return minReach - margin <= 1.0E-7;
      } else {
         Optional<Vec3d> clip = box.raycast(eye, end);
         if (clip.isEmpty()) {
            return false;
         } else {
            Vec3d hit = clip.get();
            double distance = hit.distanceTo(eye);
            return !(distance > castDistance + 1.0E-7) && !(distance < minReach - margin - 1.0E-7) ? throughWalls || isBlockPathClear(player, eye, hit) : false;
         }
      }
   }

   public static boolean canSeeTarget(ClientPlayerEntity player, LivingEntity target, double range) {
      return findAimPoint(player, target, target.getEntityPos(), range, false, Vec3d.ZERO) != null;
   }

   public static Vec3d findAimPoint(ClientPlayerEntity player, LivingEntity target, Vec3d predictedPosition, double range, boolean throughWalls, Vec3d jitter) {
      Vec3d eye = player.getCameraPosVec(1.0F);
      Vec3d preference = eye.add(jitter);
      Vec3d movement = predictedPosition.subtract(target.getEntityPos());
      Box box = target.getBoundingBox().offset(movement).expand((double)target.getTargetingMargin());
      double rangeSqr = range * range;
      if (box.squaredMagnitude(eye) <= 1.0E-7) {
         Vec3d center = box.getCenter();
         if (center.squaredDistanceTo(eye) <= 1.0E-7) {
            center = eye.add(Vec3d.fromPolar(player.getPitch(), player.getYaw()).multiply(0.01));
         }

         return center;
      } else {
         List<Vec3d> candidates = aimCandidates(box, eye, predictedPosition.y + (double)target.getStandingEyeHeight());
         candidates.sort(Comparator.comparingDouble(preference::squaredDistanceTo));

         for (Vec3d candidate : candidates) {
            if (!(candidate.squaredDistanceTo(eye) > rangeSqr + 1.0E-7) && (throughWalls || isBlockPathClear(player, eye, candidate))) {
               return candidate;
            }
         }

         return null;
      }
   }

   public static double predictedHitboxDistanceSqr(ClientPlayerEntity player, LivingEntity target, Vec3d predictedPosition) {
      Vec3d movement = predictedPosition.subtract(target.getEntityPos());
      Box box = target.getBoundingBox().offset(movement).expand((double)target.getTargetingMargin());
      return box.squaredMagnitude(player.getCameraPosVec(1.0F));
   }

   private static boolean isBlockPathClear(ClientPlayerEntity player, Vec3d from, Vec3d to) {
      BlockHitResult blockHit = player.getEntityWorld().raycast(new RaycastContext(from, to, ShapeType.OUTLINE, FluidHandling.NONE, player));
      return blockHit.getType() == Type.MISS || blockHit.getPos().squaredDistanceTo(from) + 1.0E-7 >= to.squaredDistanceTo(from);
   }

   private static List<Vec3d> aimCandidates(Box box, Vec3d eye, double predictedEyeY) {
      List<Vec3d> points = new ArrayList<>(30);
      points.add(
         new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ))
      );
      points.add(box.getCenter());
      points.add(new Vec3d((box.minX + box.maxX) * 0.5, MathHelper.clamp(predictedEyeY, box.minY, box.maxY), (box.minZ + box.maxZ) * 0.5));
      double[] factors = new double[]{0.1, 0.5, 0.9};

      for (double x : factors) {
         for (double y : factors) {
            for (double z : factors) {
               points.add(new Vec3d(MathHelper.lerp(x, box.minX, box.maxX), MathHelper.lerp(y, box.minY, box.maxY), MathHelper.lerp(z, box.minZ, box.maxZ)));
            }
         }
      }

      return points;
   }
}
