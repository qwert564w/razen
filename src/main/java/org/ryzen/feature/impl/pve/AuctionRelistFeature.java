package org.ryzen.feature.impl.pve;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.DisconnectEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.pve.PveStateMachine;
import org.ryzen.pve.economy.CommandCooldown;
import org.ryzen.pve.economy.EconomyChat;
import org.ryzen.pve.economy.EconomyCommands;
import org.ryzen.pve.economy.EconomyItemText;
import org.ryzen.pve.economy.EconomyMenus;
import org.ryzen.pve.economy.EconomyTextParser;
import org.ryzen.pve.server.ServerAdapter;
import org.ryzen.pve.server.ServerAdapters;
import org.ryzen.pve.server.ServerProfile;

@Environment(EnvType.CLIENT)
public final class AuctionRelistFeature extends PveFeature {
   private static final long CYCLE_TICKS = 1200L;
   private static final long MENU_TIMEOUT_TICKS = 80L;
   private final PveStateMachine<AuctionRelistFeature.State> machine = new PveStateMachine<>(AuctionRelistFeature.State.WAIT);
   private final CommandCooldown commandCooldown = new CommandCooldown();
   private long lastTick;
   private long nextCycleTick;
   private int ownedContainerId = -1;
   private boolean resourcesClaimed;

   public AuctionRelistFeature() {
      super("AuctionRelist", "Periodically relists expired auction items", -1, AutomationPriority.FEATURE);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      if (client.player != null && client.world != null) {
         long tick = client.world.getTime();
         this.lastTick = tick;
         if (ServerAdapters.current().profile() == ServerProfile.FUNTIME) {
            switch ((AuctionRelistFeature.State)this.machine.state()) {
               case WAIT:
                  if (tick >= this.nextCycleTick && !isPlayerMoving(client)) {
                     this.beginCycle(tick);
                  }
                  break;
               case OPEN_AUCTION:
                  this.openAuction(client, tick);
                  break;
               case WAIT_AUCTION_MENU:
                  this.waitForAuctionMenu(client, tick);
                  break;
               case WAIT_STORAGE_MENU:
                  this.waitForStorageMenu(client, tick);
                  break;
               case CLOSING:
                  if (this.machine.ticksInState(tick) >= 6L) {
                     this.finishCycle(client, tick);
                  }
            }
         } else {
            if (this.resourcesClaimed || !this.machine.is(AuctionRelistFeature.State.WAIT)) {
               this.finishCycle(client, tick);
            }
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         String text = EconomyChat.incomingText(event.getPacket());
         if (text != null && EconomyTextParser.containsAny(text, "аукцион недоступен", "auction is unavailable", "не удалось открыть аукцион", "слишком часто")
            )
          {
            MinecraftClient.getInstance().execute(() -> {
               if (this.isEnabled()) {
                  this.finishCycle(MinecraftClient.getInstance(), this.lastTick);
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
      this.nextCycleTick = this.lastTick + 1200L;
   }

   @Override
   protected void onPveDisable() {
      this.resetRuntime(true);
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.resetRuntime(true);
   }

   private void beginCycle(long tick) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.currentScreen == null && client.player.currentScreenHandler == client.player.playerScreenHandler) {
         if (!this.claim(AutomationResource.INVENTORY, new AutomationResource[]{AutomationResource.SCREEN, AutomationResource.CHAT})) {
            this.nextCycleTick = tick + 10L;
         } else {
            this.resourcesClaimed = true;
            this.machine.transition(AuctionRelistFeature.State.OPEN_AUCTION, tick);
         }
      } else {
         this.nextCycleTick = tick + 20L;
      }
   }

   private void openAuction(MinecraftClient client, long tick) {
      if (this.commandCooldown.ready(tick)) {
         ServerAdapter adapter = ServerAdapters.current();
         adapter.auctionCommand().flatMap(EconomyCommands::auctionRoot).ifPresentOrElse(command -> {
            adapter.sendCommand(client.player, command);
            this.commandCooldown.tryAcquire(tick, 40L);
            this.machine.transition(AuctionRelistFeature.State.WAIT_AUCTION_MENU, tick);
         }, () -> this.finishCycle(client, tick));
      }
   }

   private void waitForAuctionMenu(MinecraftClient client, long tick) {
      if (this.machine.ticksInState(tick) > 80L) {
         this.finishCycle(client, tick);
      } else if (EconomyMenus.titleContains(client, "аукцион", "auction")) {
         ScreenHandler menu = client.player.currentScreenHandler;
         int storage = EconomyMenus.findContainerSlot(
            menu, stack -> EconomyItemText.containsAny(stack, "хранилище", "storage", "истекшие", "expired", "снятые товары")
         );
         if (storage >= 0) {
            this.ownedContainerId = menu.syncId;
            if (EconomyMenus.click(client, menu, storage, 0, SlotActionType.PICKUP)) {
               this.machine.transition(AuctionRelistFeature.State.WAIT_STORAGE_MENU, tick);
            }
         }
      }
   }

   private void waitForStorageMenu(MinecraftClient client, long tick) {
      if (this.machine.ticksInState(tick) > 80L) {
         this.finishCycle(client, tick);
      } else if (EconomyMenus.titleContains(client, "хранилище", "storage")) {
         ScreenHandler menu = client.player.currentScreenHandler;
         int relist = EconomyMenus.findContainerSlot(
            menu, stack -> EconomyItemText.containsAny(stack, "перевыставить", "перевыстав", "выставить снова", "relist")
         );
         if (relist < 0) {
            if (this.machine.ticksInState(tick) >= 10L) {
               this.ownedContainerId = menu.syncId;
               this.machine.transition(AuctionRelistFeature.State.CLOSING, tick);
            }
         } else {
            this.ownedContainerId = menu.syncId;
            if (EconomyMenus.click(client, menu, relist, 0, SlotActionType.PICKUP)) {
               this.machine.transition(AuctionRelistFeature.State.CLOSING, tick);
            }
         }
      }
   }

   private void finishCycle(MinecraftClient client, long tick) {
      if (this.isRecognizedMenu(client)) {
         EconomyMenus.closeOwned(client, this.ownedContainerId);
      }

      this.ownedContainerId = -1;
      this.nextCycleTick = tick + 1200L;
      this.machine.transition(AuctionRelistFeature.State.WAIT, tick);
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }
   }

   private boolean isRecognizedMenu(MinecraftClient client) {
      return EconomyMenus.currentContainerId(client) == this.ownedContainerId
         && EconomyMenus.titleContains(client, "аукцион", "auction", "хранилище", "storage");
   }

   private static boolean isPlayerMoving(MinecraftClient client) {
      return client.player != null && client.player.getVelocity().horizontalLengthSquared() > 0.0025;
   }

   private void resetRuntime(boolean closeScreen) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (closeScreen && this.isRecognizedMenu(client)) {
         EconomyMenus.closeOwned(client, this.ownedContainerId);
      }

      this.machine.reset(0L);
      this.commandCooldown.reset();
      this.ownedContainerId = -1;
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }

      this.nextCycleTick = 1200L;
      this.lastTick = 0L;
   }

   @Environment(EnvType.CLIENT)
   static enum State {
      WAIT,
      OPEN_AUCTION,
      WAIT_AUCTION_MENU,
      WAIT_STORAGE_MENU,
      CLOSING;
   }
}
