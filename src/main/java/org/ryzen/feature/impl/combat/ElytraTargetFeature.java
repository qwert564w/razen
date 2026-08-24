package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class ElytraTargetFeature extends Feature implements MinecraftContext {
   private static final double OVERTAKE_DOT_MIN = -0.4;
   private static final double OVERTAKE_SPEED_SCALE = 1.25;
   private static final double MAX_AIM_DISTANCE = 2.7;
   private static final double GLIDE_DRAG = 2.7;
   private static final double AIM_HEIGHT_FRAC = 0.4;
   private static final double PITCH_HORIZONTAL = 0.35;
   private static final double TICK_VERTICAL = 0.65;
   private static final double VERTICAL_CLAMP = 0.6;
   private static final float INSTANT_ROTATION_STEP = 38.0F;
   private static final double FIREWORK_SCAN = 256.0;
   public final NumberSetting aimDistance = this.register(new NumberSetting("Aim Distance", 30.0, 5.0, 100.0, 5.0, " b"));
   public final BooleanSetting overtake = this.register(new BooleanSetting("Overtake", true));
   public final NumberSetting predictBlocks = this.register(new NumberSetting("Predict Blocks", 3.0, 1.0, 6.0, 0.1, " b").visibleWhen(this.overtake::getValue));
   public final BooleanSetting instantResponse = this.register(new BooleanSetting("Instant Response", false).visibleWhen(this.overtake::getValue));

   public ElytraTargetFeature() {
      super("ElytraTarget", "Chases a gliding player while flying an elytra", FeatureCategory.COMBAT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null) {
         LivingEntity target = this.findTarget(player);
         if (target != null) {
            this.aimAt(player, target);
            if (this.overtake.getValue()) {
               this.overtake(player, target);
            }
         }
      }
   }

   private void aimAt(ClientPlayerEntity player, LivingEntity target) {
      Vec3d aim = this.aimPoint(player, target);
      Vec3d eyes = player.getEyePos();
      if (!this.instantResponse.getValue() || !(eyes.squaredDistanceTo(aim) > 7.290000000000001)) {
         Vec3d delta = aim.subtract(eyes);
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
         float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
         if (this.instantResponse.getValue()) {
            yaw = player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yaw - player.getYaw()), -38.0F, 38.0F);
            pitch = player.getPitch() + MathHelper.clamp(pitch - player.getPitch(), -38.0F, 38.0F);
         }

         RotationContext.setRotation(yaw, pitch);
      }
   }

   private void overtake(ClientPlayerEntity player, LivingEntity target) {
      Vec3d own = player.getVelocity();
      Vec3d theirs = target.getVelocity();
      double wanted = Math.sqrt(theirs.x * theirs.x + theirs.z * theirs.z);
      double current = Math.sqrt(own.x * own.x + own.z * own.z);
      if (!(current <= 0.0) && !(current >= wanted)) {
         double scale = wanted / current * 1.25;
         player.setVelocity(own.x * scale, own.y, own.z * scale);
      }
   }

   private Vec3d aimPoint(ClientPlayerEntity player, LivingEntity target) {
      return this.overtake.getValue() && this.isTarget(player, target) && this.passesOvertakeAlignment(player, target)
         ? this.baseOffset(target)
         : this.baseBodyPoint(target);
   }

   private Vec3d baseOffset(LivingEntity target) {
      Vec3d base = this.baseBodyPoint(target);
      double blocks = this.predictBlocks.getValue();
      if (blocks <= 0.0) {
         return base;
      } else {
         Vec3d center = target.getBoundingBox().getCenter();
         double centerYOffset = center.y - target.getY();
         double tickVertical = target.getY() - target.lastRenderY;
         Vec3d velocity = target.getVelocity();
         double horizontal = Math.hypot(velocity.x, velocity.z);
         if (horizontal < 1.0E-4) {
            horizontal = Math.hypot(target.getX() - target.lastRenderX, target.getZ() - target.lastRenderZ);
         }

         if (horizontal < 1.0E-4) {
            return base;
         } else {
            double ticks = blocks / Math.max(horizontal, 0.05);
            double drag = Math.pow(0.37037037037037035, ticks);
            Vec3d extrapolated = velocity.multiply(ticks * drag * 1.25);
            double pitchRad = Math.toRadians((double)target.getPitch());
            double pitchLead = -Math.sin(pitchRad) * horizontal * 0.35;
            double verticalStep = MathHelper.clamp((tickVertical * 0.65 + pitchLead) * blocks, -0.6, 0.6);
            return base.add(extrapolated.x, extrapolated.y + centerYOffset + verticalStep, extrapolated.z);
         }
      }
   }

   private Vec3d baseBodyPoint(LivingEntity target) {
      return target.getEntityPos().add(0.0, (double)target.getHeight() * 0.4, 0.0);
   }

   private boolean passesOvertakeAlignment(ClientPlayerEntity player, LivingEntity target) {
      Vec3d toTarget = target.getEntityPos().subtract(player.getEntityPos());
      Vec3d flatToTarget = new Vec3d(toTarget.x, 0.0, toTarget.z);
      if (flatToTarget.lengthSquared() < 1.0E-6) {
         return true;
      } else {
         Vec3d own = player.getVelocity();
         Vec3d flatOwn = new Vec3d(own.x, 0.0, own.z).normalize();
         Vec3d flatTarget = new Vec3d(target.getVelocity().x, 0.0, target.getVelocity().z).normalize();
         double closing = flatOwn.dotProduct(flatTarget);
         double approach = flatOwn.dotProduct(flatToTarget.normalize());
         return closing >= -0.4 && approach >= -0.4;
      }
   }

   private LivingEntity findTarget(ClientPlayerEntity player) {
      if (mc.world != null && player.isGliding()) {
         LivingEntity best = null;
         double bestDistance = this.aimDistance.getValue() * this.aimDistance.getValue();

         for (PlayerEntity other : mc.world.getPlayers()) {
            if (other != player && this.isTarget(player, other)) {
               double distance = other.squaredDistanceTo(player);
               if (distance < bestDistance) {
                  bestDistance = distance;
                  best = other;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private boolean isTarget(ClientPlayerEntity player, LivingEntity entity) {
      return this.overtake.getValue() && player.isGliding() && entity != null && entity.isGliding() && this.hasOwnFirework(player);
   }

   public boolean isChasing(LivingEntity entity) {
      return this.isEnabled() && mc.player != null && this.isTarget(mc.player, entity);
   }

   private boolean hasOwnFirework(ClientPlayerEntity player) {
      if (mc.world == null) {
         return false;
      } else {
         for (FireworkRocketEntity rocket : mc.world.getNonSpectatingEntities(FireworkRocketEntity.class, player.getBoundingBox().expand(256.0))) {
            if (rocket.getOwner() == player) {
               return true;
            }
         }

         return false;
      }
   }
}
