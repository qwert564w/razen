package org.ryzen.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.session.Session;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServer;
import org.ryzen.menu.core.MenuConfigStore;
import org.ryzen.mixin.accessor.MinecraftAccessor;
import org.ryzen.mixin.accessor.MinecraftServerAccessor;

@Environment(EnvType.CLIENT)
public final class AccountSwitcher {
   public static final String DEFAULT_NAME = "Ryzen";
   private static boolean startupApplied;

   private AccountSwitcher() {
   }

   public static void applyStartupAccount() {
      if (!startupApplied) {
         startupApplied = true;
         String name = MenuConfigStore.getString("selectedAccount", "");
         if (name.isEmpty() || !name.matches("^[a-zA-Z0-9_]{3,16}$")) {
            name = "Ryzen";
         }

         switchTo(name);
      }
   }

   public static void switchTo(String name) {
      MinecraftClient mc = MinecraftClient.getInstance();
      UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
      Session user = new Session(name, uuid, "0", Optional.empty(), Optional.empty());
      MinecraftAccessor accessor = (MinecraftAccessor)mc;
      accessor.setUser(user);
      accessor.setProfileFuture(CompletableFuture.completedFuture(new ProfileResult(new GameProfile(uuid, name))));
      mc.updateWindowTitle();
   }

   public static void relogin(String name) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ServerInfo server = mc.getCurrentServerEntry();
      String levelId = null;
      IntegratedServer integrated = mc.getServer();
      if (mc.isInSingleplayer() && integrated != null) {
         levelId = ((MinecraftServerAccessor)integrated).getStorageSource().getDirectoryName();
      }

      switchTo(name);
      if (mc.world != null) {
         mc.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
         if (levelId != null) {
            mc.createIntegratedServerLoader().start(levelId, () -> mc.setScreenAndRender(new TitleScreen()));
         } else if (server != null && !server.isLocal() && !server.isRealm()) {
            ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(server.address), server, false, null);
         }
      }
   }
}
