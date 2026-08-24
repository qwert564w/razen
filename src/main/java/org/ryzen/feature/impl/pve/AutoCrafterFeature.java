package org.ryzen.feature.impl.pve;

import java.util.OptionalLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.DisconnectEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
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
import org.ryzen.pve.economy.EconomyMenus;
import org.ryzen.pve.economy.EconomyNavigator;
import org.ryzen.pve.economy.EconomyTextParser;
import org.ryzen.pve.economy.NearbyEconomyBlocks;
import org.ryzen.pve.navigation.BaritoneNavigator;
import org.ryzen.pve.server.ServerAdapter;
import org.ryzen.pve.server.ServerAdapters;
import org.ryzen.pve.server.ServerProfile;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class AutoCrafterFeature extends PveFeature {
   public static final String RECIPE_ENCHANTED_GOLDEN_APPLE = "Enchanted Golden Apple";
   private static final int SEARCH_RADIUS = 16;
   private static final long MOVE_TIMEOUT_TICKS = 240L;
   private static final long OPEN_TIMEOUT_TICKS = 100L;
   private static final long AUCTION_RETRY_TICKS = 1200L;
   public final ModeSetting craft = this.register(new ModeSetting("Craft", "Enchanted Golden Apple", "Enchanted Golden Apple"));
   public final BooleanSetting autoSell = this.register(new BooleanSetting("Auto Sell", true));
   public final BooleanSetting takeCrafterChest = this.register(
      new BooleanSetting("Take from Crafter Chest", false).visibleWhen(() -> !SynchronizationFeature.isActive())
   );
   private final PveStateMachine<AutoCrafterFeature.State> machine = new PveStateMachine<>(AutoCrafterFeature.State.WAIT);
   private final EconomyNavigator navigator = new EconomyNavigator(BaritoneNavigator.INSTANCE);
   private final CraftingMenuController crafting = new CraftingMenuController();
   private final CommandCooldown actionCooldown = new CommandCooldown();
   private final CommandCooldown commandCooldown = new CommandCooldown();
   private long lastTick;
   private long nextWorkTick;
   private long auctionRetryTick;
   private BlockPos targetBlock;
   private AutoCrafterFeature.ChestKind chestKind;
   private CraftingMenuController.Recipe pendingRecipe;
   private int ownedContainerId = -1;
   private int lootActions;
   private boolean resourcesClaimed;
   private int saleCount;
   private long salePrice;
   private int originalSelectedSlot = -1;
   private int saleSwapMenuSlot = -1;
   private int saleHotbarSlot = -1;
   private boolean saleConfirmed;
   private boolean saleRejected;

   public AutoCrafterFeature() {
      super("AutoCrafter", "Crafts enchanted golden apples from signed resource chests", -1, AutomationPriority.FEATURE);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && client.interactionManager != null) {
         long tick = client.world.getTime();
         this.lastTick = tick;
         if (ServerAdapters.current().profile() == ServerProfile.FUNTIME) {
            switch ((AutoCrafterFeature.State)this.machine.state()) {
               case WAIT:
                  if (tick >= this.nextWorkTick) {
                     this.machine.transition(AutoCrafterFeature.State.SELECT, tick);
                  }
                  break;
               case SELECT:
                  this.selectWork(client, player, tick);
                  break;
               case FIND_CHEST:
                  this.findChest(client, player, tick);
                  break;
               case MOVE_CHEST:
                  this.moveToTarget(player, AutoCrafterFeature.State.OPEN_CHEST, tick);
                  break;
               case OPEN_CHEST:
                  this.openChest(client, player, tick);
                  break;
               case LOOT_CHEST:
                  this.lootChest(client, player, tick);
                  break;
               case FIND_TABLE:
                  this.findTable(client, player, tick);
                  break;
               case MOVE_TABLE:
                  this.moveToTarget(player, AutoCrafterFeature.State.OPEN_TABLE, tick);
                  break;
               case OPEN_TABLE:
                  this.openTable(client, player, tick);
                  break;
               case CRAFT:
                  this.craft(client, tick);
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
            if (this.resourcesClaimed || !this.machine.is(AutoCrafterFeature.State.WAIT) || this.ownedContainerId >= 0) {
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
            MinecraftClient.getInstance().execute(() -> {
               if (this.isEnabled()) {
                  if (EconomyTextParser.containsAny(normalized, "не удалось выставить", "хранилищ", "слот", "ah rent", "auction slots are full")) {
                     this.saleRejected = true;
                     this.auctionRetryTick = this.lastTick + 1200L;
                  } else if (EconomyTextParser.containsAny(normalized, "выставлен на продажу", "listed for sale")) {
                     this.saleConfirmed = true;
                     this.auctionRetryTick = 0L;
                  }
               }
            });
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
         int output = EconomyInventory.count(player, Items.ENCHANTED_GOLDEN_APPLE);
         int apples = EconomyInventory.count(player, Items.APPLE);
         int blocks = EconomyInventory.count(player, Items.GOLD_BLOCK);
         int ingots = EconomyInventory.count(player, Items.GOLD_INGOT);
         boolean canTake = this.canTakeResources();
         switch (EconomyAutomationPolicy.crafterAction(output, apples, blocks, ingots, this.autoSell.getValue(), canTake, tick >= this.auctionRetryTick)) {
            case SELL:
               this.machine.transition(AutoCrafterFeature.State.OPEN_AUCTION_SEARCH, tick);
               break;
            case CRAFT_APPLE:
               this.pendingRecipe = CraftingMenuController.Recipe.ENCHANTED_GOLDEN_APPLE;
               this.machine.transition(AutoCrafterFeature.State.FIND_TABLE, tick);
               break;
            case CRAFT_GOLD_BLOCK:
               this.pendingRecipe = CraftingMenuController.Recipe.GOLD_BLOCK;
               this.machine.transition(AutoCrafterFeature.State.FIND_TABLE, tick);
               break;
            case TAKE_APPLES:
               this.chestKind = SynchronizationFeature.isActive() ? AutoCrafterFeature.ChestKind.APPLE : AutoCrafterFeature.ChestKind.CRAFTER;
               this.machine.transition(AutoCrafterFeature.State.FIND_CHEST, tick);
               break;
            case TAKE_GOLD:
               this.chestKind = SynchronizationFeature.isActive() ? AutoCrafterFeature.ChestKind.GOLD : AutoCrafterFeature.ChestKind.CRAFTER;
               this.machine.transition(AutoCrafterFeature.State.FIND_CHEST, tick);
               break;
            case WAIT:
               this.finishCycle(client, tick, 40L);
         }
      }
   }

   private void findChest(MinecraftClient client, ClientPlayerEntity player, long tick) {
      this.targetBlock = this.findSignedChest(client, player, this.chestKind);
      if (this.targetBlock == null) {
         if (this.autoSell.getValue() && tick >= this.auctionRetryTick && EconomyInventory.count(player, Items.ENCHANTED_GOLDEN_APPLE) > 0) {
            this.machine.transition(AutoCrafterFeature.State.OPEN_AUCTION_SEARCH, tick);
         } else {
            this.finishCycle(client, tick, 100L);
         }
      } else {
         this.machine.transition(AutoCrafterFeature.State.MOVE_CHEST, tick);
      }
   }

   private void findTable(MinecraftClient client, ClientPlayerEntity player, long tick) {
      this.targetBlock = NearbyEconomyBlocks.nearestCraftingTable(client.world, player, 16);
      if (this.targetBlock == null) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.machine.transition(AutoCrafterFeature.State.MOVE_TABLE, tick);
      }
   }

   private void moveToTarget(ClientPlayerEntity player, AutoCrafterFeature.State next, long tick) {
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

   private void openChest(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (player.currentScreenHandler instanceof GenericContainerScreenHandler menu && player.currentScreenHandler != player.playerScreenHandler) {
         this.ownedContainerId = menu.syncId;
         this.lootActions = 0;
         this.machine.transition(AutoCrafterFeature.State.LOOT_CHEST, tick);
         return;
      }

      if (this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.interactTarget(client, player, tick);
      }
   }

   private void openTable(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (player.currentScreenHandler instanceof CraftingScreenHandler menu) {
         this.ownedContainerId = menu.syncId;
         this.crafting.reset();
         this.machine.transition(AutoCrafterFeature.State.CRAFT, tick);
      } else if (this.machine.ticksInState(tick) > 100L) {
         this.finishCycle(client, tick, 100L);
      } else {
         this.interactTarget(client, player, tick);
      }
   }

   private void interactTarget(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (this.targetBlock != null && this.actionCooldown.tryAcquire(tick, 10L)) {
         client.interactionManager
            .interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(this.targetBlock), Direction.UP, this.targetBlock, false));
         player.swingHand(Hand.MAIN_HAND);
      }
   }

   private void lootChest(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (player.currentScreenHandler instanceof GenericContainerScreenHandler menu && menu.syncId == this.ownedContainerId) {
         int apples = EconomyInventory.count(player, Items.APPLE);
         int blocks = EconomyInventory.count(player, Items.GOLD_BLOCK);
         int ingots = EconomyInventory.count(player, Items.GOLD_INGOT);
         if (apples < 1 || blocks < 8 && ingots < 9) {
            if (!this.actionCooldown.ready(tick)) {
               return;
            }

            int source = EconomyMenus.findContainerSlot(menu, stack -> this.shouldLoot(stack, apples, blocks, ingots));
            if (source >= 0 && this.lootActions++ < 20) {
               if (EconomyMenus.quickMove(client, menu, source)) {
                  this.actionCooldown.tryAcquire(tick, 3L);
               }

               return;
            }

            this.closeOwned(client);
            this.machine.transition(AutoCrafterFeature.State.SELECT, tick);
            return;
         }

         this.closeOwned(client);
         this.machine.transition(AutoCrafterFeature.State.SELECT, tick);
         return;
      }

      this.finishCycle(client, tick, 60L);
   }

   private boolean shouldLoot(ItemStack stack, int apples, int blocks, int ingots) {
      return switch (this.chestKind) {
         case APPLE -> apples < 1 && stack.isOf(Items.APPLE);
         case GOLD -> blocks < 8 && (stack.isOf(Items.GOLD_BLOCK) || ingots < 72 && stack.isOf(Items.GOLD_INGOT));
         case CRAFTER -> apples < 1 && stack.isOf(Items.APPLE) || blocks < 8 && (stack.isOf(Items.GOLD_BLOCK) || ingots < 72 && stack.isOf(Items.GOLD_INGOT));
      };
   }

   private void craft(MinecraftClient client, long tick) {
      if (!(client.player.currentScreenHandler instanceof CraftingScreenHandler menu) || menu.syncId != this.ownedContainerId || this.pendingRecipe == null) {
         this.finishCycle(client, tick, 60L);
         return;
      }

      if (this.actionCooldown.ready(tick)) {
         CraftingMenuController.Result result = this.crafting.tick(client, menu, this.pendingRecipe);
         this.actionCooldown.tryAcquire(tick, 2L);
         if (result == CraftingMenuController.Result.CRAFTED) {
            this.closeOwned(client);
            this.pendingRecipe = null;
            this.machine.transition(AutoCrafterFeature.State.SELECT, tick);
         } else if (result == CraftingMenuController.Result.FAILED) {
            this.crafting.cleanup(client, menu);
            this.finishCycle(client, tick, 100L);
         }
      }
   }

   private void openAuctionSearch(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (this.commandCooldown.ready(tick)) {
         ServerAdapter adapter = ServerAdapters.current();
         String query = "зачарованное золотое яблоко";
         adapter.auctionCommand().flatMap(root -> EconomyCommands.auctionSearch(root, query)).ifPresentOrElse(command -> {
            this.saleCount = largestStackCount(player, Items.ENCHANTED_GOLDEN_APPLE);
            if (this.saleCount <= 0) {
               this.finishCycle(client, tick, 40L);
            } else {
               adapter.sendCommand(player, command);
               this.commandCooldown.tryAcquire(tick, 40L);
               this.machine.transition(AutoCrafterFeature.State.WAIT_AUCTION_SEARCH, tick);
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
         OptionalLong price = AuctionPriceScanner.competitivePrice(menu, Items.ENCHANTED_GOLDEN_APPLE, "зачарованное золотое яблоко", this.saleCount);
         if (!price.isEmpty()) {
            this.salePrice = price.getAsLong();
            this.closeOwned(client);
            this.machine.transition(AutoCrafterFeature.State.LIST_AUCTION, tick);
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
            this.machine.transition(AutoCrafterFeature.State.WAIT_SALE_CONFIRMATION, tick);
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
      if (this.saleCount <= 0) {
         return false;
      } else {
         ItemStack mainHand = player.getMainHandStack();
         if (mainHand.isOf(Items.ENCHANTED_GOLDEN_APPLE) && mainHand.getCount() == this.saleCount) {
            return true;
         } else {
            int menuSlot = InventoryUtil.findPlayerMenuSlot(player, stack -> stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) && stack.getCount() == this.saleCount);
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

   private BlockPos findSignedChest(MinecraftClient client, ClientPlayerEntity player, AutoCrafterFeature.ChestKind kind) {
      return switch (kind) {
         case APPLE -> this.firstSignedChest(client, player, "яблок", "apple");
         case GOLD -> this.firstSignedChest(client, player, "золот", "gold");
         case CRAFTER -> this.firstSignedChest(client, player, "крафтер", "crafter");
      };
   }

   private BlockPos firstSignedChest(MinecraftClient client, ClientPlayerEntity player, String... labels) {
      for (String label : labels) {
         BlockPos found = NearbyEconomyBlocks.nearestSignedChest(client.world, player, 16, label);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private boolean canTakeResources() {
      return SynchronizationFeature.isActive() || this.takeCrafterChest.getValue();
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
      this.targetBlock = null;
      this.chestKind = null;
      this.pendingRecipe = null;
      this.saleCount = 0;
      this.salePrice = 0L;
      this.saleConfirmed = false;
      this.saleRejected = false;
      this.lootActions = 0;
      this.crafting.reset();
      this.machine.transition(AutoCrafterFeature.State.WAIT, tick);
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
      this.nextWorkTick = 0L;
      this.auctionRetryTick = 0L;
      this.targetBlock = null;
      this.chestKind = null;
      this.pendingRecipe = null;
      this.ownedContainerId = -1;
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }

      this.saleCount = 0;
      this.salePrice = 0L;
      this.originalSelectedSlot = -1;
      this.saleSwapMenuSlot = -1;
      this.saleHotbarSlot = -1;
      this.saleConfirmed = false;
      this.saleRejected = false;
      this.lootActions = 0;
      this.lastTick = 0L;
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
   static enum ChestKind {
      APPLE,
      GOLD,
      CRAFTER;
   }

   @Environment(EnvType.CLIENT)
   static enum State {
      WAIT,
      SELECT,
      FIND_CHEST,
      MOVE_CHEST,
      OPEN_CHEST,
      LOOT_CHEST,
      FIND_TABLE,
      MOVE_TABLE,
      OPEN_TABLE,
      CRAFT,
      OPEN_AUCTION_SEARCH,
      WAIT_AUCTION_SEARCH,
      LIST_AUCTION,
      WAIT_SALE_CONFIRMATION;
   }
}
