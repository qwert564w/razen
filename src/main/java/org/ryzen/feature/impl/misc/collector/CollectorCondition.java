package org.ryzen.feature.impl.misc.collector;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import org.ryzen.feature.impl.misc.autobuy.AuctionUtils;

@Environment(EnvType.CLIENT)
public final class CollectorCondition {
   private static final String[] ROMAN = new String[]{"", "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
   private final String id;
   private final CollectorCondition.Kind kind;
   private final String key;
   private final String label;
   private final boolean adjustable;
   private boolean enabled;
   private int level;

   private CollectorCondition(CollectorCondition.Kind kind, String key, String label, int level, boolean enabled, boolean adjustable) {
      this.kind = kind;
      this.key = key == null ? "" : key.toLowerCase(Locale.ROOT);
      this.label = label == null ? this.key : label;
      this.id = kind.name().toLowerCase(Locale.ROOT) + "-" + this.key.replaceAll("[^a-zа-яё0-9]+", "-").replaceAll("(^-|-$)", "");
      this.level = Math.max(1, Math.min(10, level));
      this.enabled = enabled;
      this.adjustable = adjustable;
   }

   public static CollectorCondition enchant(String path, String label, int level) {
      return new CollectorCondition(CollectorCondition.Kind.ENCHANTMENT, path, label, level, true, true);
   }

   public static CollectorCondition lore(String marker, int level) {
      return new CollectorCondition(CollectorCondition.Kind.LORE, marker, marker, level, true, level > 0);
   }

   public static CollectorCondition lore(String marker, int level, boolean enabled) {
      return new CollectorCondition(CollectorCondition.Kind.LORE, marker, marker, Math.max(1, level), enabled, level > 0);
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public void setLevel(int level) {
      this.level = Math.max(1, Math.min(10, level));
   }

   public boolean matches(ItemStack stack) {
      if (!this.enabled) {
         return true;
      } else {
         return this.kind == CollectorCondition.Kind.ENCHANTMENT ? this.matchesEnchantment(stack) : this.matchesLore(stack);
      }
   }

   private boolean matchesEnchantment(ItemStack stack) {
      for (Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
         Optional<RegistryKey<Enchantment>> resourceKey = ((RegistryEntry)entry.getKey()).getKey();
         if (resourceKey.isPresent() && resourceKey.get().getValue().getPath().equals(this.key) && entry.getIntValue() >= this.level) {
            return true;
         }
      }

      return false;
   }

   private boolean matchesLore(ItemStack stack) {
      LoreComponent lore = (LoreComponent)stack.get(DataComponentTypes.LORE);
      List<Text> lines = lore == null ? List.of() : lore.lines();
      String expected = AuctionUtils.cleanName(this.key);

      for (Text component : lines) {
         String line = AuctionUtils.cleanName(component.getString());
         if (line.contains(expected) && (!this.adjustable || containsLevel(line, this.level))) {
            return true;
         }
      }

      return false;
   }

   private static boolean containsLevel(String line, int level) {
      if (level <= 1) {
         return true;
      } else {
         String decimal = Integer.toString(level);
         String roman = level < ROMAN.length ? ROMAN[level] : "";
         return line.matches(".*(?:^|\\s)" + decimal + "(?:\\s|$).*") || !roman.isEmpty() && line.matches(".*(?:^|\\s)" + roman + "(?:\\s|$).*");
      }
   }
   public String getId() {
      return this.id;
   }
   public CollectorCondition.Kind getKind() {
      return this.kind;
   }
   public String getKey() {
      return this.key;
   }
   public String getLabel() {
      return this.label;
   }
   public boolean isAdjustable() {
      return this.adjustable;
   }
   public boolean isEnabled() {
      return this.enabled;
   }
   public int getLevel() {
      return this.level;
   }

   @Environment(EnvType.CLIENT)
   public static enum Kind {
      ENCHANTMENT,
      LORE;
   }
}
