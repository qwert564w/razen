package org.ryzen.feature.impl.pve;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.DisconnectEvent;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.pve.PveStateMachine;
import org.ryzen.pve.economy.CommandCooldown;
import org.ryzen.pve.economy.EconomyInventory;
import org.ryzen.pve.economy.ServerUiText;
import org.ryzen.pve.server.ServerAdapters;
import org.ryzen.pve.server.ServerProfile;

@Environment(EnvType.CLIENT)
public final class ClanUpgradeFeature extends PveFeature {
   private final PveStateMachine<ClanUpgradeFeature.State> machine = new PveStateMachine<>(ClanUpgradeFeature.State.FIND_ITEM);
   private final CommandCooldown actionCooldown = new CommandCooldown();
   private long lastTick;
   private int upgradeSlot = -1;
   private int restoreSlot = -1;
   private Item upgradeItem;
   private BlockPos supportBlock;
   private boolean resourcesClaimed;
   private boolean rotationSaved;
   private float savedYaw;
   private float savedPitch;

   public ClanUpgradeFeature() {
      super("ClanUpgrade", "Uses the held clan-upgrade items on the block below", -1, AutomationPriority.FEATURE);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && client.interactionManager != null) {
         long tick = client.world.getTime();
         this.lastTick = tick;
         if (ServerAdapters.current().profile() != ServerProfile.FUNTIME
            || ServerUiText.tabHeader(client).contains("lobby")
            || ServerUiText.tabHeader(client).contains("лобби")) {
            this.cancelAction(player, tick);
         } else if (client.currentScreen == null && player.currentScreenHandler == player.playerScreenHandler && !player.isUsingItem()) {
            switch ((ClanUpgradeFeature.State)this.machine.state()) {
               case FIND_ITEM:
                  this.findItem(player, tick);
                  break;
               case SELECT_ITEM:
                  this.selectItem(player, tick);
                  break;
               case PLACE:
                  this.placeItem(client, player, tick);
                  break;
               case BREAK:
                  this.breakPlacedItem(client, player, tick);
                  break;
               case COOLDOWN:
                  if (this.machine.ticksInState(tick) >= 5L) {
                     this.finishAction(player, tick);
                  }
            }
         } else {
            this.cancelAction(player, tick);
         }
      }
   }

   @EventTarget
   public void onDisconnect(DisconnectEvent event) {
      this.cancelAction(MinecraftClient.getInstance().player, this.lastTick);
      this.resetRuntime();
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntime();
   }

   @Override
   protected void onPveDisable() {
      this.cancelAction(MinecraftClient.getInstance().player, this.lastTick);
      this.resetRuntime();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelAction(MinecraftClient.getInstance().player, this.lastTick);
      this.resetRuntime();
   }

   private void findItem(ClientPlayerEntity player, long tick) {
      if (this.actionCooldown.ready(tick)) {
         int slot = EconomyInventory.findHotbar(player, stack -> stack.isOf(Items.TORCH) || stack.isOf(Items.REDSTONE));
         if (slot < 0) {
            this.actionCooldown.defer(tick, 100L);
         } else {
            BlockPos below = player.getBlockPos().down();
            if (!player.getEntityWorld().getBlockState(below).isSideSolidFullSquare(player.getEntityWorld(), below, Direction.UP)) {
               this.actionCooldown.defer(tick, 20L);
            } else {
               boolean claimed = PveManagerFeature.INSTANCE.rotate.getValue()
                  ? this.claim(AutomationResource.INVENTORY, new AutomationResource[]{AutomationResource.ROTATION, AutomationResource.COMBAT})
                  : this.claim(AutomationResource.INVENTORY, new AutomationResource[]{AutomationResource.COMBAT});
               if (!claimed) {
                  this.actionCooldown.defer(tick, 5L);
               } else {
                  this.resourcesClaimed = true;
                  this.restoreSlot = player.getInventory().getSelectedSlot();
                  this.upgradeSlot = slot;
                  this.upgradeItem = player.getInventory().getStack(slot).getItem();
                  this.supportBlock = below.toImmutable();
                  this.machine.transition(ClanUpgradeFeature.State.SELECT_ITEM, tick);
               }
            }
         }
      }
   }

   private void selectItem(ClientPlayerEntity player, long tick) {
      if (!EconomyInventory.selectHotbar(player, this.upgradeSlot)) {
         this.cancelAction(player, tick);
      } else {
         this.machine.transition(ClanUpgradeFeature.State.PLACE, tick);
      }
   }

   private void placeItem(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (this.supportBlock != null
         && this.upgradeItem != null
         && player.getMainHandStack().isOf(this.upgradeItem)
         && player.getEntityWorld().getBlockState(this.supportBlock).isSideSolidFullSquare(player.getEntityWorld(), this.supportBlock, Direction.UP)) {
         this.rotateToward(player, Vec3d.ofCenter(this.supportBlock));
         Vec3d hitPosition = Vec3d.ofCenter(this.supportBlock).add(0.0, 0.5, 0.0);
         client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(hitPosition, Direction.UP, this.supportBlock, false));
         player.swingHand(Hand.MAIN_HAND);
         this.machine.transition(ClanUpgradeFeature.State.BREAK, tick);
      } else {
         this.cancelAction(player, tick);
      }
   }

   private void breakPlacedItem(MinecraftClient client, ClientPlayerEntity player, long tick) {
      if (this.supportBlock == null) {
         this.cancelAction(player, tick);
      } else {
         BlockPos placedPos = this.supportBlock.up();
         BlockState placedState = player.getEntityWorld().getBlockState(placedPos);
         boolean expectedBlock = this.upgradeItem == Items.TORCH
            ? placedState.isOf(Blocks.TORCH) || placedState.isOf(Blocks.WALL_TORCH)
            : this.upgradeItem == Items.REDSTONE && placedState.isOf(Blocks.REDSTONE_WIRE);
         if (!expectedBlock) {
            if (this.machine.ticksInState(tick) >= 10L) {
               this.actionCooldown.defer(tick, 20L);
               this.cancelAction(player, tick);
            }
         } else {
            this.rotateToward(player, Vec3d.ofCenter(placedPos));
            client.interactionManager.attackBlock(placedPos, Direction.UP);
            this.actionCooldown.tryAcquire(tick, 5L);
            this.machine.transition(ClanUpgradeFeature.State.COOLDOWN, tick);
         }
      }
   }

   private void finishAction(ClientPlayerEntity player, long tick) {
      this.restoreRotation(player);
      this.restoreSlot(player);
      this.upgradeSlot = -1;
      this.upgradeItem = null;
      this.supportBlock = null;
      this.machine.transition(ClanUpgradeFeature.State.FIND_ITEM, tick);
      this.releaseResources();
   }

   private void cancelAction(ClientPlayerEntity player, long tick) {
      this.restoreRotation(player);
      this.restoreSlot(player);
      this.upgradeSlot = -1;
      this.upgradeItem = null;
      this.supportBlock = null;
      this.machine.transition(ClanUpgradeFeature.State.FIND_ITEM, tick);
      this.releaseResources();
   }

   private void restoreSlot(ClientPlayerEntity player) {
      if (this.restoreSlot >= 0 && this.restoreSlot < 9) {
         EconomyInventory.selectHotbar(player, this.restoreSlot);
      }

      this.restoreSlot = -1;
   }

   private void releaseResources() {
      if (this.resourcesClaimed) {
         this.resourcesClaimed = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }
   }

   private void rotateToward(ClientPlayerEntity player, Vec3d target) {
      if (PveManagerFeature.INSTANCE.rotate.getValue()) {
         if (!this.rotationSaved) {
            this.savedYaw = player.getYaw();
            this.savedPitch = player.getPitch();
            this.rotationSaved = true;
         }

         Vec3d delta = target.subtract(player.getEyePos());
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         player.setYaw((float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F);
         player.setPitch((float)(-Math.toDegrees(Math.atan2(delta.y, horizontal))));
      }
   }

   private void restoreRotation(ClientPlayerEntity player) {
      if (this.rotationSaved) {
         if (player != null) {
            player.setYaw(this.savedYaw);
            player.setPitch(this.savedPitch);
         }

         this.rotationSaved = false;
      }
   }

   private void resetRuntime() {
      this.machine.reset(0L);
      this.actionCooldown.reset();
      this.upgradeSlot = -1;
      this.restoreSlot = -1;
      this.upgradeItem = null;
      this.supportBlock = null;
      this.rotationSaved = false;
      this.releaseResources();
      this.lastTick = 0L;
   }

   @Environment(EnvType.CLIENT)
   static enum State {
      FIND_ITEM,
      SELECT_ITEM,
      PLACE,
      BREAK,
      COOLDOWN;
   }
}
