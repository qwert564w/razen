package org.ryzen.feature.impl.pve;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.pve.mining.MiningSessionSnapshot;

@Environment(EnvType.CLIENT)
public final class AutoTpLootFeature extends PveFeature {
   public final NumberSetting range = this.register(new NumberSetting("Range", 128.0, 4.0, 256.0, 4.0, " blocks"));
   public final NumberSetting maxSpeed = this.register(new NumberSetting("Max Speed", 35.0, 0.5, 35.0, 0.5, " blocks/tick"));
   public final NumberSetting acceleration = this.register(new NumberSetting("Acceleration", 0.5, 0.05, 1.0, 0.05, "x"));
   public final NumberSetting returnRadius = this.register(new NumberSetting("Return Radius", 0.5, 0.1, 2.0, 0.1, " blocks"));
   public final NumberSetting targetTimeout = this.register(new NumberSetting("Target Timeout", 15.0, 2.0, 60.0, 1.0, "s"));
   private final MiningSessionSnapshot snapshot = new MiningSessionSnapshot();
   private ItemEntity target;
   private Vec3d origin;
   private AutoTpLootFeature.State state = AutoTpLootFeature.State.IDLE;
   private long stateTick;
   private long tick;
   private boolean controlledVelocity;

   public AutoTpLootFeature() {
      super("AutoTpLoot", "Accelerates flight to the nearest grounded item and returns", -1, AutomationPriority.FEATURE, AutomationResource.MOVEMENT);
   }

   @Override
   protected void onPveEnable() {
      this.reset(false);
      this.snapshot.capture(MinecraftClient.getInstance().player);
   }

   @Override
   protected void onPveDisable() {
      this.cleanup();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cleanup();
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre()) {
         ClientPlayerEntity player = event.getPlayer();
         MinecraftClient client = MinecraftClient.getInstance();
         this.tick++;
         if (player != null && client.world != null && player.isAlive() && player.getAbilities().flying) {
            this.snapshot.capture(player);
            switch (this.state) {
               case IDLE:
                  this.acquireTarget(client, player);
                  break;
               case SEEKING:
                  this.seek(player);
                  break;
               case RETURNING:
                  this.returnToOrigin(player);
            }
         } else {
            this.reset(true);
         }
      }
   }

   public AutoTpLootFeature.State getState() {
      return this.state;
   }

   public ItemEntity getTarget() {
      return this.target;
   }

   private void acquireTarget(MinecraftClient client, ClientPlayerEntity player) {
      double rangeSquared = this.range.getValue() * this.range.getValue();
      double nearestDistance = rangeSquared;
      this.target = null;

      for (Entity entity : client.world.getEntities()) {
         if (entity instanceof ItemEntity) {
            ItemEntity item = (ItemEntity)entity;
            if (item.isAlive() && item.isOnGround()) {
               double distance = item.squaredDistanceTo(player);
               if (distance <= nearestDistance) {
                  nearestDistance = distance;
                  this.target = item;
               }
            }
         }
      }

      if (this.target != null) {
         this.origin = player.getEntityPos();
         this.transition(AutoTpLootFeature.State.SEEKING);
      }
   }

   private void seek(ClientPlayerEntity player) {
      long timeoutTicks = Math.max(1L, this.targetTimeout.getValue().longValue() * 20L);
      if (this.target != null && this.target.isAlive() && this.tick - this.stateTick < timeoutTicks) {
         this.accelerate(player, this.target.getEntityPos());
      } else {
         this.transition(AutoTpLootFeature.State.RETURNING);
      }
   }

   private void returnToOrigin(ClientPlayerEntity player) {
      if (this.origin == null) {
         this.reset(true);
      } else {
         double distance = player.getEntityPos().distanceTo(this.origin);
         if (distance <= this.returnRadius.getValue()) {
            this.reset(true);
         } else {
            this.accelerate(player, this.origin);
         }
      }
   }

   private void accelerate(ClientPlayerEntity player, Vec3d destination) {
      Vec3d delta = destination.subtract(player.getEntityPos());
      double distance = delta.length();
      if (distance <= 1.0E-6) {
         player.setVelocity(Vec3d.ZERO);
         this.controlledVelocity = true;
      } else {
         double speed = Math.min(this.maxSpeed.getValue(), distance * this.acceleration.getValue());
         player.setVelocity(delta.normalize().multiply(speed));
         this.controlledVelocity = true;
      }
   }

   private void transition(AutoTpLootFeature.State next) {
      this.state = next;
      this.stateTick = this.tick;
      if (next == AutoTpLootFeature.State.RETURNING) {
         this.target = null;
      }
   }

   private void reset(boolean stopMovement) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (stopMovement && this.controlledVelocity && player != null) {
         player.setVelocity(Vec3d.ZERO);
      }

      this.target = null;
      this.origin = null;
      this.state = AutoTpLootFeature.State.IDLE;
      this.stateTick = this.tick;
      this.controlledVelocity = false;
   }

   private void cleanup() {
      MinecraftClient client = MinecraftClient.getInstance();
      this.reset(true);
      this.snapshot.restore(client);
   }

   @Environment(EnvType.CLIENT)
   public static enum State {
      IDLE,
      SEEKING,
      RETURNING;
   }
}
