package org.ryzen.feature.impl.pve;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.DisconnectEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.feature.setting.TextSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.pve.PveStateMachine;
import org.ryzen.pve.economy.AuctionPriceScanner;
import org.ryzen.pve.economy.CommandCooldown;
import org.ryzen.pve.economy.CraftingMenuController;
import org.ryzen.pve.economy.EconomyAutomationPolicy;
import org.ryzen.pve.economy.EconomyChat;
import org.ryzen.pve.economy.EconomyCommands;
import org.ryzen.pve.economy.EconomyInventory;
import org.ryzen.pve.economy.EconomyItemText;
import org.ryzen.pve.economy.EconomyMenus;
import org.ryzen.pve.economy.EconomyNavigator;
import org.ryzen.pve.economy.EconomyTextParser;
import org.ryzen.pve.economy.NearbyEconomyBlocks;
import org.ryzen.pve.navigation.BaritoneNavigator;
import org.ryzen.pve.server.ServerAdapter;
import org.ryzen.pve.server.ServerAdapters;
import org.ryzen.pve.server.ServerProfile;
import org.ryzen.utils.inventory.ContainerLootService;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class AutoTradeFeature extends PveFeature {
   private static final long MOVE_TIMEOUT_TICKS = 240L;
   private static final long OPEN_TIMEOUT_TICKS = 100L;
   private static final long AUCTION_RETRY_TICKS = 1200L;
   public final BooleanSetting buyEmeralds = this.register(new BooleanSetting("Buy Emeralds", true));
   public final NumberSetting emeraldReserve = this.register(new NumberSetting("Emerald Reserve", 192.0, 64.0, 640.0, 64.0, ""));
   public final NumberSetting scanRadius = this.register(new NumberSetting("Villager Radius", 16.0, 8.0, 28.0, 1.0, ""));
   public final BooleanSetting depositGold = this.register(new BooleanSetting("Store Gold", true));
   public final NumberSetting chestScanRadius = this.register(new NumberSetting("Chest Radius", 16.0, 4.0, 32.0, 1.0, ""));
   public final TextSetting chestKeyword = this.register(new TextSetting("Chest Sign", "золото", 32).visibleWhen(this.depositGold::getValue));
   public final BooleanSetting autoSellBlocks = this.register(new BooleanSetting("Sell Blocks", true));
   public final BooleanSetting craftBlocks = this.register(new BooleanSetting("Craft Blocks", true));
   public final TextSetting auctionQuery = this.register(new TextSetting("Auction Search", "золотой блок", 40).visibleWhen(this.autoSellBlocks::getValue));
   public final NumberSetting restockCheck = this.register(new NumberSetting("Restock Check", 180.0, 30.0, 600.0, 10.0, " s"));
   private final PveStateMachine<AutoTradeFeature.State> machine = new PveStateMachine<>(AutoTradeFeature.State.WAIT);
   private final EconomyNavigator navigator = new EconomyNavigator(BaritoneNavigator.INSTANCE);
   private final CraftingMenuController crafting = new CraftingMenuController();
   private final CommandCooldown actionCooldown = new CommandCooldown();
   private final CommandCooldown commandCooldown = new CommandCooldown();
   private final Map<UUID, Long> exhaustedVillagers = new HashMap<>();
   private long lastTick;
   private long nextWorkTick;
   private long shopRetryTick;
   private long auctionRetryTick;
   private boolean resourcesClaimed;
   private boolean moneyDry;
   private VillagerEntity targetVillager;
   private BlockPos targetBlock;
   private int ownedContainerId = -1;
   private int selectedOffer = -1;
   private long offerSelectedTick;
   private int tradeActions;
   private int shopEmeraldBefore;
   private int depositActions;
   private int saleCount;
   private long salePrice;
   private int originalSelectedSlot = -1;
   private int saleSwapMenuSlot = -1;
   private int saleHotbarSlot = -1;
   private boolean saleConfirmed;
   private boolean saleRejected;

   public AutoTradeFeature() {
      super("AutoTrade", "Buys emeralds, trades with clerics, and processes the resulting gold", -1, AutomationPriority.FEATURE);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && client.interactionManager != null) {
         long tick = client.world.getTime();
         this.lastTick = tick;
         if (ServerAdapters.current().profile() == ServerProfile.FUNTIME) {
            this.exhaustedVillagers.entrySet().removeIf(entry -> tick >= entry.getValue());
            switch ((AutoTradeFeature.State)this.machine.state()) {
               case WAIT:
                  if (tick >= this.nextWorkTick) {
                     this.machine.transition(AutoTradeFeature.State.SELECT, tick);
                  }
                  break;
               case SELECT:
                  this.selectWork(client, player, tick);
                  break;
               case OPEN_SHOP:
                  this.openShop(client, player, tick);
                  break;
               case WAIT_SHOP_MENU:
                  this.waitShopMenu(client, player, tick);
                  break;
               case BUY_SHOP:
                  this.buyFromShop(client, player, tick);
                  break;
               case FIND_VILLAGER:
                  this.findVillager(client, player, tick);
                  break;
               case MOVE_VILLAGER:
                  this.moveVillager(player, tick);
                  break;
               case OPEN_TRADE:
                  this.openTrade(client, player, tick);
                  break;
               case TRADE:
                  this.trade(client, player, tick);
                  break;
               case FIND_TABLE:
                  this.findTable(client, player, tick);
                  break;
               case MOVE_TABLE:
                  this.moveBlock(player, AutoTradeFeature.State.OPEN_TABLE, tick);
                  break;
               case OPEN_TABLE:
                  this.openTable(client, player, tick);
                  break;
               case CRAFT_BLOCKS:
                  this.craftBlocks(client, tick);
                  break;
               case FIND_DEPOSIT:
                  this.findDeposit(client, player, tick);
                  break;
               case MOVE_DEPOSIT:
                  this.moveBlock(player, AutoTradeFeature.State.OPEN_DEPOSIT, tick);
                  break;
               case OPEN_DEPOSIT:
                  this.openDeposit(client, player, tick);
                  break;
               case DEPOSIT:
                  this.deposit(client, player, tick);
                  break;
               case OPEN_AUCTION_SEARCH:
                  this.openAuctionSearch(client, player, tick);
                  break;
               case WAIT_AUCTION_SEARCH:
                  this.waitAuctionSearch(client, tick);
                  break;
               case LIST_AUCTION:
                  this.listAuction(client, player, tick);
                  break;
               case WAIT_SALE_CONFIRMATION:
                  this.waitSaleConfirmation(client, tick);
            }
         } else {
            if (this.resourcesClaimed || !this.machine.is(AutoTradeFeature.State.WAIT) || this.ownedContainerId >= 0) {
               this.finishCycle(client, tick, 20L);
            }
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         String text = EconomyChat.incomingText(event.getPacket());
         if (text != null) {
            String normalized = EconomyTextParser.normalize(text);
            MinecraftClient.getInstance().execute(() -> this.handleChat(normalized));
         }
      }
   }

   @EventTarget
   public void onDisconnect(DisconnectEvent event) {
      this.resetRuntime(false);
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntime(false);
   }

   @Override
   protected void onPveDisable() {
      this.resetRuntime(true);
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.resetRuntime(true);
   }

   private void selectWork(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (client.currentScreen != null || player.currentScreenHandler != player.playerScreenHandler) {
         this.finishCycle(client, tick, 20L);
      } else if (this.ensureCycleResources(tick)) {
         int emeralds = EconomyInventory.count(player, Items.EMERALD);
         int ingots = EconomyInventory.count(player, Items.GOLD_INGOT);
         int blocks = EconomyInventory.count(player, Items.GOLD_BLOCK);
         switch (EconomyAutomationPolicy.tradeAction(
            emeralds,
            ingots,
            blocks,
            Math.toIntExact(Math.round(this.emeraldReserve.getValue())),
            this.buyEmeralds.getValue(),
            this.depositGold.getValue(),
            this.craftBlocks.getValue(),
            this.autoSellBlocks.getValue(),
            this.moneyDry,
            tick >= this.shopRetryTick,
            tick >= this.auctionRetryTick,
            largestStackCount(player, Items.GOLD_BLOCK) >= 64
         )) {
            case SELL_BLOCKS:
               this.machine.transition(AutoTradeFeature.State.OPEN_AUCTION_SEARCH, tick);
               break;
            case CRAFT_BLOCKS:
               this.machine.transition(AutoTradeFeature.State.FIND_TABLE, tick);
               break;
            case DEPOSIT_GOLD:
               this.machine.transition(AutoTradeFeature.State.FIND_DEPOSIT, tick);
               break;
            case TRADE:
               this.machine.transition(AutoTradeFeature.State.FIND_VILLAGER, tick);
               break;
            case BUY_EMERALDS:
               this.machine.transition(AutoTradeFeature.State.OPEN_SHOP, tick);
               break;
            case WAIT:
               this.finishCycle(client, tick, 40L);
         }
      }
   }

   private void openShop(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (this.commandCooldown.tryAcquire(tick, 40L)) {
         ServerAdapters.current().sendCommand(player, EconomyCommands.shop());
         this.machine.transition(AutoTradeFeature.State.WAIT_SHOP_MENU, tick);
      }
   }

   private void waitShopMenu(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, 100L);
      } else if (EconomyMenus.titleContains(client, "магазин", "shop") && player.currentScreenHandler != player.playerScreenHandler) {
         ScreenHandler menu = player.currentScreenHandler;
         int emerald = EconomyMenus.findContainerSlot(menu, stack -> stack.isOf(Items.EMERALD) || EconomyItemText.containsAny(stack, "изумруд", "emerald"));
         if (emerald >= 0) {
            this.ownedContainerId = menu.syncId;
            this.shopEmeraldBefore = EconomyInventory.count(player, Items.EMERALD);
            if (EconomyMenus.click(client, menu, emerald, 0, SlotActionType.PICKUP)) {
               this.actionCooldown.tryAcquire(tick, 8L);
               this.machine.transition(AutoTradeFeature.State.BUY_SHOP, tick);
            }
         }
      }
   }

   private void buyFromShop(MinecraftClient client, ClientPlayerEntity player, long tick) {
      int emeralds = EconomyInventory.count(player, Items.EMERALD);
      if ((long)emeralds >= Math.round(this.emeraldReserve.getValue())) {
         this.moneyDry = false;
         this.closeOwned(client);
         this.machine.transition(AutoTradeFeature.State.SELECT, tick);
      } else {
         if (emeralds > this.shopEmeraldBefore) {
            this.shopEmeraldBefore = emeralds;
            this.actionCooldown.defer(tick, 8L);
         }

         if (this.machine.ticksInState(tick) <= 200L && player.currentScreenHandler != player.playerScreenHandler) {
            if (this.actionCooldown.ready(tick) && EconomyMenus.titleContains(client, "магазин", "shop")) {
               ScreenHandler menu = player.currentScreenHandler;
               int buy = EconomyMenus.findContainerSlot(menu, stack -> EconomyItemText.containsAny(stack, "купить", "buy", "приобрести"));
               if (buy >= 0) {
                  this.ownedContainerId = menu.syncId;
                  if (EconomyMenus.click(client, menu, buy, 0, SlotActionType.PICKUP)) {
                     this.actionCooldown.tryAcquire(tick, 10L);
                  }
               }
            }
         } else {
            this.finishCycle(client, tick, 100L);
         }
      }
   }

   private void findVillager(MinecraftClient client, ClientPlayerEntity player, long tick) {
      double radius = this.scanRadius.getValue();
      this.targetVillager = client.world
         .getEntitiesByClass(
            VillagerEntity.class,
            player.getBoundingBox().expand(radius),
            villager -> villager.isAlive()
                  && villager.getVillagerData().profession().matchesKey(VillagerProfession.CLERIC)
                  && !this.exhaustedVillagers.containsKey(villager.getUuid())
         )
         .stream()
         .min(Comparator.comparingDouble(player::squaredDistanceTo))
         .orElse(null);
      if (this.targetVillager != null) {
         this.machine.transition(AutoTradeFeature.State.MOVE_VILLAGER, tick);
      } else {
         if (!this.depositGold.getValue() || EconomyInventory.count(player, Items.GOLD_INGOT) <= 0 && EconomyInventory.count(player, Items.GOLD_BLOCK) <= 0) {
            this.finishCycle(client, tick, 100L);
         } else {
            this.machine.transition(AutoTradeFeature.State.FIND_DEPOSIT, tick);
         }
      }
   }

   private void moveVillager(ClientPlayerEntity player, long tick) {
      if (this.targetVillager != null && this.targetVillager.isAlive()) {
         if (player.squaredDistanceTo(this.targetVillager) <= 10.25) {
            this.navigator.cancel();
            this.machine.transition(AutoTradeFeature.State.OPEN_TRADE, tick);
         } else {
            this.navigator.moveTo(player, this.targetVillager.getBlockPos(), 2);
            if (this.machine.ticksInState(tick) > 240L) {
               this.markVillagerExhausted(tick);
               this.machine.transition(AutoTradeFeature.State.FIND_VILLAGER, tick);
            }
         }
      } else {
         this.machine.transition(AutoTradeFeature.State.FIND_VILLAGER, tick);
      }
   }

   private void openTrade(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (player.currentScreenHandler instanceof MerchantScreenHandler menu) {
         this.ownedContainerId = menu.syncId;
         this.selectedOffer = -1;
         this.tradeActions = 0;
         this.machine.transition(AutoTradeFeature.State.TRADE, tick);
      } else if (this.targetVillager == null || !this.targetVillager.isAlive() || this.machine.ticksInState(tick) > 100L) {
         this.markVillagerExhausted(tick);
         this.machine.transition(AutoTradeFeature.State.FIND_VILLAGER, tick);
      } else if (this.actionCooldown.tryAcquire(tick, 10L)) {
         client.interactionManager.interactEntity(player, this.targetVillager, Hand.MAIN_HAND);
         player.swingHand(Hand.MAIN_HAND);
      }
   }

   private void trade(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (!(player.currentScreenHandler instanceof MerchantScreenHandler menu) || menu.syncId != this.ownedContainerId) {
         this.finishCycle(client, tick, 60L);
         return;
      }

      if (this.tradeActions >= 128) {
         this.closeOwned(client);
         this.machine.transition(AutoTradeFeature.State.SELECT, tick);
      } else if (this.selectedOffer >= 0) {
         if (tick - this.offerSelectedTick >= 3L) {
            ItemStack result = menu.getSlot(2).getStack();
            if (isGold(result)) {
               if (EconomyMenus.quickMove(client, menu, 2)) {
                  this.tradeActions++;
                  this.selectedOffer = -1;
                  this.actionCooldown.defer(tick, 3L);
               }
            } else if (tick - this.offerSelectedTick > 40L) {
               this.markVillagerExhausted(tick);
               this.closeOwned(client);
               this.machine.transition(AutoTradeFeature.State.SELECT, tick);
            }
         }
      } else if (this.actionCooldown.ready(tick)) {
         int offer = bestOffer(menu, EconomyInventory.count(player, Items.EMERALD));
         if (offer < 0) {
            this.markVillagerExhausted(tick);
            this.closeOwned(client);
            this.machine.transition(AutoTradeFeature.State.SELECT, tick);
         } else {
            menu.setRecipeIndex(offer);
            menu.switchTo(offer);
            player.networkHandler.sendPacket(new SelectMerchantTradeC2SPacket(offer));
            this.selectedOffer = offer;
            this.offerSelectedTick = tick;
         }
      }
   }

   private static int bestOffer(MerchantScreenHandler menu, int emeralds) {
      int best = -1;
      double bestValue = 0.0;

      for (int index = 0; index < menu.getRecipes().size(); index++) {
         TradeOffer offer = (TradeOffer)menu.getRecipes().get(index);
         int cost = emeraldCost(offer);
         int gold = goldValue(offer.getSellItem());
         if (!offer.isDisabled() && cost > 0 && cost <= emeralds && gold > 0) {
            double value = (double)gold / (double)cost;
            if (value > bestValue) {
               bestValue = value;
               best = index;
            }
         }
      }

      return best;
   }

   private void findTable(MinecraftClient client, ClientPlayerEntity player, long tick) {
      this.targetBlock = NearbyEconomyBlocks.nearestCraftingTable(client.world, player, Math.round(this.chestScanRadius.getValue().floatValue()));
      if (this.targetBlock == null) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.machine.transition(AutoTradeFeature.State.MOVE_TABLE, tick);
      }
   }

   private void findDeposit(MinecraftClient client, ClientPlayerEntity player, long tick) {
      this.targetBlock = NearbyEconomyBlocks.nearestSignedChest(
         client.world, player, Math.round(this.chestScanRadius.getValue().floatValue()), this.chestKeyword.getValue()
      );
      if (this.targetBlock == null) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.machine.transition(AutoTradeFeature.State.MOVE_DEPOSIT, tick);
      }
   }

   private void moveBlock(ClientPlayerEntity player, AutoTradeFeature.State next, long tick) {
      if (this.targetBlock == null) {
         this.finishCycle(MinecraftClient.getInstance(), tick, 100L);
      } else if (EconomyNavigator.arrived(player, this.targetBlock, 3.7)) {
         this.navigator.cancel();
         this.machine.transition(next, tick);
      } else {
         this.navigator.moveTo(player, this.targetBlock, 3);
         if (this.machine.ticksInState(tick) > 240L) {
            this.finishCycle(MinecraftClient.getInstance(), tick, 100L);
         }
      }
   }

   private void openTable(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (player.currentScreenHandler instanceof CraftingScreenHandler menu) {
         this.ownedContainerId = menu.syncId;
         this.crafting.reset();
         this.machine.transition(AutoTradeFeature.State.CRAFT_BLOCKS, tick);
      } else if (this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.interactBlock(client, player, tick);
      }
   }

   private void openDeposit(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (player.currentScreenHandler instanceof GenericContainerScreenHandler menu) {
         this.ownedContainerId = menu.syncId;
         this.depositActions = 0;
         this.machine.transition(AutoTradeFeature.State.DEPOSIT, tick);
      } else if (this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.interactBlock(client, player, tick);
      }
   }

   private void interactBlock(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (this.targetBlock != null && this.actionCooldown.tryAcquire(tick, 10L)) {
         client.interactionManager
            .interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(this.targetBlock), Direction.UP, this.targetBlock, false));
         player.swingHand(Hand.MAIN_HAND);
      }
   }

   private void craftBlocks(MinecraftClient client, long tick) {
      if (!(client.player.currentScreenHandler instanceof CraftingScreenHandler menu) || menu.syncId != this.ownedContainerId) {
         this.finishCycle(client, tick, 60L);
         return;
      }

      if (this.actionCooldown.ready(tick)) {
         CraftingMenuController.Result result = this.crafting.tick(client, menu, CraftingMenuController.Recipe.GOLD_BLOCK);
         this.actionCooldown.tryAcquire(tick, 2L);
         if (result == CraftingMenuController.Result.CRAFTED) {
            this.closeOwned(client);
            this.machine.transition(AutoTradeFeature.State.SELECT, tick);
         } else if (result == CraftingMenuController.Result.FAILED) {
            this.crafting.cleanup(client, menu);
            this.finishCycle(client, tick, 100L);
         }
      }
   }

   private void deposit(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (!(player.currentScreenHandler instanceof GenericContainerScreenHandler menu) || menu.syncId != this.ownedContainerId) {
         this.finishCycle(client, tick, 60L);
         return;
      }

      if (this.actionCooldown.ready(tick)) {
         int firstPlayerSlot = ContainerLootService.containerSlotCount(menu);
         int goldSlot = -1;

         for (int slotId = firstPlayerSlot; slotId < menu.slots.size(); slotId++) {
            if (menu.isValid(slotId) && isGold(menu.getSlot(slotId).getStack())) {
               goldSlot = slotId;
               break;
            }
         }

         if (goldSlot >= 0 && this.depositActions++ < 24) {
            if (EconomyMenus.quickMove(client, menu, goldSlot)) {
               this.actionCooldown.tryAcquire(tick, 3L);
            }
         } else {
            this.moneyDry = false;
            this.closeOwned(client);
            this.finishCycle(client, tick, 100L);
         }
      }
   }

   private void openAuctionSearch(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (this.commandCooldown.ready(tick)) {
         ServerAdapter adapter = ServerAdapters.current();
         adapter.auctionCommand().flatMap(root -> EconomyCommands.auctionSearch(root, this.auctionQuery.getValue())).ifPresentOrElse(command -> {
            this.saleCount = largestStackCount(player, Items.GOLD_BLOCK);
            if (this.saleCount <= 0) {
               this.finishCycle(client, tick, 40L);
            } else {
               adapter.sendCommand(player, command);
               this.commandCooldown.tryAcquire(tick, 40L);
               this.machine.transition(AutoTradeFeature.State.WAIT_AUCTION_SEARCH, tick);
            }
         }, () -> this.finishCycle(client, tick, 100L));
      }
   }

   private void waitAuctionSearch(MinecraftClient client, long tick) {
      if (this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, 100L);
      } else if (EconomyMenus.titleContains(client, "аукцион", "auction")) {
         ScreenHandler menu = client.player.currentScreenHandler;
         this.ownedContainerId = menu.syncId;
         OptionalLong price = AuctionPriceScanner.competitivePrice(menu, Items.GOLD_BLOCK, this.auctionQuery.getValue(), this.saleCount);
         if (!price.isEmpty()) {
            this.salePrice = price.getAsLong();
            this.closeOwned(client);
            this.machine.transition(AutoTradeFeature.State.LIST_AUCTION, tick);
         }
      }
   }

   private void listAuction(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (client.currentScreen == null && this.commandCooldown.ready(tick) && this.prepareSaleStack(player)) {
         ServerAdapter adapter = ServerAdapters.current();
         adapter.auctionCommand().flatMap(root -> EconomyCommands.auctionSell(root, this.salePrice)).ifPresentOrElse(command -> {
            adapter.sendCommand(player, command);
            this.commandCooldown.tryAcquire(tick, 100L);
            this.saleConfirmed = false;
            this.saleRejected = false;
            this.machine.transition(AutoTradeFeature.State.WAIT_SALE_CONFIRMATION, tick);
         }, () -> this.finishCycle(client, tick, 100L));
      } else {
         if (this.machine.ticksInState(tick) > 100L) {
            this.finishCycle(client, tick, 100L);
         }
      }
   }

   private void waitSaleConfirmation(MinecraftClient client, long tick) {
      if (this.saleConfirmed || this.saleRejected || this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, this.saleRejected ? 1200L : 40L);
      }
   }

   private boolean prepareSaleStack(ClientPlayerEntity player) {
      if (this.saleCount < 64) {
         return false;
      } else {
         ItemStack mainHand = player.getMainHandStack();
         if (mainHand.isOf(Items.GOLD_BLOCK) && mainHand.getCount() == this.saleCount) {
            return true;
         } else {
            int menuSlot = InventoryUtil.findPlayerMenuSlot(player, stack -> stack.isOf(Items.GOLD_BLOCK) && stack.getCount() == this.saleCount);
            if (menuSlot >= 0 && player.currentScreenHandler == player.playerScreenHandler) {
               if (this.originalSelectedSlot < 0) {
                  this.originalSelectedSlot = player.getInventory().getSelectedSlot();
               }

               if (menuSlot >= 36 && menuSlot <= 44) {
                  this.saleHotbarSlot = menuSlot - 36;
                  return EconomyInventory.selectHotbar(player, this.saleHotbarSlot);
               } else {
                  this.saleHotbarSlot = player.getInventory().getSelectedSlot();
                  this.saleSwapMenuSlot = menuSlot;
                  return InventoryUtil.swapWithHotbar(menuSlot, this.saleHotbarSlot);
               }
            } else {
               return false;
            }
         }
      }
   }

   private void restoreSaleStack(ClientPlayerEntity player) {
      if (player != null && player.currentScreenHandler == player.playerScreenHandler) {
         if (this.saleSwapMenuSlot >= 0 && this.saleHotbarSlot >= 0) {
            InventoryUtil.swapWithHotbar(this.saleSwapMenuSlot, this.saleHotbarSlot);
         }

         if (this.originalSelectedSlot >= 0) {
            EconomyInventory.selectHotbar(player, this.originalSelectedSlot);
         }
      }

      this.originalSelectedSlot = -1;
      this.saleSwapMenuSlot = -1;
      this.saleHotbarSlot = -1;
   }

   private void handleChat(String text) {
      if (this.isEnabled()) {
         if (EconomyTextParser.containsAny(text, "недостаточно денег", "не хватает денег", "недостаточно средств", "insufficient funds", "not enough money")) {
            long requested = EconomyTextParser.largestAmount(text).orElse(100000L);
            SynchronizationFeature.requestMoney((int)Math.min(2147483647L, requested));
            this.moneyDry = true;
            this.shopRetryTick = this.lastTick + 200L;
            this.closeOwned(MinecraftClient.getInstance());
            this.machine.transition(AutoTradeFeature.State.SELECT, this.lastTick);
         } else {
            if (EconomyTextParser.containsAny(text, "не удалось выставить", "хранилищ", "слот", "ah rent", "auction slots are full")) {
               this.saleRejected = true;
               this.auctionRetryTick = this.lastTick + 1200L;
            } else if (EconomyTextParser.containsAny(text, "выставлен на продажу", "listed for sale")) {
               this.saleConfirmed = true;
               this.auctionRetryTick = 0L;
            }
         }
      }
   }

   private void markVillagerExhausted(long tick) {
      if (this.targetVillager != null) {
         long delay = Math.round(this.restockCheck.getValue() * 20.0);
         this.exhaustedVillagers.put(this.targetVillager.getUuid(), tick + Math.max(1L, delay));
      }

      this.targetVillager = null;
   }

   private boolean ensureCycleResources(long tick) {
      if (this.resourcesClaimed) {
         return true;
      } else if (!this.claim(
         AutomationResource.MOVEMENT,
         new AutomationResource[]{AutomationResource.NAVIGATION, AutomationResource.INVENTORY, AutomationResource.SCREEN, AutomationResource.CHAT}
      )) {
         this.nextWorkTick = tick + 5L;
         return false;
      } else {
         this.resourcesClaimed = true;
         return true;
      }
   }

   private void closeOwned(MinecraftClient client) {
      if (client.player == null
         || !(client.player.currentScreenHandler instanceof CraftingScreenHandler menu)
         || menu.syncId != this.ownedContainerId
         || this.crafting.cleanup(client, menu)) {
         EconomyMenus.closeOwned(client, this.ownedContainerId);
         this.ownedContainerId = -1;
      }
   }

   private void finishCycle(MinecraftClient client, long tick, long delayTicks) {
      this.closeOwned(client);
      this.navigator.close();
      this.restoreSaleStack(client.player);
      this.targetVillager = null;
      this.targetBlock = null;
      this.selectedOffer = -1;
      this.tradeActions = 0;
      this.depositActions = 0;
      this.saleCount = 0;
      this.salePrice = 0L;
      this.saleConfirmed = false;
      this.saleRejected = false;
      this.crafting.reset();
      this.machine.transition(AutoTradeFeature.State.WAIT, tick);
      this.nextWorkTick = tick + Math.max(1L, delayTicks);
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }
   }

   private void resetRuntime(boolean closeScreen) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (closeScreen) {
         this.closeOwned(client);
         this.restoreSaleStack(client.player);
      }

      this.navigator.close();
      this.machine.reset(0L);
      this.actionCooldown.reset();
      this.commandCooldown.reset();
      this.crafting.reset();
      this.exhaustedVillagers.clear();
      this.nextWorkTick = 0L;
      this.shopRetryTick = 0L;
      this.auctionRetryTick = 0L;
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }

      this.moneyDry = false;
      this.targetVillager = null;
      this.targetBlock = null;
      this.ownedContainerId = -1;
      this.selectedOffer = -1;
      this.tradeActions = 0;
      this.depositActions = 0;
      this.saleCount = 0;
      this.salePrice = 0L;
      this.originalSelectedSlot = -1;
      this.saleSwapMenuSlot = -1;
      this.saleHotbarSlot = -1;
      this.saleConfirmed = false;
      this.saleRejected = false;
      this.lastTick = 0L;
   }

   private static int emeraldCost(TradeOffer offer) {
      int cost = 0;
      if (offer.getDisplayedFirstBuyItem().isOf(Items.EMERALD)) {
         cost += offer.getDisplayedFirstBuyItem().getCount();
      }

      if (offer.getDisplayedSecondBuyItem().isOf(Items.EMERALD)) {
         cost += offer.getDisplayedSecondBuyItem().getCount();
      }

      return cost;
   }

   private static int goldValue(ItemStack stack) {
      if (stack.isOf(Items.GOLD_BLOCK)) {
         return stack.getCount() * 9;
      } else if (stack.isOf(Items.GOLD_INGOT)) {
         return stack.getCount();
      } else {
         return stack.isOf(Items.GOLD_NUGGET) ? Math.max(1, stack.getCount() / 9) : 0;
      }
   }

   private static boolean isGold(ItemStack stack) {
      return stack.isOf(Items.GOLD_BLOCK) || stack.isOf(Items.GOLD_INGOT) || stack.isOf(Items.GOLD_NUGGET);
   }

   private static int largestStackCount(ClientPlayerEntity player, Item item) {
      int largest = 0;

      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (stack.isOf(item)) {
            largest = Math.max(largest, stack.getCount());
         }
      }

      return largest;
   }

   @Environment(EnvType.CLIENT)
   static enum State {
      WAIT,
      SELECT,
      OPEN_SHOP,
      WAIT_SHOP_MENU,
      BUY_SHOP,
      FIND_VILLAGER,
      MOVE_VILLAGER,
      OPEN_TRADE,
      TRADE,
      FIND_TABLE,
      MOVE_TABLE,
      OPEN_TABLE,
      CRAFT_BLOCKS,
      FIND_DEPOSIT,
      MOVE_DEPOSIT,
      OPEN_DEPOSIT,
      DEPOSIT,
      OPEN_AUCTION_SEARCH,
      WAIT_AUCTION_SEARCH,
      LIST_AUCTION,
      WAIT_SALE_CONFIRMATION;
   }
}
