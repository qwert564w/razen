package org.ryzen.feature.impl.combat;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class TargetStrafeFeature extends Feature implements PlayerContext {
   private static final String MODE_COLLISION = "Collision";
   private static final String MODE_STEER = "Steer";
   private static final float AIR_FRICTION = 0.91F;
   private static final float AIR_DRAG = 0.99F;
   private static final double LEAD_MIN_SPEED = 4.0;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Collision", "Collision", "Steer"));
   public final NumberSetting radius = this.register(new NumberSetting("Radius", 1.5, 0.5, 5.0, 0.1, " blocks").visibleWhen(() -> this.mode.is("Collision")));
   public final NumberSetting strength = this.register(new NumberSetting("Strength", 0.05, 0.01, 0.1, 0.01, "").visibleWhen(() -> this.mode.is("Collision")));
   public final BooleanSetting lead = this.register(new BooleanSetting("Lead Target", true).visibleWhen(() -> this.mode.is("Steer")));
   private final Map<LivingEntity, Vec3d> lastPositions = new WeakHashMap<>();

   public TargetStrafeFeature() {
      super("TargetStrafe", "Circles and sticks to the aura's target", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.lastPositions.clear();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.lastPositions.clear();
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre() && this.mode.is("Collision")) {
         ClientPlayerEntity player = event.getPlayer();
         LivingEntity target = this.currentTarget();
         if (player != null && target != null && !((double)player.distanceTo(target) > this.radius.getValue())) {
            Vec3d toTarget = target.getEntityPos().subtract(player.getEntityPos());
            if (!(toTarget.horizontalLengthSquared() < 1.0E-6)) {
               Vec3d direction = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
               double push = this.strength.getValue() * closeInFalloff((double)player.distanceTo(target));
               if (!player.isOnGround()) {
                  push *= 1.0879121F;
               }

               Vec3d movement = player.getVelocity();
               player.setVelocity(movement.x + direction.x * push, movement.y, movement.z + direction.z * push);
            }
         }
      }
   }

   @EventTarget
   public void onPlayerInput(PlayerInputEvent event) {
      if (this.mode.is("Steer")) {
         ClientPlayerEntity player = this.localPlayer();
         LivingEntity target = this.currentTarget();
         if (player != null && target != null) {
            Vec3d aim = this.lead.getValue() ? this.leadPosition(target) : target.getEntityPos();
            double desiredYaw = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(aim.z - player.getZ(), aim.x - player.getX())) - 90.0);
            float yaw = player.getYaw();
            float bestForward = 0.0F;
            float bestStrafe = 0.0F;
            double bestError = Double.MAX_VALUE;

            for (float forward = -1.0F; forward <= 1.0F; forward++) {
               for (float strafe = -1.0F; strafe <= 1.0F; strafe++) {
                  if (forward != 0.0F || strafe != 0.0F) {
                     double error = Math.abs(MathHelper.wrapDegrees(desiredYaw - movementYaw(yaw, forward, strafe)));
                     if (error < bestError) {
                        bestError = error;
                        bestForward = forward;
                        bestStrafe = strafe;
                     }
                  }
               }
            }

            event.setMoveVector(new Vec2f(bestStrafe, bestForward).normalize());
            event.setDirections(bestForward > 0.0F, bestForward < 0.0F, bestStrafe > 0.0F, bestStrafe < 0.0F);
            event.setSprint(event.getKeyPresses().sprint() && bestForward > 0.0F);
         }
      }
   }

   private Vec3d leadPosition(LivingEntity target) {
      Vec3d position = target.getEntityPos();
      Vec3d previous = this.lastPositions.getOrDefault(target, position);
      this.lastPositions.put(target, position);
      double deltaX = position.x - previous.x;
      double deltaZ = position.z - previous.z;
      double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 20.0;
      double step = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      if (!(speed < 4.0) && !(step <= 0.01)) {
         double offset = speed >= 7.0 ? 1.25 : (speed >= 6.0 ? 1.0 : (speed >= 5.0 ? 0.75 : 0.5));
         return position.add(deltaX / step * offset, 0.0, deltaZ / step * offset);
      } else {
         return position;
      }
   }

   private static double closeInFalloff(double distance) {
      if (distance < 0.8) {
         return 0.3;
      } else {
         return distance < 1.0 ? 0.65 : 1.0;
      }
   }

   private static double movementYaw(float yaw, float forward, float strafe) {
      double radians = Math.toRadians((double)yaw);
      double sin = Math.sin(radians);
      double cos = Math.cos(radians);
      double dirX = (double)strafe * cos - (double)forward * sin;
      double dirZ = (double)forward * cos + (double)strafe * sin;
      return MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dirZ, dirX)) - 90.0);
   }

   private LivingEntity currentTarget() {
      AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
      if (aura == null) {
         return null;
      } else {
         LivingEntity target = aura.getCurrentTarget();
         return target != null && target.isAlive() ? target : null;
      }
   }
}
