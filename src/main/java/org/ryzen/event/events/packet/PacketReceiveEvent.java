package org.ryzen.event.events.packet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.ryzen.event.CancellableEvent;

@Environment(EnvType.CLIENT)
public final class PacketReceiveEvent extends CancellableEvent {
   private final ClientConnection connection;
   private final Packet<?> packet;
   private final PacketReceiveEvent.Phase phase;

   public PacketReceiveEvent(ClientConnection connection, Packet<?> packet, PacketReceiveEvent.Phase phase) {
      this.connection = connection;
      this.packet = packet;
      this.phase = phase;
   }
   public ClientConnection getConnection() {
      return this.connection;
   }
   public Packet<?> getPacket() {
      return this.packet;
   }
   public PacketReceiveEvent.Phase getPhase() {
      return this.phase;
   }

   @Environment(EnvType.CLIENT)
   public static enum Phase {
      PRE,
      POST;
   }
}
