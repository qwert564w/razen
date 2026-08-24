package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.ryzen.event.events.input.MouseInputEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.InputBindSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.TimerUtil;

@Environment(EnvType.CLIENT)
public final class TimerFeature extends Feature implements PlayerContext {
   private static final String MODE_MATRIX = "Matrix";
   private static final String MODE_GRIM = "Grim";
   private static final float MAX_BUDGET = 100.0F;
   private static final float AIRBORNE_GAIN = 0.1F;
   private static final float GRIM_GAIN = 0.05F;
   private static final float IDLE_DRAIN_GRIM = 0.05F;
   private static final float IDLE_DRAIN_EXTRA = 0.4F;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Matrix", "Matrix", "Grim"));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 2.0, 1.0, 10.0, 0.01, "x"));
   public final InputBindSetting burstKey = this.register(new InputBindSetting("Burst Key", -1).visibleWhen(() -> this.mode.is("Grim")));
   public final BooleanSetting smart = this.register(new BooleanSetting("Smart", true));
   public final BooleanSetting gainWhileMoving = this.register(new BooleanSetting("Gain While Moving", false).visibleWhen(() -> !this.mode.is("Grim")));
   public final NumberSetting gainAmount = this.register(new NumberSetting("Gain", 0.02, 0.01, 0.5, 0.01, "").visibleWhen(this.gainWhileMoving::getValue));
   public final NumberSetting drain = this.register(new NumberSetting("Drain", 1.0, 0.15, 3.0, 0.1, "").visibleWhen(() -> !this.mode.is("Grim")));
   private float budget = 100.0F;
   private boolean burstArmed;
   private boolean timerActive;
   private double lastX;
   private double lastY;
   private double lastZ;
   private float lastYaw;
   private float lastPitch;

   public TimerFeature() {
      super("Timer", "Speeds up the client clock from a tick budget", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onEnable() {
      this.refillForMode();
      this.releaseTimer();
      this.capturePosition(this.localPlayer());
   }

   @Override
   protected void onDisable() {
      this.refillForMode();
      this.releaseTimer();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.refillForMode();
      this.releaseTimer();
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (this.mode.is("Grim") && event.getAction() == 1 && this.burstKey.matches(event.getKey())) {
         this.burstArmed = true;
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (this.mode.is("Grim") && event.getAction() == 1 && this.burstKey.matchesMouse(event.getButton())) {
         this.burstArmed = true;
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (this.mode.is("Grim") && event.getPhase() == PacketReceiveEvent.Phase.PRE && this.burstArmed) {
         ClientPlayerEntity player = this.localPlayer();
         if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            this.endBurst();
         } else {
            if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet && player != null && packet.getEntityId() == player.getId()) {
               this.endBurst();
            }
         }
      }
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre()) {
         ClientPlayerEntity player = event.getPlayer();
         if (player != null) {
            boolean grim = this.mode.is("Grim");
            if (!player.isOnGround() && (!grim || !this.burstArmed)) {
               this.budget = MathHelper.clamp(this.budget + 0.1F, 0.0F, this.ceiling(grim));
            }

            this.accountMovement(player, grim);
            if (grim && !this.burstArmed) {
               this.releaseTimer();
            } else {
               float multiplier = this.speed.getValue().floatValue();
               this.setTimer(multiplier);
               if (this.smart.getValue() && !(multiplier <= 1.0F)) {
                  if (this.budget < 100.0F / multiplier) {
                     this.budget = MathHelper.clamp(this.budget + (grim ? 0.05F : this.drain.getValue().floatValue()), 0.0F, this.ceiling(grim));
                  } else {
                     this.endBurst();
                  }
               }
            }
         }
      }
   }

   private void accountMovement(ClientPlayerEntity player, boolean grim) {
      boolean stationary = this.lastX == player.getX()
         && this.lastY == player.getY()
         && this.lastZ == player.getZ()
         && this.lastYaw == player.getYaw()
         && this.lastPitch == player.getPitch();
      if (stationary) {
         this.budget = this.budget - (grim ? 0.05F : this.drain.getValue().floatValue() + 0.4F);
      } else if (this.gainWhileMoving.getValue() && !grim) {
         this.budget = this.budget - this.gainAmount.getValue().floatValue();
      }

      this.budget = MathHelper.clamp(this.budget, 0.0F, 100.0F);
      this.capturePosition(player);
   }

   private void endBurst() {
      this.burstArmed = false;
      this.releaseTimer();
   }

   private void refillForMode() {
      this.budget = this.mode.is("Grim") ? 100.0F / this.speed.getValue().floatValue() : 100.0F;
      this.burstArmed = false;
   }

   private float ceiling(boolean grim) {
      return grim ? 100.0F : 100.0F / this.speed.getValue().floatValue();
   }

   private void capturePosition(ClientPlayerEntity player) {
      if (player != null) {
         this.lastX = player.getX();
         this.lastY = player.getY();
         this.lastZ = player.getZ();
         this.lastYaw = player.getYaw();
         this.lastPitch = player.getPitch();
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
}
