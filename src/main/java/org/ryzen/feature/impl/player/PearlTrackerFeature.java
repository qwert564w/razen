package org.ryzen.feature.impl.player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.combat.AuraFeature;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class PearlTrackerFeature extends Feature implements PlayerContext {
   private static final int MAX_SIMULATION_TICKS = 300;
   private static final double AIR_INERTIA = 0.99;
   private static final double WATER_INERTIA = 0.8;
   private static final double THROWABLE_GRAVITY = 0.03;
   public final BooleanSetting ownPearls = this.register(new BooleanSetting("Track Own Pearls", false));
   public final BooleanSetting markAuraTarget = this.register(new BooleanSetting("Mark Aura Target", false));
   private final Set<UUID> reported = new HashSet<>();
   private Vec3d auraTargetLanding;

   public PearlTrackerFeature() {
      super("PearlTracker", "Reports where a thrown ender pearl will land", FeatureCategory.PLAYER, -1);
   }

   public static PearlTrackerFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(PearlTrackerFeature.class);
   }

   public Vec3d getAuraTargetLanding() {
      return this.markAuraTarget.getValue() ? this.auraTargetLanding : null;
   }

   @Override
   protected void onEnable() {
      this.reported.clear();
      this.auraTargetLanding = null;
   }

   @Override
   protected void onDisable() {
      this.reported.clear();
      this.auraTargetLanding = null;
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.reported.clear();
      this.auraTargetLanding = null;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      ClientWorld level = client.world;
      if (player != null && level != null) {
         for (Entity entity : level.getEntities()) {
            if (entity instanceof EnderPearlEntity) {
               EnderPearlEntity pearl = (EnderPearlEntity)entity;
               if (this.reported.add(pearl.getUuid())) {
                  PlayerEntity owner = resolveOwner(level, pearl);
                  if (owner != null && (owner != player || this.ownPearls.getValue())) {
                     Vec3d landing = simulate(level, player, pearl);
                     this.report(owner, landing);
                     this.rememberAuraTarget(owner, landing);
                  }
               }
            }
         }

         if (this.reported.size() > 256) {
            this.reported.clear();
         }
      }
   }

   private void report(PlayerEntity owner, Vec3d landing) {
      Text name = owner.getDisplayName();
      String coords = (int)landing.x + " " + (int)landing.y + " " + (int)landing.z;
      ChatUtil.info(name.getString() + Formatting.WHITE + " перлится на " + Formatting.GREEN + coords);
   }

   private void rememberAuraTarget(PlayerEntity owner, Vec3d landing) {
      if (this.markAuraTarget.getValue()) {
         AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
         LivingEntity target = aura == null ? null : aura.getCurrentTarget();
         if (target == owner) {
            this.auraTargetLanding = landing;
         }
      }
   }

   private static Vec3d simulate(ClientWorld level, ClientPlayerEntity player, EnderPearlEntity pearl) {
      Vec3d position = pearl.getEntityPos();
      Vec3d velocity = pearl.getVelocity();
      Vec3d previous = position;

      for (int step = 0; step <= 300; step++) {
         previous = position;
         position = position.add(velocity);
         boolean inWater = pearl.isTouchingWater() || level.getFluidState(BlockPos.ofFloored(position)).isIn(FluidTags.WATER);
         velocity = velocity.multiply(inWater ? 0.8 : 0.99);
         if (!pearl.hasNoGravity()) {
            velocity = new Vec3d(velocity.x, velocity.y - 0.03, velocity.z);
         }

         BlockHitResult hit = level.raycast(new RaycastContext(previous, position, ShapeType.COLLIDER, FluidHandling.NONE, player));
         if (hit.getType() == Type.BLOCK) {
            return hit.getPos();
         }

         if (position.y <= (double)level.getBottomY()) {
            return position;
         }
      }

      return previous;
   }

   private static PlayerEntity resolveOwner(ClientWorld level, EnderPearlEntity pearl) {
      Entity nearestDistance = pearl.getOwner();
      if (nearestDistance instanceof PlayerEntity) {
         return (PlayerEntity)nearestDistance;
      } else {
         PlayerEntity nearest = null;
         double nearestDistancex = Double.MAX_VALUE;

         for (PlayerEntity candidate : level.getPlayers()) {
            double distance = candidate.squaredDistanceTo(pearl);
            if (distance < nearestDistancex) {
               nearestDistancex = distance;
               nearest = candidate;
            }
         }

         return nearest;
      }
   }
}
