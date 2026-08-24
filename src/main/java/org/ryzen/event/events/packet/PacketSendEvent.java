package org.ryzen.event.events.packet;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.ryzen.event.CancellableEvent;

@Environment(EnvType.CLIENT)
public final class PacketSendEvent extends CancellableEvent {
   private final ClientConnection connection;
   private Packet<?> packet;
   private final PacketSendEvent.Phase phase;

   public PacketSendEvent(ClientConnection connection, Packet<?> packet, PacketSendEvent.Phase phase) {
      this.connection = connection;
      this.packet = packet;
      this.phase = phase;
   }

   public void setPacket(Packet<?> packet) {
      if (this.isCompleted()) {
         throw new IllegalStateException("Cannot replace a packet after event dispatch.");
      } else {
         this.packet = Objects.requireNonNull(packet, "packet");
      }
   }
   public ClientConnection getConnection() {
      return this.connection;
   }
   public Packet<?> getPacket() {
      return this.packet;
   }
   public PacketSendEvent.Phase getPhase() {
      return this.phase;
   }

   @Environment(EnvType.CLIENT)
   public static enum Phase {
      PRE,
      POST;
   }
}
