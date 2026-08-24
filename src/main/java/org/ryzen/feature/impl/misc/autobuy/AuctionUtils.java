package org.ryzen.feature.impl.misc.autobuy;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class AuctionUtils {
   private static final Pattern PRICE_DOLLAR = Pattern.compile("\\$[\\s\\u00a0]*([\\d][\\d\\s\\u00a0,.]*)");
   private static final Pattern PRICE_LABELED = Pattern.compile(
      "(?iu)(?:цена|price|стоимость|купить\\s+за)[\\s\\u00a0]*[:：]?[\\s\\u00a0]*\\$?[\\s\\u00a0]*([\\d][\\d\\s\\u00a0,.]*)"
   );
   private static final Pattern PRICE_CURRENCY = Pattern.compile("([\\d][\\d\\s\\u00a0,.]*)[\\s\\u00a0]*[¤$]");
   private static final Pattern DIGITS = Pattern.compile("([\\d][\\d\\s\\u00a0,.]{1,})");
   private static final Pattern COLOR_CODES = Pattern.compile("\\u00a7.");
   private static final Pattern NAME_MARKS = Pattern.compile("[★⚒❄\ud83c\udf79\\[\\]]");
   private static final Pattern BLANKS = Pattern.compile("[\\s\\u00a0]+");
   private static final Pattern AH_WORD = Pattern.compile("(?U)(?<!\\w)ah(?!\\w)");
   private static final Pattern LOT_WORD = Pattern.compile("(?U)\\bлот");

   private AuctionUtils() {
   }

   public static boolean isAuctionTitle(String title) {
      if (title != null && !title.isEmpty()) {
         String normalized = stripColors(title).toLowerCase(Locale.ROOT);
         return normalized.contains("аукцион")
            || normalized.contains("auction")
            || normalized.contains("поиск")
            || normalized.contains("search")
            || normalized.contains("market")
            || normalized.contains("рынок")
            || normalized.contains("listings")
            || LOT_WORD.matcher(normalized).find()
            || AH_WORD.matcher(normalized).find();
      } else {
         return false;
      }
   }

   public static boolean isSearchTitle(String title) {
      if (title != null && !title.isEmpty()) {
         String normalized = stripColors(title).toLowerCase(Locale.ROOT);
         return normalized.contains("поиск")
            || normalized.contains("search")
            || normalized.contains("результат")
            || normalized.contains("result")
            || normalized.contains("найти")
            || normalized.contains("filter");
      } else {
         return false;
      }
   }

   public static boolean isConfirmTitle(String title) {
      if (title != null && !title.isEmpty()) {
         String normalized = stripColors(title).toLowerCase(Locale.ROOT);
         return normalized.contains("подозрительн")
            || normalized.contains("подтвер")
            || normalized.contains("suspicious")
            || normalized.contains("confirm")
            || normalized.contains("покупк");
      } else {
         return false;
      }
   }

   public static int getPrice(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         LoreComponent lore = (LoreComponent)stack.get(DataComponentTypes.LORE);
         if (lore != null) {
            for (Text line : lore.lines()) {
               String text = line.getString();
               if (text != null && !text.isEmpty() && (text.contains("$") || text.contains("¤"))) {
                  int parsed = parsePrice(extractPriceString(text));
                  if (parsed > 0) {
                     return parsed;
                  }

                  int digits = parseDigitsOnly(text);
                  if (digits > 0) {
                     return digits;
                  }
               }
            }

            for (Text linex : lore.lines()) {
               String text = linex.getString();
               if (text != null && !text.isEmpty()) {
                  String lower = text.toLowerCase(Locale.ROOT);
                  if (lower.contains("цена") || lower.contains("price") || lower.contains("стоимость") || lower.contains("купить")) {
                     int parsedx = parsePrice(extractPriceString(text));
                     if (parsedx > 0) {
                        return parsedx;
                     }

                     int digits = parseDigitsOnly(text);
                     if (digits > 0) {
                        return digits;
                     }
                  }
               }
            }
         }

         String name = stack.getName().getString();
         if (name != null && (name.contains("$") || name.contains("¤"))) {
            int parsedxx = parsePrice(extractPriceString(name));
            return parsedxx > 0 ? parsedxx : parseDigitsOnly(name);
         } else {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private static int parseDigitsOnly(String text) {
      if (text != null && !text.isEmpty()) {
         String digits = text.replaceAll("[^0-9]", "");
         if (!digits.isEmpty() && digits.length() <= 12) {
            try {
               long value = Long.parseLong(digits);
               if (value <= 0L) {
                  return -1;
               } else {
                  return value > 2147483647L ? Integer.MAX_VALUE : (int)value;
               }
            } catch (NumberFormatException var4) {
               return -1;
            }
         } else {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private static String extractPriceString(String text) {
      if (text != null && !text.isEmpty()) {
         Matcher matcher = PRICE_DOLLAR.matcher(text);
         if (matcher.find()) {
            return matcher.group(1);
         } else {
            matcher = PRICE_LABELED.matcher(text);
            if (matcher.find()) {
               return matcher.group(1);
            } else {
               matcher = PRICE_CURRENCY.matcher(text);
               if (matcher.find()) {
                  return matcher.group(1);
               } else {
                  if (text.contains("$") || text.contains("¤") || text.toLowerCase(Locale.ROOT).contains("цена")) {
                     matcher = DIGITS.matcher(text);
                     if (matcher.find() && matcher.group(1).replaceAll("[\\s,.]", "").length() >= 2) {
                        return matcher.group(1);
                     }
                  }

                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   public static int parsePrice(String raw) {
      if (raw != null && !raw.isEmpty()) {
         String clean = raw.replaceAll("[\\s\\u00a0,.$¤]", "").trim();
         if (clean.isEmpty()) {
            return -1;
         } else {
            try {
               long value = Long.parseLong(clean);
               if (value <= 0L) {
                  return -1;
               } else {
                  return value > 2147483647L ? Integer.MAX_VALUE : (int)value;
               }
            } catch (NumberFormatException var4) {
               return -1;
            }
         }
      } else {
         return -1;
      }
   }

   public static String cleanName(String raw) {
      if (raw == null) {
         return "";
      } else {
         String cleaned = stripColors(raw).replaceAll("\\$[\\d\\s\\u00a0,.]+", "");
         cleaned = NAME_MARKS.matcher(cleaned).replaceAll("");
         cleaned = BLANKS.matcher(cleaned).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
         return cleaned.startsWith("+") ? cleaned.substring(1).trim() : cleaned;
      }
   }

   public static String stripColors(String raw) {
      return raw == null ? "" : COLOR_CODES.matcher(raw).replaceAll("");
   }

   public static int unitPrice(int lotPrice, int count) {
      if (lotPrice <= 0) {
         return -1;
      } else {
         int items = Math.max(1, count);
         return (int)(((long)lotPrice + (long)items - 1L) / (long)items);
      }
   }
}
