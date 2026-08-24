package org.ryzen.feature.impl.combat;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.AttackEvent;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.MultiSelectSetting;

@Environment(EnvType.CLIENT)
public final class CriticalsFeature extends Feature implements MinecraftContext {
   private static final String IN_WEB = "In Web";
   private static final String NEGATIVE_EFFECTS = "Negative Effects";
   private static final double WEB_EXPAND = 0.3;
   private static final double WEB_STEP = 0.5;
   private static final double CRIT_PACKET_OFFSET = 1.0E-9;
   public final MultiSelectSetting conditions = this.register(new MultiSelectSetting("Conditions", List.of("In Web"), "In Web", "Negative Effects"));
   private int stopMotionTicks;

   public CriticalsFeature() {
      super("Criticals", "Forces criticals from webs and while slow falling", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.stopMotionTicks = 0;
   }

   @EventTarget
   public void onInput(PlayerInputEvent event) {
      if (this.stopMotionTicks > 0) {
         ClientPlayerEntity player = mc.player;
         if (player != null) {
            Vec3d velocity = player.getVelocity();
            player.setVelocity(0.0, velocity.y, 0.0);
         }

         event.clearMovement(false, true);
         this.stopMotionTicks--;
      }
   }

   @EventTarget
   public void onAttack(AttackEvent event) {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         if (this.conditions.isSelected("In Web") && this.inWeb()) {
            this.stopMotionTicks = Math.max(this.stopMotionTicks, 1);
            this.sendCritPacket(player);
         } else if (this.conditions.isSelected("Negative Effects") && this.canSlowFallCrit(player)) {
            this.sendCritPacket(player);
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = mc.player;
      if (player != null && this.conditions.isSelected("In Web") && this.inWeb()) {
         this.sendCritPacket(player);
      }
   }

   private boolean canSlowFallCrit(ClientPlayerEntity player) {
      return player.hasStatusEffect(StatusEffects.SLOW_FALLING) && player.fallDistance <= 0.0;
   }

   private void sendCritPacket(ClientPlayerEntity player) {
      if (player.networkHandler != null) {
         player.networkHandler.sendPacket(new PositionAndOnGround(player.getX(), player.getY() - 1.0E-9, player.getZ(), false, player.horizontalCollision));
      }
   }

   private boolean inWeb() {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         Box box = player.getBoundingBox().expand(0.3);

         for (double x = box.minX; x <= box.maxX; x += 0.5) {
            for (double y = box.minY; y <= box.maxY; y += 0.5) {
               for (double z = box.minZ; z <= box.maxZ; z += 0.5) {
                  if (mc.world.getBlockState(BlockPos.ofFloored(x, y, z)).isOf(Blocks.COBWEB)) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
