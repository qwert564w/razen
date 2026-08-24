package ru.fiw.proxyserver.mixin;

import io.netty.channel.Channel;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetSocketAddress;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fiw.proxyserver.Config;
import ru.fiw.proxyserver.Proxy;
import ru.fiw.proxyserver.ProxyServer;

@Environment(EnvType.CLIENT)
@Mixin(
   targets = {"net/minecraft/network/ClientConnection$1"}
)
public final class ClientConnectionInit {
   private static final Logger LOGGER = LoggerFactory.getLogger("Ryzen/ProxyServerUpdated");
   private static final long HANDSHAKE_TIMEOUT_MILLIS = 10000L;

   @Inject(
      method = {"initChannel"},
      at = {@At("HEAD")}
   )
   private void proxyserver$initChannel(Channel channel, CallbackInfo callbackInfo) {
      Config.loadConfig();
      if (!ProxyServer.proxyEnabled) {
         ProxyServer.lastUsedProxy = new Proxy();
      } else {
         Proxy proxy = ProxyServer.proxy.copy();
         if (!proxy.isUsable()) {
            throw new IllegalStateException("Proxy is enabled but its address is not valid: " + proxy.ipPort);
         } else {
            InetSocketAddress address = proxy.socketAddress();
            ProxyHandler handler = createHandler(proxy, address);
            handler.setConnectTimeoutMillis(10000L);
            channel.pipeline().addFirst("proxyserver_proxy", handler);
            ProxyServer.lastUsedProxy = proxy;
            LOGGER.info("Routing connection through {} proxy {}", proxy.type, proxy.ipPort);
         }
      }
   }

   private static ProxyHandler createHandler(Proxy proxy, InetSocketAddress address) {
      String username = proxy.username == null ? "" : proxy.username;
      String password = proxy.password == null ? "" : proxy.password;
      if (proxy.type == Proxy.ProxyType.SOCKS4) {
         return username.isBlank() ? new Socks4ProxyHandler(address) : new Socks4ProxyHandler(address, username);
      } else {
         return username.isBlank() ? new Socks5ProxyHandler(address) : new Socks5ProxyHandler(address, username, password);
      }
   }
}
