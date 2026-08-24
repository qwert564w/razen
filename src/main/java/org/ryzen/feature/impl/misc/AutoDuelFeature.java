package org.ryzen.feature.impl.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class AutoDuelFeature extends Feature implements MinecraftContext {
   private static final String[] KITS = new String[]{"Shield", "Spikes", "Bow", "Totems", "Heal", "Balls", "Classic", "Cheaters", "Nether"};
   private static final String KIT_SCREEN_TITLE = "выбор набора";
   private static final String DUEL_COMMAND = "duel ";
   private static final String ROUND_START = "начало";
   private static final String ROUND_IN = "через";
   private static final String ROUND_SECONDS = "секунд!";
   private static final String IN_MATCH = "дуэли » во время поединка запрещено использовать команды";
   private static final Pattern NICKNAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
   private static final Pattern COLOUR_CODES = Pattern.compile("§.");
   private static final long CHALLENGE_INTERVAL = 1000L;
   private static final long MATCH_BACKOFF = 1100L;
   private static final long TARGET_COOLDOWN = 1000L;
   private static final float SLOT_DELAY_SCALE = 0.05F;
   public final MultiSelectSetting kits = this.register(new MultiSelectSetting("Kits", Set.of("Classic"), KITS));
   public final BooleanSetting money = this.register(new BooleanSetting("Bet Money", false));
   public final NumberSetting moneyAmount = this.register(new NumberSetting("Bet Amount", 50.0, 0.0, 500.0, 50.0, "").visibleWhen(this.money::getValue));
   public final NumberSetting slotDelay = this.register(new NumberSetting("Slot Delay", 20.0, 20.0, 400.0, 20.0, " ms"));
   private final Map<String, Long> recentTargets = new HashMap<>();
   private long challengeAt;
   private long slotClickAt;
   private long kitClickAt;
   private boolean inMatch;
   private int targetRotation;

   public AutoDuelFeature() {
      super("AutoDuel", "Automatically challenges players to duels", FeatureCategory.MISC, -1);
   }

   @Override
   protected void onEnable() {
      this.inMatch = false;
      this.targetRotation = 0;
      this.recentTargets.clear();
      long now = System.currentTimeMillis();
      this.challengeAt = now;
      this.slotClickAt = now;
      this.kitClickAt = now;
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && this.inGame()) {
         if (event.getPacket() instanceof GameMessageS2CPacket packet) {
            String text = strip(packet.content().getString());
            if (text.contains("дуэли » во время поединка запрещено использовать команды")) {
               this.inMatch = true;
               this.challengeAt = System.currentTimeMillis() + 1100L;
            } else {
               if (text.contains("начало") || text.contains("через") && text.contains("секунд!")) {
                  this.inMatch = true;
               }
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.inGame() && this.gameMode() != null) {
         if (this.screen() instanceof HandledScreen<?> container) {
            this.pickKit(container);
         } else if (!this.inMatch && this.screen() == null) {
            long now = System.currentTimeMillis();
            if (now >= this.challengeAt) {
               List<String> targets = this.collectTargets();
               if (!targets.isEmpty()) {
                  if (this.sendChallenge(this.rotateTargets(targets), now)) {
                     this.challengeAt = now + 1000L;
                  }
               }
            }
         }
      }
   }

   private List<String> collectTargets() {
      Set<String> names = new LinkedHashSet<>();
      if (mc.getNetworkHandler() != null) {
         for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile() != null
               && entry.getProfile().name() != null
               && (entry.getProfile().id() == null || !entry.getProfile().id().equals(this.player().getUuid()))) {
               String name = entry.getProfile().name();
               if (NICKNAME.matcher(name).matches()) {
                  names.add(name);
               }
            }
         }
      }

      for (PlayerEntity other : this.level().getPlayers()) {
         if (other != this.player()) {
            String name = other.getName().getString();
            if (NICKNAME.matcher(name).matches()) {
               names.add(name);
            }
         }
      }

      return new ArrayList<>(names);
   }

   private List<String> rotateTargets(List<String> targets) {
      int size = targets.size();
      int offset = Math.floorMod(this.targetRotation, size);
      List<String> rotated = new ArrayList<>(size);

      for (int index = 0; index < size; index++) {
         rotated.add(targets.get((offset + index) % size));
      }

      this.targetRotation++;
      return rotated;
   }

   private boolean sendChallenge(List<String> targets, long now) {
      String self = this.sessionName();

      for (String target : targets) {
         if (!target.equalsIgnoreCase(self)) {
            String key = target.toLowerCase(Locale.ROOT);
            Long last = this.recentTargets.get(key);
            if (last == null || now - last >= 1000L) {
               String command = "duel " + target;
               if (this.money.getValue()) {
                  command = command + " " + this.moneyAmount.getValue().intValue();
               }

               this.player().networkHandler.sendChatCommand(command);
               this.recentTargets.put(key, now);
               return true;
            }
         }
      }

      return false;
   }

   private void pickKit(HandledScreen<?> screen) {
      ScreenHandler menu = screen.getScreenHandler();
      if (menu.syncId != 0) {
         long now = System.currentTimeMillis();
         long delay = (long)(this.slotDelay.getValue().floatValue() * 0.05F * 1000.0F);
         String title = strip(screen.getTitle().getString());
         if (title.contains("выбор набора")) {
            if (now >= this.kitClickAt) {
               List<Integer> enabledSlots = new ArrayList<>();

               for (int index = 0; index < KITS.length; index++) {
                  if (this.kits.isSelected(KITS[index])) {
                     enabledSlots.add(index);
                  }
               }

               if (!enabledSlots.isEmpty()) {
                  Collections.shuffle(enabledSlots);
                  int slot = enabledSlots.getFirst();
                  if (slot < menu.slots.size()) {
                     this.clickSlot(menu, slot);
                     this.kitClickAt = now + delay;
                  }
               }
            }
         } else if (now >= this.slotClickAt) {
            for (Slot slot : menu.slots) {
               if (slot.getStack().isOf(Items.LIME_STAINED_GLASS_PANE)) {
                  this.clickSlot(menu, slot.id);
                  this.slotClickAt = now + delay;
                  return;
               }
            }
         }
      }
   }

   private void clickSlot(ScreenHandler menu, int slot) {
      this.gameMode().clickSlot(menu.syncId, slot, 0, SlotActionType.PICKUP, this.player());
   }

   private String sessionName() {
      return mc.getSession() != null && mc.getSession().getUsername() != null ? mc.getSession().getUsername() : this.player().getName().getString();
   }

   private static String strip(String text) {
      return text == null ? "" : COLOUR_CODES.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
   }
}
