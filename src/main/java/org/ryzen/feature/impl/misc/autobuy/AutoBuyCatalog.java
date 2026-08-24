package org.ryzen.feature.impl.misc.autobuy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public final class AutoBuyCatalog {
   private static final List<AutoBuyItem> ITEMS = new ArrayList<>();
   private static final Set<String> SEEN_IDS = new HashSet<>();
   private static boolean built;

   private AutoBuyCatalog() {
   }

   public static List<AutoBuyItem> all() {
      ensureBuilt();
      return Collections.unmodifiableList(ITEMS);
   }

   public static List<AutoBuyItem> forFunTimeFamily() {
      ensureBuilt();
      List<AutoBuyItem> result = new ArrayList<>();

      for (AutoBuyItem item : ITEMS) {
         if (!item.getCategory().isHolyWorld()) {
            result.add(item);
         }
      }

      return result;
   }

   public static List<AutoBuyItem> forHolyWorld() {
      return byCategory(AutoBuyItemCategory.HOLYWORLD);
   }

   public static List<AutoBuyItem> forServer(String serverMode) {
      return AutoBuyServer.isHolyFamily(serverMode) ? forHolyWorld() : forFunTimeFamily();
   }

   public static List<AutoBuyItem> byCategory(AutoBuyItemCategory category) {
      ensureBuilt();
      List<AutoBuyItem> result = new ArrayList<>();

      for (AutoBuyItem item : ITEMS) {
         if (item.getCategory() == category) {
            result.add(item);
         }
      }

      return result;
   }

   public static List<AutoBuyItem> byCategoryForServer(AutoBuyItemCategory category, String serverMode) {
      if (category == null) {
         return forServer(serverMode);
      } else {
         return category.matchesServer(serverMode) ? byCategory(category) : List.of();
      }
   }

   private static synchronized void ensureBuilt() {
      if (!built) {
         built = true;
         SEEN_IDS.clear();
         ITEMS.clear();
         buildKrush();
         buildSpheres();
         buildTalismans();
         buildPotions();
         buildMisc();
         buildHolyWorld();
      }
   }

   private static void buildKrush() {
      krush("Шлем Крушителя", 150000, Items.NETHERITE_HELMET);
      krush("Нагрудник Крушителя", 250000, Items.NETHERITE_CHESTPLATE);
      krush("Поножи Крушителя", 200000, Items.NETHERITE_LEGGINGS);
      krush("Ботинки Крушителя", 150000, Items.NETHERITE_BOOTS);
      krush("Меч Крушителя", 300000, Items.NETHERITE_SWORD);
      krush("Булава Крушителя", 280000, Items.MACE, "Булава");
      krush("Кирка Крушителя", 200000, Items.NETHERITE_PICKAXE);
   }

   private static void buildSpheres() {
      sphere("Сфера Афины", 80000);
      sphere("Сфера Хаоса", 80000);
      sphere("Сфера Сатира", 80000);
      sphere("Сфера Бестии", 80000);
      sphere("Сфера Ареса", 80000);
      sphere("Сфера Гидры", 80000);
      sphere("Сфера Икара", 80000);
      sphere("Сфера Титана", 80000);
      sphere("Сфера Эрида", 80000);
   }

   private static void buildTalismans() {
      talisman("Талисман Крушителя", 150000);
      talisman("Талисман Раздора", 100000);
      talisman("Талисман Тирана", 100000);
      talisman("Талисман Ярости", 100000);
      talisman("Талисман Вихря", 100000);
      talisman("Талисман Мрака", 100000);
      talisman("Талисман Демона", 100000);
      talisman("Талисман Карателя", 100000);
   }

   private static void buildPotions() {
      potion("Зелье Ассасина", 25000, 3355443);
      potion("Зелье Гнева", 25000, 10040115);
      potion("Хлопушка", 20000, 16738740);
      potion("Святая Вода", 20000, 16777215, "Святая вода");
      potion("Зелье Палладина", 30000, 65535);
      potion("Зелье Радиации", 25000, 3329330);
      potion("Снотворное", 20000, 4737096);
      potion("Мандариновый сок", 15000, 14077507);
      potion("Зелье Агента", 20000, 5635925);
      potion("Зелье Киллера", 25000, 11141120);
      potion("Зелье Медика", 20000, 5635925);
      potion("Зелье Победителя", 25000, 16755200, "Зелье победителя");
   }

   private static void buildMisc() {
      item("Отмычка к сферам", AutoBuyItemCategory.MISC, 50000, Items.TRIPWIRE_HOOK, "Отмычка к Сферам", "Отмычка");
      item("Чарка", AutoBuyItemCategory.MISC, 25000, Items.ENCHANTED_GOLDEN_APPLE, "Зачарованное золотое яблоко");
      item("Маяк", AutoBuyItemCategory.MISC, 80000, Items.BEACON, "Загадочный маяк");
      item("Модификатор полёта", AutoBuyItemCategory.MISC, 60000, Items.FEATHER, "Модификатор полета");
      item("Незеритовый слиток", AutoBuyItemCategory.MISC, 3000, Items.NETHERITE_INGOT);
      item("Шалкер", AutoBuyItemCategory.MISC, 20000, Items.SHULKER_BOX, "Шалкеровый ящик");
      item("Явная Пыль", AutoBuyItemCategory.MISC, 15000, Items.SUGAR, "Явная пыль");
      item("Дезориентация", AutoBuyItemCategory.MISC, 15000, Items.ENDER_EYE);
      item("Трапка", AutoBuyItemCategory.MISC, 40000, Items.NETHERITE_SCRAP);
      item("Пласт", AutoBuyItemCategory.MISC, 30000, Items.DRIED_KELP);
      item("Опыт 15", AutoBuyItemCategory.MISC, 5000, Items.EXPERIENCE_BOTTLE, "Пузырек опыта [15 ур]", "Пузырёк опыта [15 ур]");
      item("Опыт 30", AutoBuyItemCategory.MISC, 10000, Items.EXPERIENCE_BOTTLE, "Пузырек опыта [30 ур]", "Пузырёк опыта [30 Ур.]");
      item("Опыт 50", AutoBuyItemCategory.MISC, 20000, Items.EXPERIENCE_BOTTLE, "Пузырек опыта [50 ур]");
      item("Вайт", AutoBuyItemCategory.MISC, 25000, Items.TNT, "TNT - TIER WHITE", "TNT WHITE");
      item("Блек", AutoBuyItemCategory.MISC, 40000, Items.TNT, "TNT - TIER BLACK", "TNT BLACK");
      item("Тотем бессмертия", AutoBuyItemCategory.MISC, 5000, Items.TOTEM_OF_UNDYING);
      item("Элитры", AutoBuyItemCategory.MISC, 50000, Items.ELYTRA);
      item("Эндер жемчуг", AutoBuyItemCategory.MISC, 500, Items.ENDER_PEARL, "Эндер-жемчуг");
      item("Золотое яблоко", AutoBuyItemCategory.MISC, 1000, Items.GOLDEN_APPLE);
      item("Спавнер", AutoBuyItemCategory.MISC, 100000, Items.SPAWNER);
      item("Незеритовый блок", AutoBuyItemCategory.MISC, 25000, Items.NETHERITE_BLOCK);
      item("Заряд ветра", AutoBuyItemCategory.MISC, 5000, Items.WIND_CHARGE);
   }

   private static void buildHolyWorld() {
      holy("Шлем Infinity", 200000, Items.NETHERITE_HELMET);
      holy("Нагрудник Infinity", 300000, Items.NETHERITE_CHESTPLATE);
      holy("Поножи Infinity", 250000, Items.NETHERITE_LEGGINGS);
      holy("Ботинки Infinity", 200000, Items.NETHERITE_BOOTS);
      holy("Шлем Eternity", 120000, Items.NETHERITE_HELMET);
      holy("Нагрудник Eternity", 180000, Items.NETHERITE_CHESTPLATE);
      holy("Штаны Eternity", 150000, Items.NETHERITE_LEGGINGS, "Поножи Eternity");
      holy("Ботинки Eternity", 120000, Items.NETHERITE_BOOTS);
      holy("Шлем солнца", 100000, Items.GOLDEN_HELMET);
      holy("Броневая элитра", 250000, Items.ELYTRA);
      holy("Меч Eternity", 200000, Items.NETHERITE_SWORD);
      holy("Кирка Eternity", 150000, Items.NETHERITE_PICKAXE);
      holy("Арбалет Eternity", 100000, Items.CROSSBOW);
      holy("Громовержец", 150000, Items.TRIDENT);
      holySphere("Сфера Цербера", 100000, "Cerber");
      holySphere("Сфера Флеша", 100000, "Flash");
      holySphere("Сфера Имморталити", 100000, "Сфера ɪᴍᴍᴏʀᴛᴀʟɪᴛʏ", "Immortal");
      holySphere("Сфера Арморталити", 100000, "Сфера ᴀʀᴍᴏʀᴛᴀʟɪᴛʏ", "Armortality");
      holySphere("Сфера на Скорость III", 80000, "Сфера на скорость 3", "Speed3");
      holySphere("Сфера Eternity", 100000, "Eternity");
      holySphere("Сфера Stinger", 100000, "Stinger");
      holySphere("Сфера на броня III скорость II", 90000, "Сфера на броня 3", "Mythical3");
      holySphere("Сфера на урон II броня III", 90000);
      holySphere("Сфера на броня II урон III", 90000);
      holy("Талисман Stinger", 120000, Items.TOTEM_OF_UNDYING);
      holy("Талисман Infinity", 150000, Items.TOTEM_OF_UNDYING);
      holy("Талисман Eternity", 130000, Items.TOTEM_OF_UNDYING);
      holy("Легендарный талисман", 100000, Items.TOTEM_OF_UNDYING);
      holy("Пузырек с 15 уровнем", 5000, Items.EXPERIENCE_BOTTLE, "15");
      holy("Пузырек с 50 уровнем", 25000, Items.EXPERIENCE_BOTTLE, "50");
      holy("Пузырек с 100 уровнем", 80000, Items.EXPERIENCE_BOTTLE, "100");
      holy("Обычный пузырек опыта", 1000, Items.EXPERIENCE_BOTTLE, "опыт");
      holy("Рюкзак I уровень", 30000, Items.PINK_SHULKER_BOX, "рюкзак 1 уровень");
      holy("Рюкзак II уровень", 50000, Items.LIGHT_BLUE_SHULKER_BOX, "рюкзак 2 уровень");
      holy("Рюкзак III уровень", 80000, Items.RED_SHULKER_BOX, "рюкзак 3 уровень");
      holy("Рюкзак IV уровень", 120000, Items.MAGENTA_SHULKER_BOX, "рюкзак 4 уровень");
      holy("Рюкзак Infinity", 200000, Items.LIME_SHULKER_BOX, "рюкзак infinity");
      holy("Взрывная трапка", 40000, Items.PRISMARINE_SHARD);
      holy("Стан", 35000, Items.NETHER_STAR);
      holy("Взрывная штучка", 20000, Items.FIRE_CHARGE);
      holy("Ком снега", 15000, Items.SNOWBALL);
      holy("Руна «Бессмертие»", 50000, Items.ORANGE_DYE, "Бессмертие", "Руна Бессмертие");
      holyPotion("Улучшенное зелье силы", 20000, 16733440);
      holyPotion("Улучшенное зелье скорости", 20000, 3386111);
      holyPotion("Зелье исцеления", 15000, 16711680, "Зелье исцеление");
      holyPotion("Зелье черепашьей мощи", 18000, 8369336);
      holyPotion("Зелье черепашьей мощи II", 25000, 8369336);
      holy("Охотник", 40000, Items.NETHERITE_SWORD);
      holy("Снеговик", 30000, Items.SNOW_BLOCK);
      holy("Иллюминатор", 30000, Items.SEA_LANTERN);
      holy("Эндермен", 35000, Items.ENDER_PEARL);
      holy("Анти Фантом", 25000, Items.PHANTOM_MEMBRANE);
      holy("Телекинез", 30000, Items.HONEY_BLOCK);
      holy("Гравитация", 30000, Items.FEATHER);
      holy("Вампиризм", 40000, Items.WITHER_SKELETON_SKULL);
      holy("Справедливость", 35000, Items.POTION);
      holy("Универсальный ключ", 50000, Items.TRIPWIRE_HOOK);
      holy("Фармер", 40000, Items.DIAMOND_SWORD);
      holy("Золотая морковь", 500, Items.GOLDEN_CARROT);
      holy("Плод хоруса", 300, Items.CHORUS_FRUIT);
      holy("Артефакт", 80000, Items.CONDUIT);
      holy("Фейерверк", 200, Items.FIREWORK_ROCKET);
      holy("Порох", 100, Items.GUNPOWDER);
      holy("Боевой фрагмент", 15000, Items.PRISMARINE_CRYSTALS);
      holy("Взрывчатое вещество", 20000, Items.CLAY);
      holy("Динамит А", 25000, Items.TNT, "Динамит A");
      holy("Динамит B", 30000, Items.TNT, "динамит б");
      holy("Динамит B2", 35000, Items.TNT, "динамит б2");
      holy("C4 ВзРыВчАтКа", 50000, Items.TNT, "с4 взрывчатка", "C4");
      holy("Золотая кирка Джейка", 60000, Items.GOLDEN_PICKAXE);
      holy("Осколок сферы", 20000, Items.PLAYER_HEAD);
   }

   private static void krush(String name, int price, Item icon, String... aliases) {
      item(name, AutoBuyItemCategory.KRUSH, price, namedIcon(icon, name), aliases);
   }

   private static void sphere(String name, int price) {
      item(name, AutoBuyItemCategory.SPHERES, price, Items.PLAYER_HEAD, "[★] " + name);
   }

   private static void talisman(String name, int price, String... aliases) {
      item(name, AutoBuyItemCategory.TALISMANS, price, Items.TOTEM_OF_UNDYING, aliases);
   }

   private static void holy(String name, int price, Item icon, String... aliases) {
      item(name, AutoBuyItemCategory.HOLYWORLD, price, icon, aliases);
   }

   private static void holySphere(String name, int price, String... aliases) {
      item(name, AutoBuyItemCategory.HOLYWORLD, price, Items.PLAYER_HEAD, aliases);
   }

   private static void holyPotion(String name, int price, int color, String... aliases) {
      addUnique(new AutoBuyItem(name, AutoBuyItemCategory.HOLYWORLD, price, tintedPotion(Items.POTION, name, color), aliases));
   }

   private static void potion(String name, int price, int color, String... aliases) {
      addUnique(new AutoBuyItem(name, AutoBuyItemCategory.POTIONS, price, tintedPotion(Items.SPLASH_POTION, name, color), aliases));
   }

   private static void item(String name, AutoBuyItemCategory category, int price, Item icon, String... aliases) {
      addUnique(new AutoBuyItem(name, category, price, icon, aliases));
   }

   private static void item(String name, AutoBuyItemCategory category, int price, Supplier<ItemStack> icon, String... aliases) {
      addUnique(new AutoBuyItem(name, category, price, icon, aliases));
   }

   private static void addUnique(AutoBuyItem item) {
      if (item != null && !item.getId().isEmpty() && SEEN_IDS.add(item.getId())) {
         ITEMS.add(item);
      }
   }

   private static Supplier<ItemStack> tintedPotion(Item base, String name, int color) {
      return () -> {
         ItemStack stack = new ItemStack(base);
         stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.AQUA));
         stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.of(color), List.of(), Optional.empty()));
         return stack;
      };
   }

   private static Supplier<ItemStack> namedIcon(Item item, String name) {
      return () -> {
         ItemStack stack = new ItemStack(item);
         stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(new Formatting[]{Formatting.BOLD, Formatting.DARK_RED}));
         stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("[★] Оригинальный предмет").formatted(Formatting.GRAY))));
         return stack;
      };
   }
}
