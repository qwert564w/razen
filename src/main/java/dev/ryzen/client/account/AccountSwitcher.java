package dev.ryzen.client.account;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.ryzen.client.mixin.MinecraftAccessor;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

@Environment(EnvType.CLIENT)
public final class AccountSwitcher {
   private static final Pattern MINECRAFT_NICKNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
   private static boolean startupApplied;

   private AccountSwitcher() {
   }

   public static void applyStartupAccount() {
      if (!startupApplied) {
         startupApplied = true;
         MinecraftClient minecraft = MinecraftClient.getInstance();
         AccountStore store = AccountStore.load(minecraft.getSession().getUsername());
         store.selected().map(AltAccount::nickname).ifPresent(AccountSwitcher::switchTo);
      }
   }

   public static boolean switchTo(String nickname) {
      String normalized = nickname == null ? "" : nickname.trim();
      if (!MINECRAFT_NICKNAME.matcher(normalized).matches()) {
         return false;
      } else {
         MinecraftClient minecraft = MinecraftClient.getInstance();
         UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + normalized).getBytes(StandardCharsets.UTF_8));
         Session user = new Session(normalized, uuid, "0", Optional.empty(), Optional.empty());
         MinecraftAccessor accessor = (MinecraftAccessor)minecraft;
         accessor.setUser(user);
         accessor.setProfileFuture(CompletableFuture.completedFuture(new ProfileResult(new GameProfile(uuid, normalized))));
         minecraft.updateWindowTitle();
         return true;
      }
   }
}
