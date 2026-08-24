package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.util.Hand;
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
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.ModeSetting;

@Environment(EnvType.CLIENT)
public final class SpiderFeature extends Feature implements PlayerContext {
   private static final String MODE_WATER = "Water";
   private static final String MODE_HEAD = "Head";
   private static final long HEAD_PLACE_COOLDOWN_MS = 150L;
   private static final double HEAD_REACH = 4.0;
   private static final double WATER_BOOST = 0.36;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Water", "Water", "Head"));
   private long lastHeadPlaceAt;
   private int originalSlot = -1;
   private int bucketSlot = -1;

   public SpiderFeature() {
      super("Spider", "Climbs walls with a water bucket or a head", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = this.localPlayer();
      if (player != null && this.originalSlot != -1 && (this.bucketSlot == -1 || player.getInventory().getSelectedSlot() == this.bucketSlot)) {
         select(player, this.originalSlot);
      }

      this.originalSlot = -1;
      this.bucketSlot = -1;
      this.lastHeadPlaceAt = 0L;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && client.interactionManager != null) {
         if (this.mode.is("Water")) {
            this.climbWithWater(client, player);
         } else {
            this.climbWithHead(client, player);
         }
      }
   }

   private void climbWithWater(MinecraftClient client, ClientPlayerEntity player) {
      if (player.horizontalCollision) {
         int bucket = findHotbar(player, Items.WATER_BUCKET);
         if (bucket != -1) {
            if (!player.getMainHandStack().isOf(Items.WATER_BUCKET)) {
               if (this.originalSlot == -1) {
                  this.originalSlot = player.getInventory().getSelectedSlot();
               }

               this.bucketSlot = bucket;
               select(player, bucket);
            }

            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            Vec3d movement = player.getVelocity();
            player.setVelocity(movement.x, 0.36, movement.z);
         }
      }
   }

   private void climbWithHead(MinecraftClient client, ClientPlayerEntity player) {
      if (player.horizontalCollision) {
         if (player.isOnGround()) {
            player.jump();
         }

         long now = System.currentTimeMillis();
         if (now - this.lastHeadPlaceAt >= 150L) {
            ItemStack offhand = player.getOffHandStack();
            if (offhand.isOf(Items.PLAYER_HEAD)) {
               Vec3d eye = player.getEyePos();
               Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(4.0));
               BlockHitResult hit = client.world.raycast(new RaycastContext(eye, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
               if (hit.getType() == Type.BLOCK) {
                  BlockPos pos = hit.getBlockPos();
                  if (!client.world.getBlockState(pos).isAir() && !client.world.getBlockState(pos).isReplaceable()) {
                     if (player.networkHandler != null) {
                        player.networkHandler
                           .sendPacket(new LookAndOnGround(player.getYaw(), player.getPitch(), player.isOnGround(), player.horizontalCollision));
                     }

                     player.swingHand(Hand.OFF_HAND);
                     client.interactionManager.interactBlock(player, Hand.OFF_HAND, hit);
                     player.fallDistance = 0.0;
                     this.lastHeadPlaceAt = now;
                  }
               }
            }
         }
      }
   }

   private static int findHotbar(ClientPlayerEntity player, Item item) {
      for (int slot = 0; slot < 9; slot++) {
         if (player.getInventory().getStack(slot).isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   private static void select(ClientPlayerEntity player, int slot) {
      player.getInventory().setSelectedSlot(slot);
      if (player.networkHandler != null) {
         player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
      }
   }
}
