package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.TimerUtil;

@Environment(EnvType.CLIENT)
public final class FlightFeature extends Feature implements PlayerContext {
   private static final String MODE_GLIDE = "Glide";
   private static final String MODE_NORMAL = "Normal";
   private static final String MODE_MOTION = "Motion";
   private static final String MODE_JUMP = "Jump";
   private static final String MODE_DRAGON = "Dragon";
   private static final String MODE_ELYTRA_Y = "Elytra Y";
   private static final double GLIDE_SINK = -0.005;
   private static final double DRAGON_SPEED = 0.74;
   private static final float DRAGON_MOVING_TIMER = 0.7F;
   private static final float DRAGON_IDLE_TIMER = 1.05F;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Normal", "Glide", "Normal", "Motion", "Jump", "Dragon", "Elytra Y"));
   public final NumberSetting timer = this.register(new NumberSetting("Timer", 1.5, 0.1, 10.0, 0.1, "x").visibleWhen(this::usesTimer));
   public final NumberSetting verticalSpeed = this.register(new NumberSetting("Vertical Speed", 1.5, 0.1, 10.0, 0.1, "").visibleWhen(this::usesVerticalSpeed));
   public final NumberSetting elytraSpeed = this.register(
      new NumberSetting("Elytra Y Speed", 0.1, 0.05, 0.5, 0.05, "").visibleWhen(() -> this.mode.is("Elytra Y"))
   );
   private boolean timerActive;

   public FlightFeature() {
      super("Flight", "Free vertical movement", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onDisable() {
      this.releaseTimer();
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre()) {
         MinecraftClient client = MinecraftClient.getInstance();
         ClientPlayerEntity player = event.getPlayer();
         if (player != null && player.input != null) {
            boolean down = client.options.sneakKey.isPressed();
            boolean up = client.options.jumpKey.isPressed();
            double speed = this.verticalSpeed.getValue();
            boolean hasHorizontalInput = player.input.getMovementInput().lengthSquared() > 0.0F;
            String var9 = this.mode.getValue();
            switch (var9) {
               case "Glide":
                  this.applyVertical(player, verticalOr(down, up, speed, -0.005), hasHorizontalInput, true);
                  break;
               case "Normal":
                  this.applyVertical(player, verticalOr(down, up, speed, 0.0), hasHorizontalInput, true);
                  break;
               case "Motion":
                  this.applyVertical(player, verticalOr(down, up, speed, player.getVelocity().y), hasHorizontalInput, false);
                  break;
               case "Jump":
                  this.releaseTimer();
                  if (up && player.isOnGround()) {
                     player.jump();
                  }
                  break;
               case "Dragon":
                  this.applyDragon(player, down, up);
                  break;
               case "Elytra Y":
                  this.applyElytraLift(client, player);
                  break;
               default:
                  this.releaseTimer();
            }
         } else {
            this.releaseTimer();
         }
      }
   }

   private void applyVertical(ClientPlayerEntity player, double vertical, boolean hasHorizontalInput, boolean jumpOffGround) {
      if (jumpOffGround && player.isOnGround()) {
         player.jump();
      }

      this.setTimer(this.timer.getValue().floatValue());
      Vec3d movement = player.getVelocity();
      player.setVelocity(hasHorizontalInput ? movement.x : 0.0, vertical, hasHorizontalInput ? movement.z : 0.0);
   }

   private void applyDragon(ClientPlayerEntity player, boolean down, boolean up) {
      if (!player.getAbilities().flying) {
         this.releaseTimer();
      } else {
         double vertical = down ? -0.74 : (up ? 0.74 : 0.0);
         this.setTimer(!down && !up ? 1.05F : 0.7F);
         Vec3d movement = player.getVelocity();
         player.setVelocity(movement.x, vertical, movement.z);
      }
   }

   private void applyElytraLift(MinecraftClient client, ClientPlayerEntity player) {
      this.releaseTimer();
      if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && player.isGliding()) {
         player.setVelocity(0.0, player.getVelocity().y + this.elytraSpeed.getValue(), 0.0);
         client.options.jumpKey.setPressed(false);
      }
   }

   private static double verticalOr(boolean down, boolean up, double speed, double fallback) {
      if (down) {
         return -speed;
      } else {
         return up ? speed : fallback;
      }
   }

   private void setTimer(float multiplier) {
      TimerUtil.setTimer(multiplier);
      this.timerActive = true;
   }

   private void releaseTimer() {
      if (this.timerActive) {
         TimerUtil.resetTimer();
         this.timerActive = false;
      }
   }

   private boolean usesTimer() {
      return this.mode.is("Glide") || this.mode.is("Normal") || this.mode.is("Motion");
   }

   private boolean usesVerticalSpeed() {
      return this.usesTimer();
   }
}
