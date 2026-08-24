package org.ryzen.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.ryzen.event.PacketEventManager;
import org.ryzen.event.events.packet.PacketSendEvent;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin({ClientConnection.class})
public abstract class ConnectionMixin {
   @WrapMethod(
      method = {"send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"}
   )
   private void wrapSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
      if (!PacketEventManager.hasSendListeners()) {
         original.call(new Object[]{packet, listener, flush});
      } else {
         PacketSendEvent event = PacketEventManager.callSendPre((ClientConnection)(Object)this, packet);
         if (!event.isCancelled()) {
            Packet<?> dispatchedPacket = event.getPacket();
            original.call(new Object[]{dispatchedPacket, listener, flush});
            PacketEventManager.callSendPost((ClientConnection)(Object)this, dispatchedPacket);
         }
      }
   }

   @WrapMethod(
      method = {"channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V"}
   )
   private void wrapReceive(ChannelHandlerContext context, Packet<?> packet, Operation<Void> original) {
      if (!PacketEventManager.hasReceiveListeners()) {
         original.call(new Object[]{context, packet});
      } else if (!PacketEventManager.callReceivePre((ClientConnection)(Object)this, packet)) {
         original.call(new Object[]{context, packet});
         PacketEventManager.callReceivePost((ClientConnection)(Object)this, packet);
      }
   }
}
