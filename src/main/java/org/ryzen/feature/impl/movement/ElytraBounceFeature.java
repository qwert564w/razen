package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.math.BlockPos;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.ModeSetting;

@Environment(EnvType.CLIENT)
public final class ElytraBounceFeature extends Feature implements MinecraftContext {
   private static final String MODE_NEW = "New";
   private static final String MODE_OLD = "Old";
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "New", "New", "Old"));

   public ElytraBounceFeature() {
      super("ElytraBounce", "Bounces off the ground back into an elytra glide", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && mc.world != null) {
         if (!this.inWater(player) && this.hasHeadroom(player) && this.hasElytra(player)) {
            if (!this.mode.is("Old") || this.isMoving(player)) {
               if (player.isOnGround()) {
                  player.jump();
               } else if (!player.isGliding() && !this.inWater(player) && this.canBounce(player)) {
                  this.deploy(player);
               }
            }
         }
      }
   }

   private void deploy(ClientPlayerEntity player) {
      if (player.networkHandler != null) {
         player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
         player.checkGliding();
      }
   }

   private boolean isMoving(ClientPlayerEntity player) {
      return player.input.getMovementInput().lengthSquared() > 0.0F;
   }

   private boolean inWater(ClientPlayerEntity player) {
      return player.isTouchingWater() || player.isSubmergedInWater() || player.isSwimming();
   }

   private boolean hasHeadroom(ClientPlayerEntity player) {
      BlockPos base = player.getBlockPos();

      for (int i = 1; i <= 2; i++) {
         BlockPos pos = base.up(i);
         if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   private boolean hasElytra(ClientPlayerEntity player) {
      return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
   }

   private boolean canBounce(ClientPlayerEntity player) {
      if (this.hasElytra(player) && !player.isSubmergedInWater() && !player.isInLava() && !player.hasVehicle()) {
         ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
         return chest.isOf(Items.ELYTRA) && chest.getDamage() < chest.getMaxDamage() - 1;
      } else {
         return false;
      }
   }
}
