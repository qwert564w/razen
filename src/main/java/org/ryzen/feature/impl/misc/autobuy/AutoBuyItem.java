package org.ryzen.feature.impl.misc.autobuy;

import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

@Environment(EnvType.CLIENT)
public final class AutoBuyItem {
   private final String id;
   private final String name;
   private final AutoBuyItemCategory category;
   private final Supplier<ItemStack> iconFactory;
   private final String[] matchAliases;
   private boolean enabled;
   private int buyPrice;
   private int minQty;
   private ItemStack cachedIcon;

   public AutoBuyItem(String name, AutoBuyItemCategory category, int defaultPrice, Item iconItem, String... aliases) {
      this(name, category, defaultPrice, (Supplier<ItemStack>)(() -> new ItemStack(iconItem != null ? iconItem : Items.BARRIER)), aliases);
   }

   public AutoBuyItem(String name, AutoBuyItemCategory category, int defaultPrice, Supplier<ItemStack> iconFactory, String... aliases) {
      this.name = name == null ? "" : name;
      this.id = normalizeKey(this.name);
      this.category = category == null ? AutoBuyItemCategory.MISC : category;
      this.iconFactory = iconFactory != null ? iconFactory : () -> new ItemStack(Items.BARRIER);
      this.matchAliases = aliases == null ? new String[0] : aliases;
      this.enabled = false;
      this.buyPrice = Math.max(1, defaultPrice);
      this.minQty = 1;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public void setBuyPrice(int buyPrice) {
      this.buyPrice = Math.max(1, buyPrice);
   }

   public void setMinQty(int minQty) {
      this.minQty = Math.max(1, minQty);
   }

   public ItemStack icon() {
      ItemStack cached = this.cachedIcon;
      if (cached == null || cached.isEmpty()) {
         cached = this.createIcon();
         this.cachedIcon = cached;
      }

      return cached;
   }

   public ItemStack createIcon() {
      try {
         ItemStack stack = this.iconFactory.get();
         return stack != null && !stack.isEmpty() ? stack.copy() : new ItemStack(Items.BARRIER);
      } catch (Throwable var2) {
         return new ItemStack(Items.BARRIER);
      }
   }

   public boolean matchesName(String rawName) {
      return this.matchScore(rawName) > 0;
   }

   public int matchScore(String rawName) {
      String cleaned = AuctionUtils.cleanName(rawName);
      if (cleaned.isEmpty()) {
         return 0;
      } else {
         int best = scoreAgainst(cleaned, AuctionUtils.cleanName(this.name));

         for (String alias : this.matchAliases) {
            best = Math.max(best, scoreAgainst(cleaned, AuctionUtils.cleanName(alias)));
         }

         return best;
      }
   }

   private static int scoreAgainst(String auctionName, String catalogKey) {
      if (catalogKey.isEmpty()) {
         return 0;
      } else if (auctionName.equals(catalogKey)) {
         return 2000 + catalogKey.length();
      } else if (catalogKey.length() < 8) {
         return 0;
      } else if (auctionName.contains(catalogKey)) {
         return 1000 + catalogKey.length();
      } else {
         return catalogKey.contains(auctionName) && auctionName.length() >= 10 ? 500 + auctionName.length() : 0;
      }
   }

   public static String normalizeKey(String name) {
      return name == null ? "" : name.replaceAll("\\u00a7.", "").replaceAll("[★\\[\\]⚒❄\ud83c\udf79]", "").trim().toLowerCase(Locale.ROOT);
   }
   public String getId() {
      return this.id;
   }
   public String getName() {
      return this.name;
   }
   public AutoBuyItemCategory getCategory() {
      return this.category;
   }
   public String[] getMatchAliases() {
      return this.matchAliases;
   }
   public boolean isEnabled() {
      return this.enabled;
   }
   public int getBuyPrice() {
      return this.buyPrice;
   }
   public int getMinQty() {
      return this.minQty;
   }
}
