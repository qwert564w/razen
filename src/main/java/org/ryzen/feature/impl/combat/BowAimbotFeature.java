package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.math.TickSimulator;

@Environment(EnvType.CLIENT)
public final class BowAimbotFeature extends Feature {
   private static final int MAX_FLIGHT_TICKS = 120;
   private static final double MAX_HIT_ERROR_SQR = 12.25;
   public final NumberSetting range = this.register(new NumberSetting("Range", 50.0, 5.0, 100.0, 5.0, " blocks"));

   public BowAimbotFeature() {
      super("BowAimbot", "Predicts target movement and arrow ballistics", FeatureCategory.COMBAT, 66);
   }

   @Override
   protected void onDisable() {
      RotationContext.clear();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient mc = event.getClient();
      ClientPlayerEntity player = mc.player;
      ClientWorld level = mc.world;
      if (player != null && level != null && player.isUsingItem() && player.getActiveItem().getItem() instanceof BowItem) {
         LivingEntity target = this.findClosestTarget(player, level);
         if (target == null) {
            RotationContext.clear();
         } else {
            float force = player.getItemUseTime(0.0F) / 20.0F;
            force = Math.min(1.0F, (force * force + force * 2.0F) / 3.0F);
            if (force < 0.1F) {
               RotationContext.clear();
            } else {
               Vec3d predictedPos = TickSimulator.getPredictedState(target, 15, level).pos;
               double targetY = predictedPos.y + (double)target.getHeight() * 0.5;
               double diffX = predictedPos.x - player.getX();
               double diffZ = predictedPos.z - player.getZ();
               double distanceXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
               float yaw = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
               double basePitch = -Math.toDegrees(Math.atan2(targetY - player.getEyeY(), distanceXZ));
               double velocity = (double)force * 3.0;
               float bestPitch = player.getPitch();
               double bestScore = Double.MAX_VALUE;
               double bestApexX = player.getX();
               double bestApexY = player.getEyeY() - 0.1;
               double bestApexZ = player.getZ();
               double bestEndX = bestApexX;
               double bestEndY = bestApexY;
               double bestEndZ = bestApexZ;

               for (float pitch = (float)basePitch + 5.0F; pitch > -85.0F; pitch -= 0.5F) {
                  double pitchRad = Math.toRadians((double)pitch);
                  double yawRad = Math.toRadians((double)yaw);
                  double motionX = -Math.sin(yawRad) * Math.cos(pitchRad) * velocity;
                  double motionY = -Math.sin(pitchRad) * velocity;
                  double motionZ = Math.cos(yawRad) * Math.cos(pitchRad) * velocity;
                  double arrowX = player.getX();
                  double arrowY = player.getEyeY() - 0.1;
                  double arrowZ = player.getZ();
                  double apexX = arrowX;
                  double apexY = arrowY;
                  double apexZ = arrowZ;
                  double closestX = arrowX;
                  double closestY = arrowY;
                  double closestZ = arrowZ;
                  double score = Double.MAX_VALUE;

                  for (int step = 0; step < 120; step++) {
                     arrowX += motionX;
                     arrowY += motionY;
                     arrowZ += motionZ;
                     if (arrowY > apexY) {
                        apexX = arrowX;
                        apexY = arrowY;
                        apexZ = arrowZ;
                     }

                     double dx = arrowX - predictedPos.x;
                     double dy = arrowY - targetY;
                     double dz = arrowZ - predictedPos.z;
                     double distanceSqr = dx * dx + dy * dy + dz * dz;
                     if (distanceSqr < score) {
                        score = distanceSqr;
                        closestX = arrowX;
                        closestY = arrowY;
                        closestZ = arrowZ;
                     }

                     motionX *= 0.99;
                     motionY = motionY * 0.99 - 0.05;
                     motionZ *= 0.99;
                     if (motionY < 0.0 && arrowY < predictedPos.y - 1.0) {
                        break;
                     }
                  }

                  if (score < bestScore) {
                     bestScore = score;
                     bestPitch = pitch;
                     bestApexX = apexX;
                     bestApexY = apexY;
                     bestApexZ = apexZ;
                     bestEndX = closestX;
                     bestEndY = closestY;
                     bestEndZ = closestZ;
                  }
               }

               if (bestScore < 12.25 && this.isPathClear(level, player, bestApexX, bestApexY, bestApexZ, bestEndX, bestEndY, bestEndZ)) {
                  RotationContext.setRotation(yaw, bestPitch);
               } else {
                  RotationContext.clear();
               }
            }
         }
      } else {
         RotationContext.clear();
      }
   }

   private boolean isPathClear(ClientWorld level, ClientPlayerEntity player, double apexX, double apexY, double apexZ, double endX, double endY, double endZ) {
      Vec3d start = new Vec3d(player.getX(), player.getEyeY() - 0.1, player.getZ());
      Vec3d apex = new Vec3d(apexX, apexY, apexZ);
      Vec3d end = new Vec3d(endX, endY, endZ);
      return start.squaredDistanceTo(apex) > 0.01 && this.isBlocked(level, player, start, apex)
         ? false
         : apex.squaredDistanceTo(end) <= 0.01 || !this.isBlocked(level, player, apex, end);
   }

   private boolean isBlocked(ClientWorld level, ClientPlayerEntity player, Vec3d from, Vec3d to) {
      return level.raycast(new RaycastContext(from, to, ShapeType.COLLIDER, FluidHandling.NONE, player)).getType() == Type.BLOCK;
   }

   private LivingEntity findClosestTarget(ClientPlayerEntity player, ClientWorld level) {
      double maxRange = this.range.getValue();
      double closestDistance = maxRange * maxRange;
      LivingEntity closest = null;

      for (Entity entity : level.getOtherEntities(player, player.getBoundingBox().expand(maxRange), this::isTarget)) {
         double distance = player.squaredDistanceTo(entity);
         if (distance < closestDistance && entity instanceof LivingEntity living) {
            closestDistance = distance;
            closest = living;
         }
      }

      return closest;
   }

   private boolean isTarget(Entity entity) {
      return entity instanceof LivingEntity && entity.isAlive() && entity.isAttackable() && !entity.isSpectator();
   }
}
