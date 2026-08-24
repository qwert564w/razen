package org.ryzen.feature.impl.visual;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.TextSetting;

@Environment(EnvType.CLIENT)
public final class BoardSpooferFeature extends Feature {
   private static final String RANK = "Rank";
   private static final String COINS = "Coins";
   private static final String TOKENS = "Tokens";
   private static final Pattern RANK_LINE = Pattern.compile("(?iu)(ранг|rank)(\\s*[:：]\\s*)(.*)");
   private static final Pattern COINS_LINE = Pattern.compile("(?iu)(монет(?:ы|)|coins?)(\\s*[:：]\\s*)([-+0-9 ,.]+)");
   private static final Pattern TOKENS_LINE = Pattern.compile("(?iu)(токен(?:ы|ов|)|tokens?)(\\s*[:：]\\s*)([-+0-9 ,.]+)");
   public final MultiSelectSetting elements = this.register(new MultiSelectSetting("Elements", List.of("Rank", "Coins", "Tokens"), "Rank", "Coins", "Tokens"));
   public final ModeSetting rank = this.register(
      new ModeSetting("Privilege", "Игрок", "Игрок", "Барон", "Страж", "Герой", "Аспид", "Сквид", "Глава", "Элита", "Титан", "Принц", "Князь", "Герцог")
         .renamedFrom("Player", "Игрок")
         .renamedFrom("Baron", "Барон")
         .renamedFrom("Guardian", "Страж")
         .renamedFrom("Hero", "Герой")
         .renamedFrom("Aspid", "Аспид")
         .renamedFrom("Squid", "Сквид")
         .renamedFrom("Head", "Глава")
         .renamedFrom("Elite", "Элита")
         .renamedFrom("Titan", "Титан")
         .renamedFrom("Prince", "Принц")
         .renamedFrom("Duke", "Князь")
         .renamedFrom("Herzog", "Герцог")
         .visibleWhen(() -> this.elements.isSelected("Rank"))
   );
   public final TextSetting coins = this.register(new TextSetting("Coins", "0", 18).visibleWhen(() -> this.elements.isSelected("Coins")));
   public final TextSetting tokens = this.register(new TextSetting("Tokens", "0", 18).visibleWhen(() -> this.elements.isSelected("Tokens")));

   public BoardSpooferFeature() {
      super("Board Spoofer", "Changes rank and currency values shown in the scoreboard", FeatureCategory.VISUAL, -1);
   }

   public static Text transform(Text original) {
      BoardSpooferFeature feature = FeatureManager.INSTANCE.getEnabled(BoardSpooferFeature.class);
      if (feature != null && original != null) {
         String value = original.getString();
         String replaced = value;
         Formatting formatting = null;
         if (feature.elements.isSelected("Rank")) {
            Matcher matcher = RANK_LINE.matcher(value);
            if (matcher.find()) {
               replaced = matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + feature.rank.getValue()));
               formatting = rankColor(feature.rank.getValue());
            }
         }

         if (feature.elements.isSelected("Coins")) {
            replaced = replace(COINS_LINE, replaced, sanitizeNumber(feature.coins.getValue()));
         }

         if (feature.elements.isSelected("Tokens")) {
            replaced = replace(TOKENS_LINE, replaced, sanitizeNumber(feature.tokens.getValue()));
         }

         if (replaced.equals(value)) {
            return original;
         } else {
            Text result = Text.literal(replaced).fillStyle(original.getStyle());
            return (Text)(formatting == null ? result : result.copy().formatted(formatting));
         }
      } else {
         return original;
      }
   }

   private static String replace(Pattern pattern, String input, String replacement) {
      Matcher matcher = pattern.matcher(input);
      return !matcher.find() ? input : matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + replacement));
   }

   private static String sanitizeNumber(String raw) {
      String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
      if (digits.isEmpty()) {
         return "0";
      } else {
         try {
            return String.format(Locale.US, "%,d", Long.parseLong(digits));
         } catch (NumberFormatException var3) {
            return digits.substring(0, Math.min(18, digits.length()));
         }
      }
   }

   private static Formatting rankColor(String rank) {
      String var1 = rank.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "страж", "guardian" -> Formatting.YELLOW;
         case "барон", "сквид", "baron", "squid" -> Formatting.AQUA;
         case "герой", "hero" -> Formatting.GREEN;
         case "аспид", "aspid" -> Formatting.DARK_AQUA;
         case "глава", "титан", "head", "titan" -> Formatting.GOLD;
         case "элита", "elite" -> Formatting.DARK_PURPLE;
         case "принц", "князь", "prince", "duke" -> Formatting.RED;
         case "герцог", "herzog" -> Formatting.DARK_RED;
         default -> Formatting.WHITE;
      };
   }
}
