package org.ryzen.feature.impl.pve;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import org.ryzen.utils.StaffManager;

@Environment(EnvType.CLIENT)
public final class ModeratorDetector {
   private static final Pattern FORMATTING_CODE = Pattern.compile("(?i)§[0-9A-FK-ORX]");
   private static final Pattern ROLE = Pattern.compile(
      "(?<![\\p{L}\\p{N}])(?:admin(?:istrator)?|moderator|mod|staff|helper|curator|админ(?:истратор)?|модер(?:атор)?|хелпер|куратор|персонал|стаж[её]р)(?![\\p{L}\\p{N}])",
      66
   );

   private ModeratorDetector() {
   }

   public static Optional<String> find(MinecraftClient client, ClientPlayerEntity self) {
      if (client != null && self != null && client.getNetworkHandler() != null) {
         for (PlayerListEntry info : client.getNetworkHandler().getPlayerList()) {
            if (!self.getUuid().equals(info.getProfile().id())) {
               String name = info.getProfile().name();
               if ((containsRole(roleText(info)) || StaffManager.INSTANCE.isStaff(name)) && name != null) {
                  return Optional.of(name);
               }
            }
         }

         if (client.world != null) {
            for (PlayerEntity player : client.world.getPlayers()) {
               if (player != self && !self.getUuid().equals(player.getUuid()) && containsRole(roleText(player.getDisplayName(), player.getScoreboardTeam()))) {
                  String name = player.getGameProfile().name();
                  if (name != null) {
                     return Optional.of(name);
                  }
               }
            }
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   public static boolean containsRole(String value) {
      if (value != null && !value.isBlank()) {
         String normalized = Normalizer.normalize(value, Form.NFKC);
         normalized = FORMATTING_CODE.matcher(normalized).replaceAll("");
         normalized = normalized.toLowerCase(Locale.ROOT);
         return ROLE.matcher(normalized).find();
      } else {
         return false;
      }
   }

   private static String roleText(PlayerListEntry info) {
      return roleText(info.getDisplayName(), info.getScoreboardTeam());
   }

   private static String roleText(Text displayName, Team team) {
      StringBuilder text = new StringBuilder();
      append(text, displayName);
      if (team != null) {
         append(text, team.getPrefix());
         append(text, team.getSuffix());
         text.append(' ').append(team.getName());
      }

      return text.toString();
   }

   private static void append(StringBuilder target, Text component) {
      if (component != null) {
         target.append(' ').append(component.getString());
      }
   }
}
