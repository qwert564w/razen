package org.ryzen.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.event.events.packet.PacketSendEvent;

@Environment(EnvType.CLIENT)
public final class PacketEventManager {
   private PacketEventManager() {
   }

   public static boolean hasSendListeners() {
      return EventManager.hasListeners(PacketSendEvent.class);
   }

   public static boolean hasReceiveListeners() {
      return EventManager.hasListeners(PacketReceiveEvent.class);
   }

   public static PacketSendEvent callSendPre(ClientConnection connection, Packet<?> packet) {
      return EventManager.call(new PacketSendEvent(connection, packet, PacketSendEvent.Phase.PRE));
   }

   public static void callSendPost(ClientConnection connection, Packet<?> packet) {
      EventManager.call(new PacketSendEvent(connection, packet, PacketSendEvent.Phase.POST));
   }

   public static boolean callReceivePre(ClientConnection connection, Packet<?> packet) {
      return EventManager.call(new PacketReceiveEvent(connection, packet, PacketReceiveEvent.Phase.PRE)).isCancelled();
   }

   public static void callReceivePost(ClientConnection connection, Packet<?> packet) {
      EventManager.call(new PacketReceiveEvent(connection, packet, PacketReceiveEvent.Phase.POST));
   }
}
