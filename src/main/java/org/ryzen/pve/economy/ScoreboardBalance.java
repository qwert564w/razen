package org.ryzen.pve.economy;

import java.util.OptionalLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class ScoreboardBalance {
   private static final String[] LABELS = new String[]{"balance", "money", "coins", "баланс", "монет"};

   private ScoreboardBalance() {
   }

   public static OptionalLong read(ClientPlayerEntity player) {
      if (player == null) {
         return OptionalLong.empty();
      } else {
         Scoreboard scoreboard = player.getEntityWorld().getScoreboard();
         ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
         if (objective == null) {
            return OptionalLong.empty();
         } else {
            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
               if (!entry.hidden()) {
                  Text name = (Text)(entry.display() == null ? Text.literal(entry.owner()) : entry.display());
                  Team team = scoreboard.getScoreHolderTeam(entry.owner());
                  String line = Team.decorateName(team, name).getString();
                  if (EconomyTextParser.containsAny(line, LABELS)) {
                     OptionalLong amount = EconomyTextParser.amountNearAnyLabel(line, LABELS);
                     if (amount.isEmpty()) {
                        amount = EconomyTextParser.largestAmount(line);
                     }

                     if (amount.isPresent()) {
                        return amount;
                     }
                  }
               }
            }

            return OptionalLong.empty();
         }
      }
   }
}
