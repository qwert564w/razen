package org.ryzen.menu.pages.friends;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
final class FriendSkinCache {
   private static final ConcurrentHashMap<String, Supplier<SkinTextures>> SKINS = new ConcurrentHashMap<>();
   private static final Set<String> RESOLVING = ConcurrentHashMap.newKeySet();

   private FriendSkinCache() {
   }

   static Identifier texture(MinecraftClient minecraft, String name) {
      PlayerListEntry online = onlineInfo(minecraft, name);
      String key = normalize(name);
      if (online != null) {
         Supplier<SkinTextures> skin = online::getSkinTextures;
         SKINS.put(key, skin);
         return skin.get().body().texturePath();
      } else {
         Supplier<SkinTextures> fallback = SKINS.computeIfAbsent(key, ignored -> defaultSkin(name));
         resolve(minecraft, name, key);
         return fallback.get().body().texturePath();
      }
   }

   static boolean isOnline(MinecraftClient minecraft, String name) {
      return onlineInfo(minecraft, name) != null;
   }

   private static PlayerListEntry onlineInfo(MinecraftClient minecraft, String name) {
      return minecraft.getNetworkHandler() == null
         ? null
         : minecraft.getNetworkHandler().getPlayerList().stream().filter(info -> info.getProfile().name().equalsIgnoreCase(name)).findFirst().orElse(null);
   }

   private static void resolve(MinecraftClient minecraft, String name, String key) {
      if (RESOLVING.add(key)) {
         CompletableFuture.supplyAsync(() -> (Optional<GameProfile>)minecraft.getApiServices().profileResolver().getProfileByName(name), Util.getDownloadWorkerExecutor())
            .thenAccept(
               profile -> profile.ifPresent(
                  resolved -> minecraft.execute(() -> SKINS.put(key, minecraft.getSkinProvider().supplySkinTextures((GameProfile)resolved, false)))
               )
            )
            .whenComplete((unused, throwable) -> RESOLVING.remove(key));
      }
   }

   private static Supplier<SkinTextures> defaultSkin(String name) {
      UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
      GameProfile profile = new GameProfile(uuid, name);
      SkinTextures skin = DefaultSkinHelper.getSkinTextures(profile);
      return () -> skin;
   }

   private static String normalize(String name) {
      return name.toLowerCase(Locale.ROOT);
   }
}
