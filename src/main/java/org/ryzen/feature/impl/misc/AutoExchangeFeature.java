package org.ryzen.feature.impl.misc;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class AutoExchangeFeature extends Feature implements PlayerContext {
   private static final Pattern BALANCE = Pattern.compile("Ваш баланс:\\s*(\\d+)\\s*\\|");
   private static final Pattern EXPECTED = Pattern.compile("Ожидается:\\s*(\\d+)\\s*\\|");
   private static final Pattern GIVING = Pattern.compile("Вы отдадите:\\s*(\\d+)\\s*\\|");
   private static final Pattern ADD_BUTTON = Pattern.compile("▶ Добавить (\\d+) \\|❘\\| \\(коинов\\)");
   private static final String BUY_LINE = "[ЛКМ] — Приобрести";
   private static final String TITLE_EXCHANGE = "Биржа";
   private static final String TITLE_PURCHASE = "Покупка";
   private static final String COMMAND = "exchange";
   public final NumberSetting coins = this.register(new NumberSetting("Coins", 100.0, 1.0, 300.0, 1.0, ""));
   private int target = -1;
   private int openedOfferSlot = -1;
   private Screen lastScreen;
   private int screenSettledAtTick;
   private int screenSettleDelay;

   public AutoExchangeFeature() {
      super("AutoExchange", "Buys coins through the server exchange menu", FeatureCategory.MISC, -1);
   }

   @Override
   protected void onEnable() {
      this.target = this.coins.getValue().intValue();
      this.openedOfferSlot = -1;
      this.lastScreen = null;
   }

   @Override
   protected void onDisable() {
      this.target = -1;
      this.openedOfferSlot = -1;
      this.lastScreen = null;
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 1 && event.getKey() == 256) {
         ChatUtil.info("AutoExchange: остановлено");
         this.setEnabled(false);
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && this.target > 0) {
         if (client.currentScreen == null) {
            if (player.age % 10 == 0 && player.networkHandler != null) {
               player.networkHandler.sendChatCommand("exchange");
            }

            this.openedOfferSlot = -1;
            this.lastScreen = null;
         } else if (client.currentScreen instanceof HandledScreen<?> screen) {
            ScreenHandler menu = player.currentScreenHandler;
            if (menu != null) {
               if (this.settled(client, player)) {
                  int balance = this.findNumber(player, menu, BALANCE);
                  if (balance != -1 && balance < this.target) {
                     ChatUtil.error("AutoExchange: на балансе меньше коинов, чем запрошено");
                     player.closeHandledScreen();
                     this.setEnabled(false);
                  } else {
                     String title = screen.getTitle().getString();
                     boolean exchange = title.contains("Биржа");
                     boolean purchase = title.contains("Покупка");
                     if (!exchange && !purchase) {
                        player.closeHandledScreen();
                        this.openedOfferSlot = -1;
                     } else {
                        if (exchange && player.age % 10 == 0) {
                           this.openedOfferSlot = -1;
                        }

                        if (this.openedOfferSlot != -1) {
                           if (purchase) {
                              this.fillPurchase(client, player, menu);
                           }
                        } else {
                           if (exchange && !purchase) {
                              this.openOffer(client, player, menu);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean settled(MinecraftClient client, ClientPlayerEntity player) {
      if (client.currentScreen != this.lastScreen) {
         this.lastScreen = client.currentScreen;
         this.screenSettledAtTick = player.age;
         this.screenSettleDelay = ThreadLocalRandom.current().nextInt(3, 5);
      }

      return player.age >= this.screenSettledAtTick + this.screenSettleDelay;
   }

   private void openOffer(MinecraftClient client, ClientPlayerEntity player, ScreenHandler menu) {
      for (Slot slot : menu.slots) {
         if (slot != null && slot.hasStack()) {
            int expected = matchNumber(tooltip(player, slot.getStack()), EXPECTED);
            if (expected >= this.target) {
               click(client, player, menu, slot.id, 1);
               this.openedOfferSlot = slot.id;
               return;
            }
         }
      }
   }

   private void fillPurchase(MinecraftClient client, ClientPlayerEntity player, ScreenHandler menu) {
      if (player.age % 10 == 0) {
         int current = -1;

         for (Slot slot : menu.slots) {
            if (slot != null && slot.hasStack()) {
               int giving = matchNumber(tooltip(player, slot.getStack()), GIVING);
               if (giving != -1) {
                  current = giving;
                  break;
               }
            }
         }

         if (current != -1) {
            if (current == this.target) {
               Slot buy = this.findBuySlot(player, menu);
               if (buy != null) {
                  click(client, player, menu, buy.id, 0);
                  ChatUtil.success("AutoExchange: покупка отправлена");
                  this.setEnabled(false);
               }
            } else {
               int missing = this.target - current;
               if (missing > 0) {
                  Slot addButton = this.findAddButton(player, menu, missing);
                  if (addButton != null) {
                     click(client, player, menu, addButton.id, 0);
                  }
               }
            }
         }
      }
   }

   private Slot findBuySlot(ClientPlayerEntity player, ScreenHandler menu) {
      for (Slot slot : menu.slots) {
         if (slot != null && slot.hasStack()) {
            List<Text> lines = tooltip(player, slot.getStack());
            boolean buyable = lines.stream().anyMatch(line -> line.getString().contains("[ЛКМ] — Приобрести"));
            if (buyable && matchNumber(lines, GIVING) == this.target) {
               return slot;
            }
         }
      }

      return null;
   }

   private Slot findAddButton(ClientPlayerEntity player, ScreenHandler menu, int missing) {
      Slot best = null;
      int bestAmount = 0;

      for (Slot slot : menu.slots) {
         if (slot != null && slot.hasStack()) {
            int amount = matchNumber(tooltip(player, slot.getStack()), ADD_BUTTON);
            if (amount > 0 && amount <= missing && amount > bestAmount) {
               bestAmount = amount;
               best = slot;
            }
         }
      }

      return best;
   }

   private int findNumber(ClientPlayerEntity player, ScreenHandler menu, Pattern pattern) {
      for (Slot slot : menu.slots) {
         if (slot != null && slot.hasStack()) {
            int value = matchNumber(tooltip(player, slot.getStack()), pattern);
            if (value != -1) {
               return value;
            }
         }
      }

      return -1;
   }

   private static int matchNumber(List<Text> lines, Pattern pattern) {
      for (Text line : lines) {
         Matcher matcher = pattern.matcher(line.getString());
         if (matcher.find()) {
            return Integer.parseInt(matcher.group(1).replace(",", ""));
         }
      }

      return -1;
   }

   private static List<Text> tooltip(ClientPlayerEntity player, ItemStack stack) {
      return stack.getTooltip(TooltipContext.create(player.getEntityWorld()), player, TooltipType.BASIC);
   }

   private static void click(MinecraftClient client, ClientPlayerEntity player, ScreenHandler menu, int slot, int button) {
      if (client.interactionManager != null) {
         client.interactionManager.clickSlot(menu.syncId, slot, button, SlotActionType.PICKUP, player);
      }
   }
}
