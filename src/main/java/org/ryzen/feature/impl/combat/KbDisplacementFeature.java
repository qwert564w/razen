package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.AttackEvent;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;

@Environment(EnvType.CLIENT)
public final class KbDisplacementFeature extends Feature {
   private static final double PITCH_JITTER_CHANCE = 0.01;
   private static final float PITCH_JITTER = 0.5F;
   public final BooleanSetting fakeSprint = this.register(new BooleanSetting("Fake Sprint", false));
   private boolean restoreLook;
   private boolean resendSprint;
   private Entity delayedTarget;
   private int delayTicks;
   private boolean replaying;

   public KbDisplacementFeature() {
      super("KBDisplacement", "Aims the knockback you deal by driving sprint and look around the hit", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.reset();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null) {
         if (!this.fakeSprint.getValue()) {
            forceSprint(client, player);
            this.releaseDelayedAttack(client, player);
         }

         if (this.restoreLook) {
            this.restoreLook = false;
            sendLook(player, player.getYaw(), player.getPitch());
         }

         if (this.resendSprint) {
            this.resendSprint = false;
            sendSprint(player, Mode.START_SPRINTING);
         }
      }
   }

   @EventTarget
   public void onAttack(AttackEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      Entity target = client.targetedEntity;
      if (player != null && client.world != null && target != null && !event.isCancelled()) {
         if (this.fakeSprint.getValue()) {
            if (player.isSprinting()) {
               sendSprint(player, Mode.STOP_SPRINTING);
            }

            sendSprint(player, Mode.START_SPRINTING);
            if (player.isSprinting()) {
               this.resendSprint = true;
            }

            this.aimAt(player, target);
         } else {
            boolean wasSprinting = player.isSprinting();
            forceSprint(client, player);
            if (!this.replaying && !wasSprinting && canSprint(client, player)) {
               this.delayedTarget = target;
               this.delayTicks = 1;
               event.cancel();
            } else if (player.isSprinting()) {
               this.aimAt(player, target);
            }
         }
      }
   }

   private void releaseDelayedAttack(MinecraftClient client, ClientPlayerEntity player) {
      if (this.delayedTarget != null && client.interactionManager != null) {
         if (!this.delayedTarget.isAlive() || !canSprint(client, player)) {
            this.delayedTarget = null;
            this.delayTicks = 0;
         } else if (this.delayTicks > 0) {
            this.delayTicks--;
         } else {
            Entity target = this.delayedTarget;
            this.delayedTarget = null;
            this.replaying = true;

            try {
               client.interactionManager.attackEntity(player, target);
               player.swingHand(Hand.MAIN_HAND);
            } finally {
               this.replaying = false;
            }
         }
      }
   }

   private void aimAt(ClientPlayerEntity player, Entity target) {
      Vec3d delta = target.getEntityPos().subtract(player.getEntityPos());
      float yaw = MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
      float pitch = player.getPitch();
      if (Math.random() < 0.01) {
         pitch += 0.5F;
      }

      sendLook(player, yaw, pitch);
      this.restoreLook = true;
   }

   private static void forceSprint(MinecraftClient client, ClientPlayerEntity player) {
      client.options.sprintKey.setPressed(true);
      if (!player.isSprinting() && canSprint(client, player)) {
         player.setSprinting(true);
      }
   }

   private static boolean canSprint(MinecraftClient client, ClientPlayerEntity player) {
      return player.input != null
         && player.input.playerInput.forward()
         && !player.horizontalCollision
         && !player.isSneaking()
         && !player.isTouchingWater()
         && !player.isInLava()
         && !player.isSubmergedInWater()
         && (player.getHungerManager().getFoodLevel() > 6 || player.getAbilities().allowFlying);
   }

   private static void sendLook(ClientPlayerEntity player, float yaw, float pitch) {
      if (player.networkHandler != null) {
         player.networkHandler.sendPacket(new LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
      }
   }

   private static void sendSprint(ClientPlayerEntity player, Mode action) {
      if (player.networkHandler != null) {
         player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, action));
      }
   }

   private void reset() {
      this.restoreLook = false;
      this.resendSprint = false;
      this.delayedTarget = null;
      this.delayTicks = 0;
      this.replaying = false;
   }
}
