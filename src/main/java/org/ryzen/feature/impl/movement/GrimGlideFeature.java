package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.event.events.packet.PacketSendEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.combat.AuraFeature;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class GrimGlideFeature extends Feature implements PlayerContext {
   private static final long FIREWORK_GRACE_MS = 150L;
   private static final double SPEED_EVEN = 0.079;
   private static final double SPEED_ODD = 0.088;
   private static final double AHEAD_DOT = 0.35;
   private long lastFireworkAt;

   public GrimGlideFeature() {
      super("GrimGlide", "Moves on an elytra without spending fireworks", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onEnable() {
      this.lastFireworkAt = 0L;
      ChatUtil.info("GrimGlide: на Really World долгое использование может привести к кику");
   }

   @Override
   protected void onDisable() {
      this.lastFireworkAt = 0L;
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPhase() == PacketSendEvent.Phase.PRE && event.getPacket() instanceof PlayerInteractItemC2SPacket) {
         this.lastFireworkAt = System.currentTimeMillis();
      }
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre()) {
         ClientPlayerEntity player = event.getPlayer();
         if (player != null && player.isGliding()) {
            if (System.currentTimeMillis() - this.lastFireworkAt > 150L && !this.targetIsAhead(player)) {
               double speed = player.age % 2 == 0 ? 0.079 : 0.088;
               double yawRadians = Math.toRadians((double)player.getYaw());
               double x = -Math.sin(yawRadians) * speed;
               double z = Math.cos(yawRadians) * speed;
               player.setVelocity(x, player.getVelocity().y, z);
               if (player.age % 2 == 0) {
                  player.setPosition(player.getX() + x, player.getY(), player.getZ() + z);
               }
            }
         }
      }
   }

   private boolean targetIsAhead(ClientPlayerEntity player) {
      AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
      if (aura == null) {
         return false;
      } else {
         LivingEntity target = aura.getCurrentTarget();
         if (target != null && target.isGliding()) {
            Vec3d velocity = target.getVelocity();
            Vec3d heading = new Vec3d(velocity.x, 0.0, velocity.z);
            if (heading.lengthSquared() < 1.0E-6) {
               Vec3d look = target.getRotationVector();
               heading = new Vec3d(look.x, 0.0, look.z);
            }

            if (heading.lengthSquared() < 1.0E-6) {
               return false;
            } else {
               heading = heading.normalize();
               Vec3d toSelf = new Vec3d(player.getX() - target.getX(), 0.0, player.getZ() - target.getZ());
               return toSelf.dotProduct(heading) > 0.35;
            }
         } else {
            return false;
         }
      }
   }
}
