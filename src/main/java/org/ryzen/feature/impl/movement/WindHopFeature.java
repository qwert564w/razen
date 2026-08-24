package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.game.PlayerJumpEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.ryzen.event.events.input.MouseInputEvent;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.event.events.packet.PacketSendEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.InputBindSetting;
import org.ryzen.feature.setting.ModeSetting;

@Environment(EnvType.CLIENT)
public final class WindHopFeature extends Feature implements PlayerContext {
   private static final String MODE_AFTER_USE = "After Use";
   private static final String MODE_AUTO = "Auto";
   private static final String MODE_BIND = "Bind";
   private static final int OFFHAND_MARKER = 40;
   private static final float WALL_SCAN_PITCH = 75.0F;
   private static final float DOWNWARD_PITCH = 90.0F;
   private static final double WALL_SCAN_DISTANCE = 1.5;
   private static final double RISING_SPEED = 0.4;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "After Use", "After Use", "Auto", "Bind"));
   public final InputBindSetting key = this.register(new InputBindSetting("Key", -1).visibleWhen(() -> this.mode.is("Bind")));
   public final BooleanSetting autoJump = this.register(new BooleanSetting("Auto Jump", true));
   public final BooleanSetting predictLanding = this.register(new BooleanSetting("Predict Landing", true));
   public final BooleanSetting lookDown = this.register(new BooleanSetting("Look Down", true).visibleWhen(() -> this.mode.is("After Use")));
   private boolean pending;
   private int jumpDelay = -1;

   public WindHopFeature() {
      super("Wind Hop", "Jumps after a wind charge or uses one for an extra jump", FeatureCategory.PLAYER, -1);
      this.renamedFrom("WindHop");
   }

   @Override
   protected void onDisable() {
      this.pending = false;
      this.jumpDelay = -1;
   }

   @EventTarget
   public void onJump(PlayerJumpEvent event) {
      if (this.mode.is("Auto")) {
         this.useCharge(MinecraftClient.getInstance(), event.getPlayer());
      }
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (this.mode.is("After Use") && event.getPhase() == PacketSendEvent.Phase.PRE && event.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null && player.getStackInHand(packet.getHand()).isOf(Items.WIND_CHARGE)) {
            if (this.lookDown.getValue() && player.networkHandler != null) {
               player.networkHandler.sendPacket(new LookAndOnGround(player.getYaw(), 90.0F, player.isOnGround(), player.horizontalCollision));
            }

            this.jumpDelay = 2;
         }
      }
   }

   @EventTarget
   public void onPlayerInput(PlayerInputEvent event) {
      if (this.mode.is("After Use") && this.jumpDelay == 0) {
         event.setJump(true);
         this.jumpDelay = -1;
      }
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (this.mode.is("Bind") && event.getAction() == 0 && this.key.matches(event.getKey())) {
         this.pending = true;
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (this.mode.is("Bind") && event.getAction() == 0 && this.key.matchesMouse(event.getButton())) {
         this.pending = true;
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (this.jumpDelay > 0) {
         this.jumpDelay--;
      }

      if (player != null && client.world != null) {
         if (!this.mode.is("After Use")) {
            if (findChargeSlot(player) != -1) {
               if (this.mode.is("Bind")) {
                  if (this.pending) {
                     this.pending = false;
                     this.useCharge(client, player);
                  }
               } else {
                  boolean shouldFire = false;
                  if (this.isRisingInOpenAir(client, player)) {
                     shouldFire = this.scanForWall(client, player) != null;
                  } else if (this.predictLanding.getValue()) {
                     shouldFire = !blockAt(client, player, -1.0).isAir() && player.fallDistance > 2.0;
                  }

                  if (shouldFire && (client.options.jumpKey.isPressed() || this.autoJump.getValue())) {
                     this.useCharge(client, player);
                  }

                  if (player.isOnGround() && this.autoJump.getValue()) {
                     player.jump();
                  }
               }
            }
         }
      }
   }

   private void useCharge(MinecraftClient client, ClientPlayerEntity player) {
      if (player != null && player.networkHandler != null && client.world != null) {
         int slot = findChargeSlot(player);
         if (slot != -1) {
            boolean offhand = slot == 40;
            Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
            int previousSlot = player.getInventory().getSelectedSlot();
            boolean switched = !offhand && slot != previousSlot;
            if (switched) {
               player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            float yaw = player.getYaw();
            float pitch = 90.0F;
            if (this.isRisingInOpenAir(client, player)) {
               Float wallYaw = this.scanForWall(client, player);
               if (wallYaw != null) {
                  yaw = wallYaw;
                  pitch = 75.0F;
               }
            }

            player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(hand, 0, yaw, pitch));
            player.swingHand(hand);
            if (switched) {
               player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            }
         }
      }
   }

   private Float scanForWall(MinecraftClient client, ClientPlayerEntity player) {
      Vec3d eye = player.getEyePos();

      for (int offset = 0; offset < 360; offset += 45) {
         Vec3d direction = Vec3d.fromPolar(75.0F, (float)offset);
         HitResult hit = client.world.raycast(new RaycastContext(eye, eye.add(direction.multiply(1.5)), ShapeType.COLLIDER, FluidHandling.NONE, player));
         if (hit.getType() == Type.BLOCK) {
            return MathHelper.wrapDegrees(player.getYaw() + (float)offset);
         }
      }

      return null;
   }

   private boolean isRisingInOpenAir(MinecraftClient client, ClientPlayerEntity player) {
      return !player.isOnGround() && blockAt(client, player, -2.0).isAir() && player.getVelocity().y > 0.4;
   }

   private static BlockState blockAt(MinecraftClient client, ClientPlayerEntity player, double offsetY) {
      return client.world.getBlockState(BlockPos.ofFloored(player.getEntityPos().add(0.0, offsetY, 0.0)));
   }

   private static int findChargeSlot(ClientPlayerEntity player) {
      if (player.getOffHandStack().isOf(Items.WIND_CHARGE)) {
         return 40;
      } else {
         for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isOf(Items.WIND_CHARGE)) {
               return slot;
            }
         }

         return -1;
      }
   }
}
