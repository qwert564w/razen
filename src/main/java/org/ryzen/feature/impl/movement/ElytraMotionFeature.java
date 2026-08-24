package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.combat.AuraFeature;
import org.ryzen.feature.impl.combat.ElytraTargetFeature;
import org.ryzen.feature.setting.ModeSetting;

@Environment(EnvType.CLIENT)
public final class ElytraMotionFeature extends Feature implements MinecraftContext {
   private static final String MODE_NEW = "New";
   private static final String MODE_OLD = "Old";
   private static final double HOLD_DISTANCE = 4.0;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "New", "New", "Old"));
   private boolean frozen;
   private Vec3d savedPos;
   private Vec3d savedVelocity;

   public ElytraMotionFeature() {
      super("ElytraMotion", "Freezes elytra motion near the aura target", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         player.setNoGravity(false);
      }

      this.reset();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null) {
         if (this.mode.is("New")) {
            this.newMode(player);
         } else {
            this.oldMode(player);
         }
      }
   }

   private void newMode(ClientPlayerEntity player) {
      if (!player.isGliding()) {
         this.reset();
      } else {
         LivingEntity target = this.auraTarget();
         if (this.shouldHold(player, target)) {
            if (!this.frozen) {
               this.savedPos = player.getEntityPos();
               this.savedVelocity = player.getVelocity();
            }

            this.frozen = true;
            player.setVelocity(0.0, 0.0, 0.0);
            if (this.savedPos != null) {
               player.setPosition(this.savedPos.x, this.savedPos.y, this.savedPos.z);
            }

            player.knockedBack = true;
         } else {
            if (this.savedVelocity != null) {
               player.setVelocity(this.savedVelocity.x, 0.0, this.savedVelocity.z);
            }

            this.reset();
         }
      }
   }

   private void oldMode(ClientPlayerEntity player) {
      LivingEntity target = this.auraTarget();
      if (this.shouldHold(player, target)) {
         player.setVelocity(0.0, 0.0, 0.0);
         player.setNoGravity(true);
      } else {
         player.setNoGravity(false);
         this.reset();
      }
   }

   private boolean shouldHold(ClientPlayerEntity player, LivingEntity target) {
      if (target != null && player.isGliding()) {
         ElytraTargetFeature elytraTarget = FeatureManager.INSTANCE.getEnabled(ElytraTargetFeature.class);
         if (elytraTarget != null && elytraTarget.isChasing(target)) {
            return false;
         } else {
            Vec3d aim = target.getBoundingBox().getCenter().add(0.0, (target.getY() - target.lastRenderY) * 2.0, 0.0);
            return player.getEyePos().distanceTo(aim) < 4.0;
         }
      } else {
         return false;
      }
   }

   private LivingEntity auraTarget() {
      AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
      return aura == null ? null : aura.getCurrentTarget();
   }

   private void reset() {
      this.frozen = false;
      this.savedPos = null;
      this.savedVelocity = null;
   }
}
