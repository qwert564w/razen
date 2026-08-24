package org.ryzen.feature.impl.pve;

import java.util.EnumSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldJoinEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.feature.setting.TextSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;

@Environment(EnvType.CLIENT)
public final class AntiAfkFeature extends PveFeature {
   private static final float TURN_DEGREES = 3.0F;
   private static final float ROTATION_EPSILON = 0.01F;
   public final NumberSetting actionInterval = this.register(new NumberSetting("Action Interval", 20.0, 1.0, 300.0, 1.0, " s"));
   public final BooleanSetting turnHead = this.register(new BooleanSetting("Turn Head", true));
   public final BooleanSetting jump = this.register(new BooleanSetting("Jump", true));
   public final BooleanSetting sendChatMessage = this.register(new BooleanSetting("Send Chat Message", false));
   public final TextSetting chatText = this.register(new TextSetting("Chat Text", "Still here", 256).visibleWhen(this.sendChatMessage::getValue));
   private final AntiAfkActionTimer timer = new AntiAfkActionTimer();
   private ClientWorld observedLevel;
   private boolean orientationKnown;
   private float lastYaw;
   private float lastPitch;
   private float turnDirection = 1.0F;

   public AntiAfkFeature() {
      super("AntiAFK", "Performs small idle actions at a configurable interval", -1, AutomationPriority.BACKGROUND);
   }

   @Override
   protected void onPveEnable() {
      this.reset(null);
   }

   @Override
   protected void onPveDisable() {
      this.reset(null);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      ClientWorld level = client.world;
      if (player == null || level == null) {
         this.reset(null);
      } else if (level != this.observedLevel) {
         this.reset(level);
         this.rememberOrientation(player);
      } else if (client.currentScreen != null) {
         this.timer.reset();
         this.rememberOrientation(player);
      } else {
         this.timer.advance();
         if (this.hasManualActivity(client, player)) {
            this.timer.reset();
         } else if (!this.hasConfiguredAction()) {
            this.timer.reset();
         } else {
            long intervalTicks = AntiAfkActionTimer.secondsToTicks(this.actionInterval.getValue());
            if (this.timer.isDue(intervalTicks) && !this.automationIsBusy()) {
               boolean shouldTurn = this.turnHead.getValue();
               boolean shouldJump = this.jump.getValue() && this.canJumpSafely(player);
               String message = this.chatText.getValue().trim();
               boolean shouldChat = this.sendChatMessage.getValue() && !message.isEmpty();
               EnumSet<AutomationResource> resources = EnumSet.noneOf(AutomationResource.class);
               if (shouldTurn) {
                  resources.add(AutomationResource.ROTATION);
               }

               if (shouldJump) {
                  resources.add(AutomationResource.MOVEMENT);
               }

               if (shouldChat) {
                  resources.add(AutomationResource.CHAT);
               }

               if (!resources.isEmpty() && PveAutomationCoordinator.INSTANCE.acquire(this, AutomationPriority.BACKGROUND, resources)) {
                  try {
                     if (shouldTurn) {
                        this.turnSlightly(player);
                     }

                     if (shouldJump) {
                        player.jump();
                     }

                     if (shouldChat) {
                        player.networkHandler.sendChatMessage(message);
                     }

                     this.timer.actionPerformed();
                  } finally {
                     PveAutomationCoordinator.INSTANCE.release(this);
                  }
               }
            }
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.reset(null);
   }

   @EventTarget
   public void onWorldJoin(WorldJoinEvent event) {
      this.reset(event.getLevel());
   }

   private boolean hasManualActivity(MinecraftClient client, ClientPlayerEntity player) {
      boolean keyActivity = client.options.forwardKey.isPressed()
         || client.options.backKey.isPressed()
         || client.options.leftKey.isPressed()
         || client.options.rightKey.isPressed()
         || client.options.jumpKey.isPressed()
         || client.options.sneakKey.isPressed()
         || client.options.sprintKey.isPressed()
         || client.options.attackKey.isPressed()
         || client.options.useKey.isPressed();
      if (!this.orientationKnown) {
         this.rememberOrientation(player);
         return keyActivity;
      } else {
         boolean rotationActivity = Math.abs(MathHelper.wrapDegrees(player.getYaw() - this.lastYaw)) > 0.01F
            || Math.abs(player.getPitch() - this.lastPitch) > 0.01F;
         this.rememberOrientation(player);
         return keyActivity || rotationActivity;
      }
   }

   private boolean automationIsBusy() {
      PveAutomationCoordinator coordinator = PveAutomationCoordinator.INSTANCE;
      return coordinator.isClaimedByOther(this, AutomationResource.MOVEMENT)
         || coordinator.isClaimedByOther(this, AutomationResource.ROTATION)
         || coordinator.isClaimedByOther(this, AutomationResource.CHAT);
   }

   private boolean hasConfiguredAction() {
      return this.turnHead.getValue() || this.jump.getValue() || this.sendChatMessage.getValue() && !this.chatText.getValue().isBlank();
   }

   private boolean canJumpSafely(ClientPlayerEntity player) {
      return player.isAlive()
         && player.isOnGround()
         && !player.hasVehicle()
         && !player.isInSneakingPose()
         && !player.isSpectator()
         && !player.getAbilities().flying
         && !player.isGliding()
         && !player.isTouchingWater()
         && !player.isInLava()
         && !player.isClimbing();
   }

   private void turnSlightly(ClientPlayerEntity player) {
      float yaw = MathHelper.wrapDegrees(player.getYaw() + 3.0F * this.turnDirection);
      this.turnDirection = -this.turnDirection;
      player.setYaw(yaw);
      player.setHeadYaw(yaw);
      this.rememberOrientation(player);
   }

   private void rememberOrientation(ClientPlayerEntity player) {
      this.lastYaw = player.getYaw();
      this.lastPitch = player.getPitch();
      this.orientationKnown = true;
   }

   private void reset(ClientWorld level) {
      this.observedLevel = level;
      this.timer.reset();
      this.orientationKnown = false;
      this.lastYaw = 0.0F;
      this.lastPitch = 0.0F;
      this.turnDirection = 1.0F;
   }
}
