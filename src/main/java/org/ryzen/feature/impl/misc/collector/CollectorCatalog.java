package org.ryzen.feature.impl.misc.collector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.ryzen.feature.impl.misc.DonItems;

@Environment(EnvType.CLIENT)
public final class CollectorCatalog {
   private static final List<CollectorItem> ITEMS = build();

   private CollectorCatalog() {
   }

   public static List<CollectorItem> all() {
      return ITEMS;
   }

   private static List<CollectorItem> build() {
      List<CollectorItem> items = new ArrayList<>();
      items.add(
         configured(
            "Незеритовый меч",
            Items.NETHERITE_SWORD,
            1,
            true,
            true,
            enchant("sharpness", "Острота", 7),
            enchant("fire_aspect", "Заговор огня", 2),
            lore("Яд", 3),
            lore("Вампиризм", 2),
            lore("Окисление", 2),
            lore("Опытный", 3, false),
            lore("Детекция", 3)
         )
      );
      items.add(
         configured(
            "Булава", Items.MACE, 1, true, true, enchant("sharpness", "Острота", 7), enchant("breach", "Пробитие", 3), enchant("density", "Плотность", 5)
         )
      );
      items.add(
         configured(
            "Трезубец", Items.TRIDENT, 1, true, true, lore("Ступор", 3), lore("Притяжение", 2), lore("Скаут", 3), lore("Возвращение", 0), lore("Подрывник", 0)
         )
      );
      items.add(
         configured(
            "Незеритовый шлем",
            Items.NETHERITE_HELMET,
            1,
            true,
            true,
            enchant("protection", "Защита", 5),
            enchant("unbreaking", "Прочность", 5),
            enchant("respiration", "Подводное дыхание", 3),
            enchant("mending", "Починка", 1)
         )
      );
      items.add(
         configured(
            "Незеритовый нагрудник",
            Items.NETHERITE_CHESTPLATE,
            1,
            true,
            true,
            enchant("protection", "Защита", 5),
            enchant("unbreaking", "Прочность", 5),
            enchant("mending", "Починка", 1)
         )
      );
      items.add(
         configured(
            "Незеритовые поножи",
            Items.NETHERITE_LEGGINGS,
            1,
            true,
            true,
            enchant("protection", "Защита", 5),
            enchant("unbreaking", "Прочность", 5),
            enchant("mending", "Починка", 1)
         )
      );
      items.add(
         configured(
            "Незеритовые ботинки",
            Items.NETHERITE_BOOTS,
            1,
            true,
            true,
            enchant("protection", "Защита", 5),
            enchant("unbreaking", "Прочность", 5),
            enchant("depth_strider", "Подводная ходьба", 3),
            enchant("mending", "Починка", 1)
         )
      );
      items.add(configured("Трапка", Items.NETHERITE_SCRAP, 8, true, false, lore("Каст: Нерушимая клетка", 0)));
      items.add(configured("Явная пыль", Items.SUGAR, 12, true, false, lore("Каст: Световая вспышка", 0)));
      items.add(configured("Божья аура", Items.PHANTOM_MEMBRANE, 4, true, false, lore("Каст: Божественная аура", 0)));
      items.add(configured("Дезориентация", Items.ENDER_EYE, 16, true, false, lore("Каст: Звуковая волна", 0)));
      items.add(plain("Заряд ветра", Items.WIND_CHARGE, 32, true));
      items.add(configured("Пласт", Items.DRIED_KELP, 16, true, false, lore("Каст: Нерушимая стена", 0)));
      items.add(configured("Снежок заморозка", Items.SNOWBALL, 4, true, false, lore("Каст: Ледяная сфера", 0)));
      items.add(plain("Перка", Items.ENDER_PEARL, 16, true));
      items.add(plain("Тотем бессмертия", Items.TOTEM_OF_UNDYING, 1, true));
      items.add(
         configured(
            "Арбалет",
            Items.CROSSBOW,
            1,
            true,
            false,
            enchant("quick_charge", "Быстрая перезарядка", 3),
            enchant("mending", "Починка", 1),
            enchant("multishot", "Тройной выстрел", 1)
         )
      );
      items.add(plain("Золотое яблоко", Items.GOLDEN_APPLE, 16, true));
      items.add(plain("Зачарованное золотое яб", Items.ENCHANTED_GOLDEN_APPLE, 8, true));
      items.add(plain("Золотая морковь", Items.GOLDEN_CARROT, 64, true));
      items.add(plain("Хорус", Items.CHORUS_FRUIT, 64, true));
      items.add(plain("Элитры", Items.ELYTRA, 1, true));
      items.add(plain("Фейерверк", Items.FIREWORK_ROCKET, 64, true));
      items.add(profile("Хлопушка", Items.SPLASH_POTION, 1, true, false, DonItems.FunTime.POPPER));
      items.add(profile("Святая вода", Items.SPLASH_POTION, 1, true, false, DonItems.FunTime.HOLY_WATER));
      items.add(profile("Зелье Гнева", Items.SPLASH_POTION, 1, true, true, DonItems.FunTime.RAGE_POTION));
      items.add(profile("Зелье Палладина", Items.SPLASH_POTION, 1, true, false, DonItems.FunTime.PALADIN_POTION));
      items.add(profile("Зелье Ассасина", Items.SPLASH_POTION, 1, true, false, DonItems.FunTime.ASSASSIN_POTION));
      items.add(profile("Зелье Радиации", Items.SPLASH_POTION, 1, true, false, DonItems.FunTime.RADIATION_POTION));
      items.add(profile("Снотворное", Items.SPLASH_POTION, 1, false, false, DonItems.FunTime.DROWSINESS_POTION));
      items.add(signature("Зелье", Items.POTION, 1, true, false, potionEffects("strength", 3, "speed", 3)));
      items.add(signature("Зелье регенерации", Items.POTION, 1, true, false, potionEffects("instant_health", 2, "regeneration", 1)));
      items.add(profile("Кровавая стрела", Items.TIPPED_ARROW, 32, true, false, DonItems.FunTime.BLOOD_ARROW));
      items.add(profile("Стрела обледенения", Items.TIPPED_ARROW, 64, false, false, DonItems.FunTime.FREEZE_ARROW));
      items.add(profile("Мучительная стрела", Items.TIPPED_ARROW, 64, false, false, DonItems.FunTime.AGONY_ARROW));
      return List.copyOf(items);
   }

   private static CollectorItem plain(String name, Item item, int count, boolean enabled) {
      return new CollectorItem(name, item, count, enabled, false, false);
   }

   private static CollectorItem configured(String name, Item item, int count, boolean enabled, boolean scan, CollectorCondition... conditions) {
      return new CollectorItem(name, item, count, enabled, scan, false, null, null, List.of(conditions));
   }

   private static CollectorItem profile(String name, Item item, int count, boolean enabled, boolean scan, DonItems.DonItem profile) {
      return new CollectorItem(name, item, count, enabled, scan, false, profile, null, List.of());
   }

   private static CollectorItem signature(String name, Item item, int count, boolean enabled, boolean scan, Predicate<ItemStack> matcher) {
      return new CollectorItem(name, item, count, enabled, scan, false, null, matcher, List.of());
   }

   private static CollectorCondition enchant(String path, String label, int level) {
      return CollectorCondition.enchant(path, label, level);
   }

   private static CollectorCondition lore(String marker, int level) {
      return CollectorCondition.lore(marker, level);
   }

   private static CollectorCondition lore(String marker, int level, boolean enabled) {
      return CollectorCondition.lore(marker, level, enabled);
   }

   private static Predicate<ItemStack> potionEffects(Object... values) {
      return stack -> {
         PotionContentsComponent contents = (PotionContentsComponent)stack.get(DataComponentTypes.POTION_CONTENTS);
         if (contents == null) {
            return false;
         } else {
            List<StatusEffectInstance> effects = new ArrayList<>();
            contents.getEffects().forEach(effects::add);

            for (int index = 0; index + 1 < values.length; index += 2) {
               String path = (String)values[index];
               int level = (Integer)values[index + 1];
               boolean found = effects.stream().anyMatch(effect -> effect.getTranslationKey().endsWith(path) && effect.getAmplifier() + 1 >= level);
               if (!found) {
                  return false;
               }
            }

            return true;
         }
      };
   }
}
