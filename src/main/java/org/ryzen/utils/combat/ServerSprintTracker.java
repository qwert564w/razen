package org.ryzen.utils.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.lifecycle.WorldJoinEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.packet.PacketSendEvent;

@Environment(EnvType.CLIENT)
public final class ServerSprintTracker {
   public static final ServerSprintTracker INSTANCE = new ServerSprintTracker();
   private volatile boolean sprinting;

   private ServerSprintTracker() {
   }

   public static boolean isServerSprinting() {
      return INSTANCE.sprinting;
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPhase() == PacketSendEvent.Phase.POST && event.getPacket() instanceof ClientCommandC2SPacket packet) {
         if (packet.getMode() == Mode.START_SPRINTING) {
            this.sprinting = true;
         } else if (packet.getMode() == Mode.STOP_SPRINTING) {
            this.sprinting = false;
         }
      }
   }

   @EventTarget
   public void onWorldJoin(WorldJoinEvent event) {
      this.sprinting = false;
      LocalPlayerHistory.reset();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.sprinting = false;
      LocalPlayerHistory.reset();
   }
}
