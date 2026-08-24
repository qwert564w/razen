package org.ryzen.context;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.ryzen.event.PacketEventManager;

@Environment(EnvType.CLIENT)
public interface PacketContext extends MinecraftContext {
   default ClientPlayNetworkHandler packetListener() {
      return this.client().getNetworkHandler();
   }

   default ClientConnection connection() {
      ClientPlayNetworkHandler packetListener = this.packetListener();
      return packetListener != null ? packetListener.getConnection() : null;
   }

   default boolean hasConnection() {
      return this.connection() != null;
   }

   default boolean isConnected() {
      ClientConnection connection = this.connection();
      return connection != null && connection.isOpen();
   }

   default void sendPacket(Packet<?> packet) {
      ClientConnection connection = this.connection();
      if (connection != null && packet != null) {
         connection.send(packet);
      }
   }

   default boolean hasPacketSendListeners() {
      return PacketEventManager.hasSendListeners();
   }

   default boolean hasPacketReceiveListeners() {
      return PacketEventManager.hasReceiveListeners();
   }
}
