package org.ryzen.feature.impl.misc.collector;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import org.ryzen.feature.impl.misc.DonItems;
import org.ryzen.feature.impl.misc.autobuy.AuctionUtils;

@Environment(EnvType.CLIENT)
public final class CollectorItem {
   private final String id;
   private final String name;
   private final Item item;
   private final boolean strictName;
   private final DonItems.DonItem profile;
   private final Predicate<ItemStack> signature;
   private final List<CollectorCondition> conditions;
   private boolean enabled;
   private boolean scanCheapest;
   private int count;

   public CollectorItem(String name, Item item, int count, boolean enabled, boolean scanCheapest, boolean strictName) {
      this(name, item, count, enabled, scanCheapest, strictName, null, null, List.of());
   }

   public CollectorItem(
      String name,
      Item item,
      int count,
      boolean enabled,
      boolean scanCheapest,
      boolean strictName,
      DonItems.DonItem profile,
      Predicate<ItemStack> signature,
      List<CollectorCondition> conditions
   ) {
      this.name = name;
      this.id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-я0-9]+", "-").replaceAll("(^-|-$)", "");
      this.item = item;
      this.count = Math.max(1, Math.min(this.maxTargetCount(), count));
      this.enabled = enabled;
      this.scanCheapest = scanCheapest;
      this.strictName = strictName;
      this.profile = profile;
      this.signature = signature;
      this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
   }

   public ItemStack icon() {
      return new ItemStack(this.item, Math.min(this.count, this.item.getMaxCount()));
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public void setScanCheapest(boolean scanCheapest) {
      this.scanCheapest = scanCheapest;
   }

   public void setCount(int count) {
      this.count = Math.max(1, Math.min(this.maxTargetCount(), count));
   }

   public boolean matches(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && stack.isOf(this.item)) {
         if (this.profile != null && !this.profile.matches(stack)) {
            return false;
         } else if (this.signature != null && !this.signature.test(stack)) {
            return false;
         } else {
            for (CollectorCondition condition : this.conditions) {
               if (!condition.matches(stack)) {
                  return false;
               }
            }

            if (this.strictName && this.profile == null && this.signature == null && this.conditions.isEmpty()) {
               String lot = AuctionUtils.cleanName(stack.getName().getString());
               String expected = AuctionUtils.cleanName(this.name);
               if (!lot.equals(expected) && !lot.contains(expected)) {
                  String[] words = expected.split(" ");
                  int matched = 0;

                  for (String word : words) {
                     if (word.length() >= 4 && lot.contains(word)) {
                        matched++;
                     }
                  }

                  return matched >= Math.min(2, words.length);
               } else {
                  return true;
               }
            } else {
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public int maxTargetCount() {
      int vanilla = this.item.getMaxCount();
      if (vanilla > 1) {
         return vanilla;
      } else if (this.item == Items.TOTEM_OF_UNDYING || this.item == Items.SPLASH_POTION) {
         return 6;
      } else {
         return this.item instanceof PotionItem ? 16 : 1;
      }
   }
   public String getId() {
      return this.id;
   }
   public String getName() {
      return this.name;
   }
   public Item getItem() {
      return this.item;
   }
   public boolean isStrictName() {
      return this.strictName;
   }
   public DonItems.DonItem getProfile() {
      return this.profile;
   }
   public Predicate<ItemStack> getSignature() {
      return this.signature;
   }
   public List<CollectorCondition> getConditions() {
      return this.conditions;
   }
   public boolean isEnabled() {
      return this.enabled;
   }
   public boolean isScanCheapest() {
      return this.scanCheapest;
   }
   public int getCount() {
      return this.count;
   }
}
