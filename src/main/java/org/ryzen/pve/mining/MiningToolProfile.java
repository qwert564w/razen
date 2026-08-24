package org.ryzen.pve.mining;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.ryzen.pve.economy.EconomyItemText;
import org.ryzen.pve.economy.EconomyTextParser;
import org.ryzen.pve.server.ServerProfile;

@Environment(EnvType.CLIENT)
public enum MiningToolProfile {
   STANDARD(0, 1),
   BULLDOZER_I(1, 9),
   BULLDOZER_II(2, 27);

   private static final Pattern BULLDOZER = Pattern.compile("(?iuU)\\bбульдозер\\s*(?:[:\\-]?\\s*)?(ii|i|2|1)\\b");
   private final int bulldozerLevel;
   private final int maximumBlocksPerBreak;

   private MiningToolProfile(int bulldozerLevel, int maximumBlocksPerBreak) {
      this.bulldozerLevel = bulldozerLevel;
      this.maximumBlocksPerBreak = maximumBlocksPerBreak;
   }

   public int bulldozerLevel() {
      return this.bulldozerLevel;
   }

   public int maximumBlocksPerBreak() {
      return this.maximumBlocksPerBreak;
   }

   public int durabilityReserve(ItemStack stack, double minimumPercent) {
      if (stack != null && !stack.isEmpty() && stack.isDamageable()) {
         int configured = (int)Math.ceil((double)stack.getMaxDamage() * Math.max(0.0, minimumPercent) / 100.0);
         return configured + this.maximumBlocksPerBreak;
      } else {
         return 0;
      }
   }

   public String displayName() {
      return switch (this) {
         case STANDARD -> "Standard";
         case BULLDOZER_I -> "Бульдозер I (3x3x1)";
         case BULLDOZER_II -> "Бульдозер II (3x3x3)";
      };
   }

   public static MiningToolProfile detect(ServerProfile server, ItemStack stack) {
      if (server == ServerProfile.FUNTIME && stack != null && !stack.isEmpty()) {
         MiningToolProfile enchantmentProfile = STANDARD;

         for (Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            String id = entry.getKey().getKey().map(key -> key.getValue().toString()).orElse("");
            String description = entry.getKey().value().description().getString();
            if (EconomyTextParser.containsAny(id + " " + description, "bulldozer", "бульдозер")) {
               if (entry.getIntValue() >= 2) {
                  return BULLDOZER_II;
               }

               enchantmentProfile = BULLDOZER_I;
            }
         }

         return enchantmentProfile != STANDARD ? enchantmentProfile : detect(EconomyItemText.combined(stack));
      } else {
         return STANDARD;
      }
   }

   static MiningToolProfile detect(String text) {
      Matcher matcher = BULLDOZER.matcher(EconomyTextParser.normalize(text));

      MiningToolProfile detected;
      for (detected = STANDARD; matcher.find(); detected = BULLDOZER_I) {
         String level = matcher.group(1);
         if ("ii".equalsIgnoreCase(level) || "2".equals(level)) {
            return BULLDOZER_II;
         }
      }

      return detected;
   }
}
