package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.event.events.packet.PacketSendEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.ModeSetting;

@Environment(EnvType.CLIENT)
public final class VelocityFeature extends Feature implements MinecraftContext {
   private static final String MODE_VANILLA = "Vanilla";
   private static final String MODE_GRIM_TICKS = "Grim by ticks";
   private static final String MODE_GRIM = "Grim";
   private static final String MODE_GRIM_AIR = "Grim in air";
   private static final String MODE_OLD_GRIM = "Old grim";
   private static final int SWALLOW_TICKS = 6;
   private static final int GROUND_SPOOF_TICKS = 3;
   private static final float FALL_LIMIT = 15.0F;
   private static final float KNOCKBACK_MIN = 0.09F;
   private static final int OLD_GRIM_PERIOD = 2;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Vanilla", "Vanilla", "Grim by ticks", "Grim", "Grim in air", "Old grim"));
   private int swallowTicks;
   private int groundTicks;
   private int oldGrimCount;
   private boolean airborne;

   public VelocityFeature() {
      super("Velocity", "Removes knockback", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.swallowTicks = 0;
      this.groundTicks = 0;
      this.oldGrimCount = 0;
      this.airborne = false;
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && this.player() != null) {
         if (event.getPacket() instanceof PlayerPositionLookS2CPacket && this.mode.is("Grim")) {
            this.groundTicks = 3;
         } else {
            if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet && packet.getEntityId() == this.player().getId()) {
               String var5 = this.mode.getValue();
               switch (var5) {
                  case "Grim by ticks":
                     this.swallowTicks = 6;
                     event.cancel();
                     break;
                  case "Grim":
                     this.airborne = true;
                     event.cancel();
                     break;
                  case "Grim in air":
                     if (this.canRefuseInAir()) {
                        event.cancel();
                     }
                     break;
                  case "Old grim":
                     if (this.oldGrimCount >= 2) {
                        this.oldGrimCount = 0;
                        return;
                     }

                     this.oldGrimCount++;
                     event.cancel();
                     break;
                  default:
                     event.cancel();
               }

               return;
            }
         }
      }
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (this.player() != null && event.getPacket() instanceof PlayerMoveC2SPacket) {
         if (this.mode.is("Grim by ticks") && this.swallowTicks > 0) {
            this.swallowTicks--;
            event.cancel();
         } else if (this.mode.is("Grim") && this.airborne) {
            this.groundTicks--;
            if (this.groundTicks <= 0) {
               this.player().networkHandler.sendPacket(new OnGroundOnly(true, this.player().horizontalCollision));
            }

            this.airborne = false;
         }
      }
   }

   private boolean canRefuseInAir() {
      return !this.player().isGliding() && !this.player().isTouchingWater() && !this.player().isInLava()
         ? this.player().fallDistance > 0.09F && this.player().fallDistance < 15.0
         : false;
   }
}
