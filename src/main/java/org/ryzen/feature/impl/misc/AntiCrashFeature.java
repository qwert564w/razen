package org.ryzen.feature.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class AntiCrashFeature extends Feature implements MinecraftContext {
   private static final double DEFAULT_LIMIT = 1.0E9;
   private static final long NOTICE_INTERVAL_MS = 1000L;
   public final NumberSetting limit = this.register(new NumberSetting("Limit", 1.0E9, 1000000.0, 1.0E12, 1000000.0, ""));
   public final BooleanSetting explosions = this.register(new BooleanSetting("Explosions", true));
   public final BooleanSetting particles = this.register(new BooleanSetting("Particles", true));
   public final BooleanSetting notify = this.register(new BooleanSetting("Notify", true));
   private long lastNoticeAt;

   public AntiCrashFeature() {
      super("AntiCrash", "Blocks explosion and particle packets built to crash the client", FeatureCategory.MISC, -1);
   }

   public static AntiCrashFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(AntiCrashFeature.class);
   }

   @Override
   protected void onDisable() {
      this.lastNoticeAt = 0L;
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         if (event.getPacket() instanceof ExplosionS2CPacket packet) {
            if (this.explosions.getValue() && this.isAbsurdExplosion(packet)) {
               event.cancel();
               this.notice("blocked an explosion packet");
            }
         } else {
            if (event.getPacket() instanceof ParticleS2CPacket packet && this.particles.getValue() && this.isAbsurdParticle(packet)) {
               event.cancel();
               this.notice("blocked a particle packet");
            }
         }
      }
   }

   private boolean isAbsurdExplosion(ExplosionS2CPacket packet) {
      double bound = this.limit.getValue();
      Vec3d center = packet.center();
      return isFinite(center, bound) && !exceeds((double)packet.radius(), bound)
         ? packet.playerKnockback().map(knockback -> !isFinite(knockback, bound)).orElse(false)
         : true;
   }

   private boolean isAbsurdParticle(ParticleS2CPacket packet) {
      double bound = this.limit.getValue();
      return exceeds(packet.getX(), bound)
         || exceeds(packet.getY(), bound)
         || exceeds(packet.getZ(), bound)
         || exceeds((double)packet.getOffsetX(), bound)
         || exceeds((double)packet.getOffsetY(), bound)
         || exceeds((double)packet.getOffsetZ(), bound)
         || exceeds((double)packet.getSpeed(), bound);
   }

   private static boolean isFinite(Vec3d vector, double bound) {
      return !exceeds(vector.x, bound) && !exceeds(vector.y, bound) && !exceeds(vector.z, bound);
   }

   private static boolean exceeds(double value, double bound) {
      return Double.isNaN(value) || Math.abs(value) > bound;
   }

   private void notice(String what) {
      if (this.notify.getValue()) {
         long now = System.currentTimeMillis();
         if (now - this.lastNoticeAt >= 1000L) {
            this.lastNoticeAt = now;
            mc.execute(() -> ChatUtil.info("AntiCrash: " + what));
         }
      }
   }
}
