package org.ryzen.feature.impl.pve;

import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldJoinEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.mixin.accessor.FishingHookAccessor;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;

@Environment(EnvType.CLIENT)
public final class AutoFishFeature extends PveFeature {
   private static final int SAVE_DURABILITY = 10;
   private static final int INVENTORY_SIZE = 36;
   private static final int HOTBAR_SIZE = 9;
   private static final long HOOK_APPEAR_TIMEOUT_TICKS = 40L;
   private static final long RECAST_DELAY_TICKS = 8L;
   private static final long BITE_LATCH_TIMEOUT_TICKS = 40L;
   private static final long SPLASH_MAX_AGE_NANOS = 2000000000L;
   private static final double SPLASH_DISTANCE_SQUARED = 9.0;
   public final BooleanSetting saveRod = this.register(new BooleanSetting("Save Rod", false));
   private final AtomicReference<AutoFishFeature.SplashSignal> splashSignal = new AtomicReference<>();
   private AutoFishFeature.FishingState state = AutoFishFeature.FishingState.READY_TO_CAST;
   private long tick;
   private long stateSinceTick;
   private int observedHookId = -1;
   private boolean wasBiting;
   private boolean biteLatched;
   private long biteLatchedAtTick;
   private Hand activeHand;
   private int swapSettleTicks;
   private int managedHotbarSlot = -1;
   private int managedInventorySlot = -1;
   private int restoreSelectedSlot = -1;
   private ItemStack displacedStack = ItemStack.EMPTY;

   public AutoFishFeature() {
      super("AutoFish", "Casts and reels a fishing rod automatically", -1, AutomationPriority.FEATURE);
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntime();
   }

   @Override
   protected void onPveDisable() {
      this.restoreManagedRod();
      this.resetRuntime();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      this.tick++;
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && client.interactionManager != null) {
         if (client.currentScreen == null) {
            FishingBobberEntity hook = player.fishHook;
            if (hook != null && hook.getPlayerOwner() == player && !hook.isRemoved()) {
               this.handleHookPresent(client, player, hook);
            } else {
               this.handleHookAbsent(client, player);
            }
         }
      } else {
         if (player != null && this.hasManagedRod()) {
            this.restoreManagedRod();
         }

         this.resetForMissingWorld();
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         if (event.getPacket() instanceof PlaySoundS2CPacket packet && isBobberSplash(packet.getSound())) {
            this.splashSignal.set(new AutoFishFeature.SplashSignal(packet.getX(), packet.getY(), packet.getZ(), -1, System.nanoTime()));
            return;
         }

         if (event.getPacket() instanceof PlaySoundFromEntityS2CPacket packet && isBobberSplash(packet.getSound())) {
            this.splashSignal.set(new AutoFishFeature.SplashSignal(0.0, 0.0, 0.0, packet.getEntityId(), System.nanoTime()));
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.restoreManagedRod();
      this.resetRuntime();
   }

   @EventTarget
   public void onWorldJoin(WorldJoinEvent event) {
      this.resetRuntime();
   }

   private void handleHookAbsent(MinecraftClient client, ClientPlayerEntity player) {
      this.clearObservedHook();
      if (this.state == AutoFishFeature.FishingState.REEL_SENT || this.state == AutoFishFeature.FishingState.WAITING_FOR_BITE) {
         this.transition(AutoFishFeature.FishingState.RECAST_COOLDOWN);
      } else if (this.state == AutoFishFeature.FishingState.WAITING_FOR_HOOK) {
         if (this.ticksInState() >= 40L) {
            this.transition(AutoFishFeature.FishingState.READY_TO_CAST);
         }
      } else if (this.state == AutoFishFeature.FishingState.RECAST_COOLDOWN) {
         if (this.ticksInState() >= 8L) {
            this.transition(AutoFishFeature.FishingState.READY_TO_CAST);
         }
      } else {
         Hand hand = this.prepareRod(player);
         if (hand != null) {
            this.splashSignal.set(null);
            this.useRod(client, player, hand);
            this.activeHand = hand;
            this.transition(AutoFishFeature.FishingState.WAITING_FOR_HOOK);
         }
      }
   }

   private void handleHookPresent(MinecraftClient client, ClientPlayerEntity player, FishingBobberEntity hook) {
      if (hook.getId() != this.observedHookId) {
         this.observedHookId = hook.getId();
         this.wasBiting = false;
         this.biteLatched = false;
         this.transition(AutoFishFeature.FishingState.WAITING_FOR_BITE);
      } else if (this.state != AutoFishFeature.FishingState.REEL_SENT && this.state != AutoFishFeature.FishingState.WAITING_FOR_BITE) {
         this.transition(AutoFishFeature.FishingState.WAITING_FOR_BITE);
      }

      boolean biting = ((FishingHookAccessor)hook).blade$isBiting();
      if (biting && !this.wasBiting) {
         this.latchBite();
      }

      if (this.consumeSplashNear(hook)) {
         this.latchBite();
      }

      this.wasBiting = biting;
      if (this.state != AutoFishFeature.FishingState.REEL_SENT && this.biteLatched) {
         if (this.tick - this.biteLatchedAtTick > 40L) {
            this.biteLatched = false;
         } else {
            Hand hand = this.prepareRod(player);
            if (hand != null) {
               this.useRod(client, player, hand);
               this.activeHand = hand;
               this.biteLatched = false;
               this.transition(AutoFishFeature.FishingState.REEL_SENT);
            }
         }
      }
   }

   private Hand prepareRod(ClientPlayerEntity player) {
      if (this.swapSettleTicks > 0) {
         this.swapSettleTicks--;
         return null;
      } else {
         Hand heldHand = this.findHeldRodHand(player);
         if (heldHand == null) {
            return null;
         } else {
            ItemStack held = player.getStackInHand(heldHand);
            if (!this.saveRod.getValue() || remainingDurability(held) > 10) {
               return heldHand;
            } else if (this.hasManagedRod()) {
               if (this.restoreManagedRod()) {
                  this.swapSettleTicks = Math.max(this.swapSettleTicks, 1);
               }

               return null;
            } else {
               AutoFishFeature.RodCandidate candidate = this.findBestUsableRod(player);
               if (candidate == null) {
                  return null;
               } else {
                  return candidate.offhand() ? Hand.OFF_HAND : this.activateInventoryRod(player, candidate.inventoryIndex());
               }
            }
         }
      }
   }

   private Hand findHeldRodHand(ClientPlayerEntity player) {
      if (this.managedHotbarSlot >= 0 && player.getInventory().getSelectedSlot() == this.managedHotbarSlot && isRod(player.getMainHandStack())) {
         return Hand.MAIN_HAND;
      } else if (this.activeHand != null && isRod(player.getStackInHand(this.activeHand))) {
         return this.activeHand;
      } else if (isRod(player.getMainHandStack())) {
         return Hand.MAIN_HAND;
      } else {
         return isRod(player.getOffHandStack()) ? Hand.OFF_HAND : null;
      }
   }

   private AutoFishFeature.RodCandidate findBestUsableRod(ClientPlayerEntity player) {
      AutoFishFeature.RodCandidate best = null;
      int selectedSlot = player.getInventory().getSelectedSlot();

      for (int index = 0; index < 36; index++) {
         ItemStack stack = player.getInventory().getStack(index);
         if (isUsableReplacement(stack)) {
            int accessibility = index == selectedSlot ? 3 : (index < 9 ? 2 : 1);
            AutoFishFeature.RodCandidate candidate = new AutoFishFeature.RodCandidate(
               index, false, remainingDurability(stack), unbreakingLevel(stack), accessibility
            );
            if (isBetter(candidate, best)) {
               best = candidate;
            }
         }
      }

      ItemStack offhand = player.getOffHandStack();
      if (isUsableReplacement(offhand)) {
         AutoFishFeature.RodCandidate candidate = new AutoFishFeature.RodCandidate(-1, true, remainingDurability(offhand), unbreakingLevel(offhand), 3);
         if (isBetter(candidate, best)) {
            best = candidate;
         }
      }

      return best;
   }

   private Hand activateInventoryRod(ClientPlayerEntity player, int inventoryIndex) {
      int selectedSlot = player.getInventory().getSelectedSlot();
      if (inventoryIndex == selectedSlot) {
         return Hand.MAIN_HAND;
      } else if (!this.claim(AutomationResource.INVENTORY, new AutomationResource[0])) {
         return null;
      } else {
         Object var4;
         try {
            if (!isUsableReplacement(player.getInventory().getStack(inventoryIndex))) {
               return null;
            }

            if (inventoryIndex < 9) {
               this.restoreSelectedSlot = selectedSlot;
               this.managedHotbarSlot = inventoryIndex;
               player.getInventory().setSelectedSlot(inventoryIndex);
               return Hand.MAIN_HAND;
            }

            if (player.currentScreenHandler == player.playerScreenHandler && player.playerScreenHandler.getCursorStack().isEmpty()) {
               this.displacedStack = player.getInventory().getStack(selectedSlot).copy();
               MinecraftClient.getInstance()
                  .interactionManager
                  .clickSlot(player.playerScreenHandler.syncId, inventoryIndex, selectedSlot, SlotActionType.SWAP, player);
               this.managedInventorySlot = inventoryIndex;
               this.managedHotbarSlot = selectedSlot;
               this.swapSettleTicks = 1;
               return null;
            }

            var4 = null;
         } finally {
            PveAutomationCoordinator.INSTANCE.release(this);
         }

         return (Hand)var4;
      }
   }

   private boolean restoreManagedRod() {
      if (!this.hasManagedRod()) {
         return true;
      } else {
         MinecraftClient client = MinecraftClient.getInstance();
         ClientPlayerEntity player = client.player;
         if (player != null && client.interactionManager != null && this.claim(AutomationResource.INVENTORY, new AutomationResource[0])) {
            boolean var8;
            try {
               if (this.managedInventorySlot >= 0) {
                  if (player.currentScreenHandler != player.playerScreenHandler || !player.playerScreenHandler.getCursorStack().isEmpty()) {
                     return false;
                  }

                  ItemStack source = player.getInventory().getStack(this.managedInventorySlot);
                  ItemStack managed = player.getInventory().getStack(this.managedHotbarSlot);
                  if (ItemStack.areEqual(source, this.displacedStack) && isRod(managed)) {
                     client.interactionManager
                        .clickSlot(player.playerScreenHandler.syncId, this.managedInventorySlot, this.managedHotbarSlot, SlotActionType.SWAP, player);
                     this.swapSettleTicks = 1;
                  }
               }

               if (this.restoreSelectedSlot >= 0 && player.getInventory().getSelectedSlot() == this.managedHotbarSlot) {
                  player.getInventory().setSelectedSlot(this.restoreSelectedSlot);
               }

               this.clearManagedRod();
               var8 = true;
            } finally {
               PveAutomationCoordinator.INSTANCE.release(this);
            }

            return var8;
         } else {
            return false;
         }
      }
   }

   private void useRod(MinecraftClient client, ClientPlayerEntity player, Hand hand) {
      if (isRod(player.getStackInHand(hand))) {
         ActionResult result = client.interactionManager.interactItem(player, hand);
         if (result.isAccepted()) {
            player.swingHand(hand);
         }
      }
   }

   private boolean consumeSplashNear(FishingBobberEntity hook) {
      AutoFishFeature.SplashSignal signal = this.splashSignal.getAndSet(null);
      if (signal == null || System.nanoTime() - signal.receivedAtNanos() > 2000000000L) {
         return false;
      } else {
         return signal.entityId() >= 0 ? signal.entityId() == hook.getId() : hook.squaredDistanceTo(signal.x(), signal.y(), signal.z()) <= 9.0;
      }
   }

   private void latchBite() {
      this.biteLatched = true;
      this.biteLatchedAtTick = this.tick;
   }

   private void transition(AutoFishFeature.FishingState next) {
      if (this.state != next) {
         this.state = next;
         this.stateSinceTick = this.tick;
      }
   }

   private long ticksInState() {
      return Math.max(0L, this.tick - this.stateSinceTick);
   }

   private void clearObservedHook() {
      this.observedHookId = -1;
      this.wasBiting = false;
      this.biteLatched = false;
   }

   private void resetForMissingWorld() {
      this.splashSignal.set(null);
      this.state = AutoFishFeature.FishingState.READY_TO_CAST;
      this.stateSinceTick = this.tick;
      this.activeHand = null;
      this.swapSettleTicks = 0;
      this.clearObservedHook();
      this.clearManagedRod();
   }

   private void resetRuntime() {
      this.tick = 0L;
      this.state = AutoFishFeature.FishingState.READY_TO_CAST;
      this.stateSinceTick = 0L;
      this.activeHand = null;
      this.swapSettleTicks = 0;
      this.splashSignal.set(null);
      this.clearObservedHook();
      this.clearManagedRod();
   }

   private boolean hasManagedRod() {
      return this.managedHotbarSlot >= 0;
   }

   private void clearManagedRod() {
      this.managedHotbarSlot = -1;
      this.managedInventorySlot = -1;
      this.restoreSelectedSlot = -1;
      this.displacedStack = ItemStack.EMPTY;
   }

   private static boolean isBobberSplash(RegistryEntry<SoundEvent> sound) {
      return sound != null && sound.value() == SoundEvents.ENTITY_FISHING_BOBBER_SPLASH;
   }

   private static boolean isRod(ItemStack stack) {
      return stack != null && stack.isOf(Items.FISHING_ROD);
   }

   private static boolean isUsableReplacement(ItemStack stack) {
      return isRod(stack) && isUsableRodDurability(remainingDurability(stack));
   }

   static boolean isUsableRodDurability(int remainingDurability) {
      return remainingDurability > 10;
   }

   private static int remainingDurability(ItemStack stack) {
      return Math.max(0, stack.getMaxDamage() - stack.getDamage());
   }

   private static int unbreakingLevel(ItemStack stack) {
      for (RegistryEntry<Enchantment> enchantment : stack.getEnchantments().getEnchantments()) {
         if (enchantment.matchesKey(Enchantments.UNBREAKING)) {
            return stack.getEnchantments().getLevel(enchantment);
         }
      }

      return 0;
   }

   static int compareRodQuality(int remainingA, int unbreakingA, int remainingB, int unbreakingB) {
      int durability = Integer.compare(remainingA, remainingB);
      return durability != 0 ? durability : Integer.compare(unbreakingA, unbreakingB);
   }

   private static boolean isBetter(AutoFishFeature.RodCandidate candidate, AutoFishFeature.RodCandidate currentBest) {
      if (currentBest == null) {
         return true;
      } else {
         int quality = compareRodQuality(
            candidate.remainingDurability(), candidate.unbreakingLevel(), currentBest.remainingDurability(), currentBest.unbreakingLevel()
         );
         if (quality != 0) {
            return quality > 0;
         } else {
            return candidate.accessibility() != currentBest.accessibility()
               ? candidate.accessibility() > currentBest.accessibility()
               : candidate.inventoryIndex() < currentBest.inventoryIndex();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum FishingState {
      READY_TO_CAST,
      WAITING_FOR_HOOK,
      WAITING_FOR_BITE,
      REEL_SENT,
      RECAST_COOLDOWN;
   }

   @Environment(EnvType.CLIENT)
   private static record RodCandidate(int inventoryIndex, boolean offhand, int remainingDurability, int unbreakingLevel, int accessibility) {
   }

   @Environment(EnvType.CLIENT)
   private static record SplashSignal(double x, double y, double z, int entityId, long receivedAtNanos) {
   }
}
