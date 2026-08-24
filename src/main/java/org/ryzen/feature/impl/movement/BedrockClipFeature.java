package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;

@Environment(EnvType.CLIENT)
public final class BedrockClipFeature extends Feature implements PlayerContext {
   private static final double SEARCH_RANGE_SQR = 6.25;
   private static final double MOUNT_RANGE = 3.0;
   private static final double CLIP_DROP = 135.0;
   private static final long MOUNT_COOLDOWN_MS = 300L;
   private static final long NO_PHYSICS_MS = 1000L;
   private static final float AIM_TOLERANCE = 15.0F;
   public final BooleanSetting autoMount = this.register(new BooleanSetting("Auto Mount", true));
   private boolean clipping;
   private long lastMountAttemptAt;
   private long clipStartedAt;

   public BedrockClipFeature() {
      super("BedrockClip", "Rides a boat and drops through the bedrock floor", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onDisable() {
      this.stopClipping();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.stopClipping();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      ClientWorld level = client.world;
      if (player != null && level != null && client.interactionManager != null) {
         if (this.clipping) {
            if (System.currentTimeMillis() - this.clipStartedAt >= 1000L) {
               this.stopClipping();
            }
         } else {
            AbstractBoatEntity boat = nearestBoat(player, level);
            if (boat != null) {
               if (player.hasVehicle() && player.getVehicle() == boat) {
                  player.setPosition(player.getX(), player.getY() - 135.0, player.getZ());
                  player.stopRiding();
                  player.noClip = true;
                  player.fallDistance = 0.0;
                  this.clipping = true;
                  this.clipStartedAt = System.currentTimeMillis();
               } else {
                  if (this.autoMount.getValue() && (double)player.distanceTo(boat) < 3.0) {
                     this.tryMount(client, player, boat);
                  }
               }
            }
         }
      }
   }

   private void tryMount(MinecraftClient client, ClientPlayerEntity player, AbstractBoatEntity boat) {
      Vec3d aim = boat.getEntityPos().add(0.0, (double)boat.getHeight() / 2.0, 0.0).subtract(player.getEyePos());
      float yaw = MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90.0));
      float pitch = (float)(-Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))));
      if (player.networkHandler != null) {
         player.networkHandler.sendPacket(new LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
      }

      if (!(Math.abs(MathHelper.wrapDegrees(yaw - player.getYaw())) >= 15.0F) && !(Math.abs(pitch - player.getPitch()) >= 15.0F)) {
         long now = System.currentTimeMillis();
         if (now - this.lastMountAttemptAt >= 300L) {
            this.lastMountAttemptAt = now;
            client.interactionManager.interactEntity(player, boat, Hand.MAIN_HAND);
         }
      }
   }

   private static AbstractBoatEntity nearestBoat(ClientPlayerEntity player, ClientWorld level) {
      AbstractBoatEntity nearest = null;
      double nearestDistance = Double.MAX_VALUE;

      for (Entity entity : level.getEntities()) {
         if (entity instanceof AbstractBoatEntity) {
            AbstractBoatEntity boat = (AbstractBoatEntity)entity;
            double distance = player.squaredDistanceTo(boat);
            if (distance < 6.25 && distance < nearestDistance) {
               nearestDistance = distance;
               nearest = boat;
            }
         }
      }

      return nearest;
   }

   private void stopClipping() {
      ClientPlayerEntity player = this.localPlayer();
      if (player != null) {
         player.noClip = false;
      }

      this.clipping = false;
      this.clipStartedAt = 0L;
      this.lastMountAttemptAt = 0L;
   }
}
