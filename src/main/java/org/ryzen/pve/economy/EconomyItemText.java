package org.ryzen.pve.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class EconomyItemText {
   private EconomyItemText() {
   }

   public static List<String> lines(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         ArrayList<String> lines = new ArrayList<>();
         lines.add(stack.getName().getString());
         LoreComponent lore = (LoreComponent)stack.get(DataComponentTypes.LORE);
         if (lore != null) {
            for (Text line : lore.lines()) {
               lines.add(line.getString());
            }
         }

         return List.copyOf(lines);
      } else {
         return List.of();
      }
   }

   public static String combined(ItemStack stack) {
      return String.join("\n", lines(stack));
   }

   public static boolean containsAny(ItemStack stack, String... markers) {
      return EconomyTextParser.containsAny(combined(stack), markers);
   }

   public static OptionalLong listingPrice(ItemStack stack) {
      long largest = -1L;

      for (String line : lines(stack)) {
         String normalized = EconomyTextParser.normalize(line);
         if (EconomyTextParser.containsAny(normalized, "price", "cost", "цена", "стоимость", "за все", "за штуку", "монет", "$")) {
            OptionalLong amount = EconomyTextParser.largestAmount(normalized);
            if (amount.isPresent()) {
               largest = Math.max(largest, amount.getAsLong());
            }
         }
      }

      return largest < 0L ? OptionalLong.empty() : OptionalLong.of(largest);
   }

   public static OptionalLong listingUnitPrice(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         long largest = -1L;

         for (String line : lines(stack)) {
            String normalized = EconomyTextParser.normalize(line);
            if (EconomyTextParser.containsAny(normalized, "price", "cost", "цена", "стоимость", "за все", "за штуку", "per item", "each", "монет", "$")) {
               OptionalLong parsed = EconomyTextParser.largestAmount(normalized);
               if (!parsed.isEmpty()) {
                  boolean explicitlyPerItem = EconomyTextParser.containsAny(normalized, "за штуку", "per item", "each", "1 шт");
                  long unit = explicitlyPerItem ? parsed.getAsLong() : Math.max(1L, parsed.getAsLong() / (long)Math.max(1, stack.getCount()));
                  largest = Math.max(largest, unit);
               }
            }
         }

         return largest < 0L ? OptionalLong.empty() : OptionalLong.of(largest);
      } else {
         return OptionalLong.empty();
      }
   }
}
