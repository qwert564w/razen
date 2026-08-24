package org.ryzen.feature.impl.misc.collector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.impl.misc.autobuy.AuctionUtils;
import org.ryzen.feature.setting.ButtonSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.menu.clickgui.ClickGuiRedactorPage;
import org.ryzen.menu.core.MenuOverlay;
import org.ryzen.menu.core.MenuPage;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class CollectorFeature extends Feature {
   private static final Pattern PAGE = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
   public final NumberSetting clickDelay = this.register(new NumberSetting("Click Delay", 450.0, 100.0, 2000.0, 50.0, " ms"));
   public final ButtonSetting startWork = this.register(new ButtonSetting("Collector", "Start Work", this::start));
   public final ButtonSetting openEditor = this.register(new ButtonSetting("Open Editor", "Redactor", () -> {
      ClickGuiRedactorPage.requestCollector();
      if (MenuOverlay.isOpen()) {
         MenuOverlay.state().openPage(MenuPage.AUTOBUY);
      }
   }));
   private List<CollectorItem> queue = List.of();
   private int index;
   private long nextActionAt;
   private long searchSentAt;
   private int pageChanges;
   private final List<CollectorFeature.PageOffer> pageOffers = new ArrayList<>();
   private final Set<Integer> scannedPages = new HashSet<>();
   private int selectedPage = -1;
   private int selectedPrice = -1;
   private boolean running;

   public CollectorFeature() {
      super("Collector", "Automatically builds the selected FunTime inventory", FeatureCategory.MISC, -1);
   }

   public boolean isRunning() {
      return this.running;
   }

   public int progressIndex() {
      return this.index;
   }

   public int progressTotal() {
      return this.queue.size();
   }

   public void start() {
      CollectorManager.get().ensureLoaded();
      this.queue = new ArrayList<>(CollectorManager.get().enabled());
      this.index = 0;
      this.nextActionAt = 0L;
      this.searchSentAt = 0L;
      this.pageChanges = 0;
      this.resetPageScan();
      this.running = !this.queue.isEmpty();
      if (!this.running) {
         ChatUtil.error("Collector: no enabled items in Redactor");
      } else {
         if (!this.isEnabled()) {
            this.setEnabled(true);
         }

         ChatUtil.info("Collector started: " + this.queue.size() + " item types");
      }
   }

   @Override
   protected void onEnable() {
      CollectorManager.get().ensureLoaded();
   }

   @Override
   protected void onDisable() {
      this.stop(false);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.running) {
         MinecraftClient client = event.getClient();
         ClientPlayerEntity player = client.player;
         if (player != null && player.networkHandler != null && client.interactionManager != null) {
            if (this.index >= this.queue.size()) {
               this.stop(true);
            } else {
               CollectorItem target = this.queue.get(this.index);
               int current = count(player, target);
               if (current >= target.getCount()) {
                  this.next(client, target);
               } else if (!hasEmptySlot(player)) {
                  ChatUtil.error("Collector stopped: inventory is full");
                  this.stop(false);
               } else {
                  long now = System.currentTimeMillis();
                  if (now >= this.nextActionAt) {
                     if (client.currentScreen instanceof HandledScreen<?> screen) {
                        String title = screen.getTitle().getString();
                        if (AuctionUtils.isConfirmTitle(title)) {
                           int confirm = findNamedSlot(screen, "купить", "buy", "подтверд", "confirm");
                           if (confirm < 0) {
                              confirm = Math.min(13, containerSlotCount(screen) - 1);
                           }

                           click(client, screen, confirm, SlotActionType.PICKUP);
                           this.nextActionAt = now + this.clickDelay.getValue().longValue();
                        } else {
                           String cleanTitle = AuctionUtils.cleanName(title);
                           if (!AuctionUtils.isAuctionTitle(title) && !cleanTitle.contains(AuctionUtils.cleanName(target.getName()))) {
                              player.closeHandledScreen();
                              this.nextActionAt = now + 500L;
                           } else if (!target.isScanCheapest() || !this.scanCheapest(client, screen, target, current, title, now)) {
                              CollectorFeature.Candidate candidate = this.findCandidate(screen, target, target.getCount() - current);
                              if (candidate != null) {
                                 click(client, screen, candidate.slot(), SlotActionType.QUICK_MOVE);
                                 this.nextActionAt = now + this.clickDelay.getValue().longValue();
                              } else {
                                 int nextPage = findNamedSlot(screen, "следующая", "next page", "вперёд", "forward");
                                 if (nextPage >= 0 && this.pageChanges++ < (target.isScanCheapest() ? 4 : 1)) {
                                    click(client, screen, nextPage, SlotActionType.PICKUP);
                                    this.nextActionAt = now + 700L;
                                 } else {
                                    ChatUtil.info("Collector skipped " + target.getName() + ": no suitable lots");
                                    this.next(client, target);
                                 }
                              }
                           }
                        }
                     } else {
                        if (now - this.searchSentAt >= 1200L) {
                           player.networkHandler.sendChatCommand("ah search " + target.getName());
                           this.searchSentAt = now;
                           this.nextActionAt = now + 900L;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private CollectorFeature.Candidate findCandidate(HandledScreen<?> screen, CollectorItem target, int missing) {
      return this.findCandidates(screen, target, missing).stream().min(Comparator.comparingInt(CollectorFeature.Candidate::price)).orElse(null);
   }

   private void next(MinecraftClient client, CollectorItem item) {
      if (client.player != null && client.currentScreen instanceof HandledScreen) {
         client.player.closeHandledScreen();
      }

      this.index++;
      this.searchSentAt = 0L;
      this.pageChanges = 0;
      this.resetPageScan();
      this.nextActionAt = System.currentTimeMillis() + 300L;
   }

   private void stop(boolean completed) {
      boolean wasRunning = this.running;
      this.running = false;
      this.queue = List.of();
      this.index = 0;
      this.pageChanges = 0;
      this.resetPageScan();
      if (completed && wasRunning) {
         ChatUtil.success("Collector finished the selected loadout");
      }
   }

   private static int count(ClientPlayerEntity player, CollectorItem item) {
      int total = 0;

      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (item.matches(stack)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   private boolean scanCheapest(MinecraftClient client, HandledScreen<?> screen, CollectorItem target, int currentCount, String title, long now) {
      CollectorFeature.PageInfo page = pageInfo(title);
      if (page == null) {
         return false;
      } else {
         int missing = target.getCount() - currentCount;
         if (this.scannedPages.add(page.current())) {
            for (CollectorFeature.Candidate candidate : this.findCandidates(screen, target, missing)) {
               this.pageOffers.add(new CollectorFeature.PageOffer(page.current(), candidate.price()));
            }
         }

         int lastScanPage = Math.min(4, page.total());
         if (this.selectedPage < 0 && page.current() < lastScanPage) {
            int next = findNamedSlot(screen, "следующая страница", "next page", "вперёд", "forward");
            if (next >= 0) {
               click(client, screen, next, SlotActionType.QUICK_MOVE);
               this.nextActionAt = now + 650L;
               return true;
            }
         }

         if (this.selectedPage < 0) {
            CollectorFeature.PageOffer offer = this.pageOffers.stream().min(Comparator.comparingInt(CollectorFeature.PageOffer::price)).orElse(null);
            if (offer == null) {
               ChatUtil.info("Collector skipped " + target.getName() + ": no suitable lots");
               this.next(client, target);
               return true;
            }

            this.selectedPage = offer.page();
            this.selectedPrice = offer.price();
         }

         if (page.current() != this.selectedPage) {
            String[] markers = page.current() < this.selectedPage
               ? new String[]{"следующая страница", "next page", "вперёд", "forward"}
               : new String[]{"предыдущая страница", "previous page", "назад", "back"};
            int navigation = findNamedSlot(screen, markers);
            if (navigation >= 0) {
               click(client, screen, navigation, SlotActionType.QUICK_MOVE);
               this.nextActionAt = now + 650L;
               return true;
            } else {
               this.resetPageScan();
               return false;
            }
         } else {
            List<CollectorFeature.Candidate> live = this.findCandidates(screen, target, missing);
            CollectorFeature.Candidate chosen = live.stream()
               .filter(candidate -> candidate.price() == this.selectedPrice)
               .findFirst()
               .orElseGet(() -> live.stream().min(Comparator.comparingInt(CollectorFeature.Candidate::price)).orElse(null));
            if (chosen == null) {
               this.resetPageScan();
               if (client.player != null) {
                  client.player.closeHandledScreen();
               }

               this.nextActionAt = now + 450L;
               return true;
            } else {
               click(client, screen, chosen.slot(), SlotActionType.QUICK_MOVE);
               this.nextActionAt = now + this.clickDelay.getValue().longValue();
               return true;
            }
         }
      }
   }

   private List<CollectorFeature.Candidate> findCandidates(HandledScreen<?> screen, CollectorItem target, int missing) {
      int limit = containerSlotCount(screen);
      int tolerance = Math.max(0, Math.round((float)target.getCount() * 0.2F));
      int maximumLot = Math.max(1, missing + tolerance);
      List<CollectorFeature.Candidate> candidates = new ArrayList<>();

      for (int slot = 0; slot < limit; slot++) {
         ItemStack stack = ((Slot)screen.getScreenHandler().slots.get(slot)).getStack();
         if (target.matches(stack) && stack.getCount() <= maximumLot && acceptableQuality(stack)) {
            int price = AuctionUtils.getPrice(stack);
            if (price > 0) {
               candidates.add(new CollectorFeature.Candidate(slot, price));
            }
         }
      }

      return candidates;
   }

   private static boolean acceptableQuality(ItemStack stack) {
      if (stack.isOf(Items.TOTEM_OF_UNDYING) && stack.hasGlint()) {
         return false;
      } else {
         return stack.isOf(Items.ELYTRA) && stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() >= stack.getMaxDamage() / 2 : true;
      }
   }

   private static boolean hasEmptySlot(ClientPlayerEntity player) {
      for (int slot = 0; slot < 36; slot++) {
         if (player.getInventory().getStack(slot).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private static CollectorFeature.PageInfo pageInfo(String title) {
      Matcher matcher = PAGE.matcher(AuctionUtils.stripColors(title));
      if (!matcher.find()) {
         return null;
      } else {
         try {
            int current = Integer.parseInt(matcher.group(1));
            int total = Integer.parseInt(matcher.group(2));
            return current > 0 && total >= current ? new CollectorFeature.PageInfo(current, total) : null;
         } catch (NumberFormatException var4) {
            return null;
         }
      }
   }

   private void resetPageScan() {
      this.pageOffers.clear();
      this.scannedPages.clear();
      this.selectedPage = -1;
      this.selectedPrice = -1;
   }

   private static void click(MinecraftClient client, HandledScreen<?> screen, int slot, SlotActionType type) {
      if (slot >= 0 && slot < screen.getScreenHandler().slots.size()) {
         client.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot, 0, type, client.player);
      }
   }

   private static int findNamedSlot(HandledScreen<?> screen, String... markers) {
      int limit = containerSlotCount(screen);

      for (int slot = 0; slot < limit; slot++) {
         String text = ((Slot)screen.getScreenHandler().slots.get(slot)).getStack().getName().getString().toLowerCase(Locale.ROOT);

         for (String marker : markers) {
            if (text.contains(marker)) {
               return slot;
            }
         }
      }

      return -1;
   }

   private static int containerSlotCount(HandledScreen<?> screen) {
      List<Slot> slots = screen.getScreenHandler().slots;
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null) {
         return slots.size();
      } else {
         for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index).inventory == client.player.getInventory()) {
               return index;
            }
         }

         return slots.size();
      }
   }

   @Environment(EnvType.CLIENT)
   private static record Candidate(int slot, int price) {
   }

   @Environment(EnvType.CLIENT)
   private static record PageInfo(int current, int total) {
   }

   @Environment(EnvType.CLIENT)
   private static record PageOffer(int page, int price) {
   }
}
