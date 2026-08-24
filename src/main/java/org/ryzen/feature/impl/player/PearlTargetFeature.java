package org.ryzen.feature.impl.player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.ryzen.context.MinecraftContext;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.combat.AuraFeature;
import org.ryzen.feature.setting.BooleanSetting;

@Environment(EnvType.CLIENT)
public final class PearlTargetFeature extends Feature implements MinecraftContext {
   private static final float ROTATION_TOLERANCE = 1.0F;
   private static final double THROW_SPEED = 1.5;
   private static final double AIR_INERTIA = 0.99;
   private static final double WATER_INERTIA = 0.8;
   private static final double THROWABLE_GRAVITY = 0.03;
   private static final double WATER_GRAVITY = 0.015707963267948967;
   private static final int MAX_SIMULATION_TICKS = 600;
   private static final double ENTITY_EXPAND = 0.3;
   private static final double VELOCITY_LEAD_FACTOR = 0.02;
   private static final float PITCH_MIN = -89.0F;
   private static final float PITCH_MAX = 89.0F;
   public final BooleanSetting onlyAuraTarget = this.register(new BooleanSetting("Only Aura Target", false));
   private final Set<UUID> seenPearls = new HashSet<>();
   private UUID auraTargetId;
   private UUID armedTargetId;
   private Vec3d aimPoint;
   private boolean armed;

   public PearlTargetFeature() {
      super("PearlTarget", "Throws an ender pearl after the aura target's pearl", FeatureCategory.PLAYER, -1);
   }

   @Override
   protected void onDisable() {
      this.reset();
      this.auraTargetId = null;
      this.seenPearls.clear();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.reset();
      this.auraTargetId = null;
      this.seenPearls.clear();
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPost()) {
         ClientPlayerEntity player = mc.player;
         if (player != null && mc.world != null) {
            LivingEntity target = this.resolveTarget(player);
            if (target != null && target.isAlive()) {
               if (!this.armed) {
                  Vec3d landing = this.trackIncomingPearl(player, target);
                  if (landing == null) {
                     return;
                  }

                  this.armed = true;
                  this.armedTargetId = target.getUuid();
                  this.aimPoint = landing;
               }

               if (this.armedTargetId == null || !this.armedTargetId.equals(target.getUuid()) || this.aimPoint == null) {
                  this.reset();
               } else if (!player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack()) && this.hasPearl(player)) {
                  Vec3d aim = this.adjustAim(player, this.aimPoint, target, null);
                  float[] rotation = this.solveThrow(player, aim, target);
                  RotationContext.setRotation(rotation[0], rotation[1]);
                  float yawDiff = Math.abs(MathHelper.wrapDegrees(rotation[0] - player.getYaw()));
                  float pitchDiff = Math.abs(rotation[1] - player.getPitch());
                  if (!(yawDiff > 1.0F) && !(pitchDiff > 1.0F)) {
                     this.throwPearl(player);
                     this.reset();
                  }
               } else {
                  this.reset();
               }
            } else {
               if (this.armed) {
                  this.reset();
               }
            }
         } else {
            this.reset();
         }
      }
   }

   private LivingEntity resolveTarget(ClientPlayerEntity player) {
      AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
      LivingEntity auraTarget = aura == null ? null : aura.getCurrentTarget();
      if (this.onlyAuraTarget.getValue()) {
         if (auraTarget != null && auraTarget.isAlive()) {
            this.auraTargetId = auraTarget.getUuid();
            return auraTarget;
         } else {
            this.auraTargetId = null;
            return null;
         }
      } else {
         if (this.armedTargetId != null) {
            LivingEntity entity = this.byUuid(this.armedTargetId);
            if (entity != null && entity.isAlive()) {
               return entity;
            }
         }

         if (auraTarget != null && auraTarget.isAlive()) {
            this.auraTargetId = auraTarget.getUuid();
            return auraTarget;
         } else {
            if (this.auraTargetId != null) {
               LivingEntity entity = this.byUuid(this.auraTargetId);
               if (entity != null && entity.isAlive()) {
                  return entity;
               }
            }

            return null;
         }
      }
   }

   private LivingEntity byUuid(UUID id) {
      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof LivingEntity living && entity.getUuid().equals(id)) {
            return living;
         }
      }

      return null;
   }

   private boolean hasPearl(ClientPlayerEntity player) {
      if (!player.getMainHandStack().isOf(Items.ENDER_PEARL) && !player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
         for (int slot = 0; slot < 36; slot++) {
            if (player.getInventory().getStack(slot).isOf(Items.ENDER_PEARL)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private Vec3d trackIncomingPearl(ClientPlayerEntity player, LivingEntity target) {
      if (this.seenPearls.size() > 256) {
         this.seenPearls.clear();
      }

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof EnderPearlEntity) {
            EnderPearlEntity pearl = (EnderPearlEntity)entity;
            if (pearl.age <= 2) {
               UUID id = pearl.getUuid();
               if (!this.seenPearls.contains(id)) {
                  Entity owner = pearl.getOwner();
                  if (owner != null && owner.getUuid().equals(target.getUuid())) {
                     this.seenPearls.add(id);
                     return this.adjustAim(player, this.simulate(player, pearl.getEntityPos(), pearl.getVelocity(), pearl), target, pearl.getVelocity());
                  }
               }
            }
         }
      }

      return null;
   }

   private Vec3d simulate(ClientPlayerEntity player, Vec3d start, Vec3d velocity, Entity thrower) {
      Vec3d pos = start;
      Vec3d vel = velocity;

      for (int step = 0; step <= 600; step++) {
         Vec3d next = pos.add(vel);
         HitResult block = mc.world.getCollisionsIncludingWorldBorder(new RaycastContext(pos, next, ShapeType.COLLIDER, FluidHandling.NONE, player));
         Vec3d end = block.getType() != Type.MISS ? block.getPos() : next;
         Box box = new Box(pos, end).expand(0.3);
         EntityHitResult hit = ProjectileUtil.raycast(
            thrower,
            pos,
            end,
            box,
            candidate -> EntityPredicates.CAN_HIT.test(candidate) && candidate != thrower && candidate != player,
            pos.squaredDistanceTo(end)
         );
         if (hit != null) {
            return hit.getPos();
         }

         if (block.getType() != Type.MISS) {
            return block.getPos();
         }

         pos = next;
         vel = this.applyDrag(next, vel);
      }

      return pos;
   }

   private Vec3d applyDrag(Vec3d pos, Vec3d vel) {
      return this.inWater(pos) ? vel.multiply(0.8).subtract(0.0, 0.015707963267948967, 0.0) : vel.multiply(0.99).subtract(0.0, 0.03, 0.0);
   }

   private boolean inWater(Vec3d pos) {
      return mc.world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
   }

   private Vec3d predictTargetPos(ClientPlayerEntity player, LivingEntity target) {
      Vec3d velocity = target.getVelocity();
      double lead = Math.min(0.35 + target.squaredDistanceTo(player) * 0.02, 1.0);
      return target.getEntityPos().add(0.0, (double)target.getHeight() * 0.5, 0.0).add(velocity.x * lead, velocity.y * lead, velocity.z * lead);
   }

   private Vec3d adjustAim(ClientPlayerEntity player, Vec3d landing, LivingEntity target, Vec3d pearlVelocity) {
      if (target == null || landing == null) {
         return landing;
      } else {
         return !this.nearTarget(landing, target, pearlVelocity) && !this.nearSelf(player, landing) ? landing : this.predictTargetPos(player, target);
      }
   }

   private boolean nearTarget(Vec3d landing, LivingEntity target, Vec3d pearlVelocity) {
      if (target.getBoundingBox().expand(0.35, 0.15, 0.35).contains(landing)) {
         return true;
      } else {
         Vec3d targetPos = target.getEntityPos();
         double horizontal = Math.hypot(landing.x - targetPos.x, landing.z - targetPos.z);
         double dy = landing.y - targetPos.y;
         if (horizontal <= 2.0 && dy >= -0.35 && dy <= 1.5) {
            return true;
         } else {
            return pearlVelocity == null ? false : Math.hypot(pearlVelocity.x, pearlVelocity.z) < 0.35 && pearlVelocity.y < -0.15 && horizontal <= 2.5;
         }
      }
   }

   private boolean nearSelf(ClientPlayerEntity player, Vec3d landing) {
      Vec3d playerPos = player.getEntityPos();
      double horizontal = Math.hypot(landing.x - playerPos.x, landing.z - playerPos.z);
      double dy = landing.y - playerPos.y;
      return horizontal <= 1.75 && dy >= -0.35 && dy <= 1.5;
   }

   private float[] solveThrow(ClientPlayerEntity player, Vec3d aim, LivingEntity target) {
      Vec3d eye = player.getEyePos();
      Vec3d delta = aim.subtract(eye);
      double horizontal = Math.hypot(delta.x, delta.z);
      if (horizontal < 1.0E-4 && target != null) {
         delta = this.predictTargetPos(player, target).subtract(eye);
         horizontal = Math.hypot(delta.x, delta.z);
      }

      float yaw = MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F);
      Vec3d base = this.baseVelocity(player);
      float bestPitch = horizontal < 1.0E-4 ? 0.0F : MathHelper.clamp((float)(-Math.toDegrees(Math.atan2(delta.y, horizontal))), -89.0F, 89.0F);
      double bestDistance = Double.MAX_VALUE;
      float step = this.inLiquid(player) ? 0.5F : 1.0F;

      for (float pitch = -89.0F; pitch <= 89.0F; pitch += step) {
         Vec3d direction = directionOf(pitch, yaw);
         Vec3d velocity = direction.multiply(1.5).add(base);
         Vec3d landing = this.simulate(player, eye, velocity, player);
         double distance = landing.squaredDistanceTo(aim);
         if (distance < bestDistance) {
            bestDistance = distance;
            bestPitch = pitch;
         }
      }

      return new float[]{yaw, MathHelper.clamp(bestPitch, -89.0F, 89.0F)};
   }

   private Vec3d baseVelocity(ClientPlayerEntity player) {
      Vec3d velocity = player.getVelocity();
      return this.inLiquid(player) ? new Vec3d(velocity.x, 0.0, velocity.z) : velocity;
   }

   private boolean inLiquid(ClientPlayerEntity player) {
      return player.isTouchingWater() || player.isSubmergedInWater() || player.isInLava() || player.isSwimming();
   }

   private static Vec3d directionOf(float pitch, float yaw) {
      float pitchRad = pitch * (float) (Math.PI / 180.0);
      float yawRad = yaw * (float) (Math.PI / 180.0);
      float cosPitch = MathHelper.cos((double)pitchRad);
      return new Vec3d(
         (double)(-MathHelper.sin((double)yawRad) * cosPitch), (double)(-MathHelper.sin((double)pitchRad)), (double)(MathHelper.cos((double)yawRad) * cosPitch)
      );
   }

   private void throwPearl(ClientPlayerEntity player) {
      MinecraftClient client = mc;
      if (client.interactionManager != null) {
         if (player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            player.swingHand(Hand.MAIN_HAND);
         } else if (player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
            client.interactionManager.interactItem(player, Hand.OFF_HAND);
            player.swingHand(Hand.OFF_HAND);
         }
      }
   }

   private void reset() {
      this.armed = false;
      this.armedTargetId = null;
      this.aimPoint = null;
   }
}
