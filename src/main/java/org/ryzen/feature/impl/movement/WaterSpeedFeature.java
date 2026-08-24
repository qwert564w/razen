package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.TimerUtil;

@Environment(EnvType.CLIENT)
public final class WaterSpeedFeature extends Feature implements PlayerContext {
   private static final String MODE_SIMPLE = "Simple";
   private static final String MODE_LEGIT = "Legit";
   private static final String MODE_MOTION = "Motion";
   private static final String MODE_SWIM_BOOST = "Swim Boost";
   private static final double LEGIT_SPEED_CAP = 0.209;
   private static final double SURFACE_TOLERANCE = 0.2;
   private static final double SURFACE_LIFT = 0.2;
   private static final double SURFACE_TIMER_SCALE = 5.0;
   private static final int BOB_PERIOD = 10;
   private static final int BOB_JUMP_TICKS = 5;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Simple", "Simple", "Legit", "Motion", "Swim Boost"));
   public final NumberSetting boost = this.register(new NumberSetting("Boost", 1.05, 1.01, 1.2, 0.01, "x").visibleWhen(() -> this.mode.is("Simple")));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 0.5, 0.1, 2.0, 0.05, "").visibleWhen(() -> this.mode.is("Motion")));
   public final BooleanSetting autoBob = this.register(new BooleanSetting("Auto Jump/Sneak", false).visibleWhen(() -> this.mode.is("Legit")));
   public final BooleanSetting stayUnder = this.register(new BooleanSetting("Stay Under", false).visibleWhen(this.autoBob::getValue));
   private int waterTicks;
   private boolean bobbing;
   private boolean timerActive;

   public WaterSpeedFeature() {
      super("WaterSpeed", "Swim faster", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onEnable() {
      this.waterTicks = 0;
   }

   @Override
   protected void onDisable() {
      this.waterTicks = 0;
      this.releaseBob();
      this.releaseTimer();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null) {
         this.releaseTimer();
      } else if (!player.isTouchingWater()) {
         this.releaseBob();
         this.releaseTimer();
         this.waterTicks = 0;
      } else {
         String var4 = this.mode.getValue();
         switch (var4) {
            case "Legit":
               if (this.autoBob.getValue()) {
                  this.runBob(client, player);
               }

               if (player.isSwimming()) {
                  this.nudgeLegit(player);
               }
               break;
            case "Motion":
               if (player.isSwimming()) {
                  this.driveMotion(player);
               }
               break;
            case "Swim Boost":
               this.rideSurface(client, player);
               break;
            default:
               if (player.isSwimming() && this.isMoving()) {
                  Vec3d movement = player.getVelocity();
                  player.setVelocity(movement.x * this.boost.getValue(), movement.y, movement.z * this.boost.getValue());
               }
         }
      }
   }

   private void nudgeLegit(ClientPlayerEntity player) {
      if (player.age % (15 + player.getId() % 11) == 0) {
         Vec3d movement = player.getVelocity();
         double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
         if (!(horizontal <= 0.01)) {
            double factor = 1.01 + Math.random() * 0.015;
            if (horizontal * factor > 0.209) {
               factor = 0.209 / horizontal;
            }

            player.setVelocity(movement.x * factor, movement.y, movement.z * factor);
         }
      }
   }

   private void driveMotion(ClientPlayerEntity player) {
      float forward = player.input.getMovementInput().y;
      float strafe = player.input.getMovementInput().x;
      if (forward != 0.0F || strafe != 0.0F) {
         double yawRadians = Math.toRadians((double)player.getYaw());
         double speedValue = this.speed.getValue();
         double x = -Math.sin(yawRadians) * speedValue;
         double z = Math.cos(yawRadians) * speedValue;
         if (forward < 0.0F) {
            x = -x;
            z = -z;
         }

         player.setVelocity(x, player.getVelocity().y, z);
      }
   }

   private void rideSurface(MinecraftClient client, ClientPlayerEntity player) {
      if (!client.options.jumpKey.isPressed()) {
         this.releaseTimer();
      } else {
         BlockPos pos = player.getBlockPos();
         FluidState fluid = client.world.getFluidState(pos);
         if (!fluid.isIn(FluidTags.WATER)) {
            this.releaseTimer();
         } else {
            double surfaceY = (double)((float)pos.getY() + fluid.getHeight(client.world, pos));
            double eyeY = player.getEyeY();
            if (!(eyeY < surfaceY - 0.2) && !(eyeY > surfaceY + 0.2)) {
               Vec3d movement = player.getVelocity();
               player.setVelocity(movement.x, 0.2, movement.z);
               double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
               TimerUtil.setTimer((float)Math.max(1.0, horizontal * 5.0));
               this.timerActive = true;
            } else {
               this.releaseTimer();
            }
         }
      }
   }

   private void runBob(MinecraftClient client, ClientPlayerEntity player) {
      this.bobbing = true;
      this.waterTicks++;
      boolean holdDown = this.stayUnder.getValue() && client.world.getBlockState(player.getBlockPos().up()).isAir();
      if (!holdDown && this.waterTicks % 10 < 5) {
         client.options.jumpKey.setPressed(true);
         client.options.sneakKey.setPressed(false);
      } else {
         client.options.jumpKey.setPressed(false);
         client.options.sneakKey.setPressed(true);
      }
   }

   private void releaseBob() {
      if (this.bobbing) {
         this.bobbing = false;
         MinecraftClient client = MinecraftClient.getInstance();
         client.options.jumpKey.setPressed(false);
         client.options.sneakKey.setPressed(false);
      }
   }

   private void releaseTimer() {
      if (this.timerActive) {
         TimerUtil.resetTimer();
         this.timerActive = false;
      }
   }
}
