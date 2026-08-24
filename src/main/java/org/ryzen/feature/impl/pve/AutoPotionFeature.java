package org.ryzen.feature.impl.pve;

import java.util.Arrays;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import org.ryzen.context.MinecraftContext;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.combat.AuraFeature;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.pve.PvpStateTracker;
import org.ryzen.utils.inventory.DropAllInventoryController;
import org.ryzen.utils.inventory.InventorySwap;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class AutoPotionFeature extends PveFeature implements MinecraftContext {
   private static final String FIRE_RESISTANCE = "Fire Resistance";
   private static final String STRENGTH = "Strength";
   private static final String SPEED = "Speed";
   private static final long THROW_THROTTLE_NANOS = 600000000L;
   private static final int MIN_WORLD_AGE_TICKS = 100;
   private static final int INVENTORY_SWAP_RETURN_TICKS = 4;
   private static final double GROUND_PROBE_DEPTH = 0.5;
   public final MultiSelectSetting potions = this.register(new MultiSelectSetting("Potions", Set.of(), "Fire Resistance", "Strength", "Speed"));
   public final BooleanSetting onlyPvp = this.register(new BooleanSetting("Only PvP", false));
   private boolean throwing;
   private int operationTicks;
   private int potionContainerSlot = -1;
   private int originalHotbarSlot = -1;
   private ItemStack originalHotbarStack = ItemStack.EMPTY;
   private float throwYaw;
   private boolean rotationApplied;
   private long lastThrowNanos;

   public AutoPotionFeature() {
      super("AutoPotion", "Throws selected splash potions when their effects are missing", -1, AutomationPriority.FEATURE);
   }

   @Override
   protected void onPveEnable() {
      this.resetOperationState();
      this.lastThrowNanos = 0L;
   }

   @Override
   protected void onPveDisable() {
      this.cancelOperation();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelOperation();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (this.throwing) {
         this.tickOperation(player);
      } else {
         ClientWorld level = event.getClient().world;
         long nowNanos = System.nanoTime();
         if (this.canStart(player, level) && !InventorySwap.isBusy() && this.throttleElapsed(nowNanos) && (!this.onlyPvp.getValue() || hasActivePlayerTarget())
            )
          {
            AutoPotionFeature.PotionChoice choice = this.findPotion(player);
            if (choice != null && this.claimOperationResources()) {
               this.beginThrow(player, choice, nowNanos);
            }
         }
      }
   }

   @EventTarget
   public void onPlayerInput(PlayerInputEvent event) {
      if (this.throwing) {
         event.clearMovement(false, true);
      }
   }

   private boolean canStart(ClientPlayerEntity player, ClientWorld level) {
      return player != null
         && level != null
         && player.isAlive()
         && player.age > 100
         && !player.isUsingItem()
         && player.currentScreenHandler == player.playerScreenHandler
         && player.playerScreenHandler.getCursorStack().isEmpty()
         && mc.interactionManager != null
         && !DropAllInventoryController.blocksInventoryOperations()
         && isOnOrNearGround(player, level);
   }

   private boolean throttleElapsed(long nowNanos) {
      return this.lastThrowNanos == 0L || nowNanos - this.lastThrowNanos >= 600000000L;
   }

   private AutoPotionFeature.PotionChoice findPotion(ClientPlayerEntity player) {
      AutoPotionFeature.PotionKind[] kinds = AutoPotionFeature.PotionKind.values();
      boolean[] selected = new boolean[kinds.length];
      boolean[] active = new boolean[kinds.length];
      int[] slots = new int[kinds.length];
      Arrays.fill(slots, -1);

      for (int index = 0; index < kinds.length; index++) {
         AutoPotionFeature.PotionKind kind = kinds[index];
         selected[index] = this.potions.isSelected(kind.settingName());
         active[index] = player.hasStatusEffect(kind.effect());
         if (selected[index] && !active[index]) {
            slots[index] = InventoryUtil.findPlayerMenuSlot(player, stack -> containsEffect(stack, kind.effect()));
         }
      }

      int selectedIndex = selectPotionIndex(selected, active, slots);
      return selectedIndex < 0 ? null : new AutoPotionFeature.PotionChoice(kinds[selectedIndex], slots[selectedIndex]);
   }

   private void beginThrow(ClientPlayerEntity player, AutoPotionFeature.PotionChoice choice, long nowNanos) {
      this.potionContainerSlot = choice.containerSlot();
      this.originalHotbarSlot = player.getInventory().getSelectedSlot();
      this.originalHotbarStack = player.getInventory().getStack(this.originalHotbarSlot).copy();
      this.throwYaw = player.getYaw();
      this.operationTicks = 0;
      if (PveManagerFeature.INSTANCE.rotate.getValue()) {
         RotationContext.setRotation(this.throwYaw, 90.0F);
         this.rotationApplied = true;
      }

      InventorySwap.useFromSlot(this.potionContainerSlot);
      if (!InventorySwap.isBusy()) {
         this.clearAppliedRotation();
         PveAutomationCoordinator.INSTANCE.release(this);
         this.resetOperationState();
      } else {
         this.throwing = true;
         this.lastThrowNanos = nowNanos;
      }
   }

   private void tickOperation(ClientPlayerEntity player) {
      if (player == null) {
         this.cancelOperation();
      } else {
         if (this.rotationApplied) {
            RotationContext.setRotation(this.throwYaw, 90.0F);
         }

         this.operationTicks++;
         if (!InventorySwap.isBusy() || this.operationTicks >= 4) {
            this.finishOperation(player);
         }
      }
   }

   private void finishOperation(ClientPlayerEntity player) {
      InventorySwap.abort();
      this.restoreOriginalSlot(player);
      this.clearAppliedRotation();
      PveAutomationCoordinator.INSTANCE.release(this);
      this.resetOperationState();
   }

   private void cancelOperation() {
      if (this.throwing) {
         ClientPlayerEntity player = this.player();
         InventorySwap.abort();
         if (player != null) {
            this.restoreOriginalSlot(player);
         }

         this.clearAppliedRotation();
         PveAutomationCoordinator.INSTANCE.release(this);
         this.resetOperationState();
      }
   }

   private void restoreOriginalSlot(ClientPlayerEntity player) {
      if (this.originalHotbarSlot >= 0
         && this.originalHotbarSlot <= 8
         && this.potionContainerSlot >= 0
         && player.currentScreenHandler == player.playerScreenHandler
         && mc.interactionManager != null) {
         int selectedContainerSlot = 36 + this.originalHotbarSlot;
         if (this.potionContainerSlot != selectedContainerSlot && player.playerScreenHandler.isValid(this.potionContainerSlot)) {
            ItemStack source = player.playerScreenHandler.getSlot(this.potionContainerSlot).getStack();
            ItemStack selected = player.playerScreenHandler.getSlot(selectedContainerSlot).getStack();
            if (ItemStack.areEqual(source, this.originalHotbarStack) && !ItemStack.areEqual(selected, this.originalHotbarStack)) {
               mc.interactionManager
                  .clickSlot(player.playerScreenHandler.syncId, this.potionContainerSlot, this.originalHotbarSlot, SlotActionType.SWAP, player);
            }
         }

         if (player.getInventory().getSelectedSlot() != this.originalHotbarSlot) {
            player.getInventory().setSelectedSlot(this.originalHotbarSlot);
         }
      }
   }

   private boolean claimOperationResources() {
      return PveManagerFeature.INSTANCE.rotate.getValue()
         ? this.claim(
            AutomationResource.INVENTORY, new AutomationResource[]{AutomationResource.ROTATION, AutomationResource.MOVEMENT, AutomationResource.SCREEN}
         )
         : this.claim(AutomationResource.INVENTORY, new AutomationResource[]{AutomationResource.MOVEMENT, AutomationResource.SCREEN});
   }

   private void clearAppliedRotation() {
      if (this.rotationApplied) {
         if (this.owns(AutomationResource.ROTATION) || !PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.ROTATION)) {
            RotationContext.clear();
         }

         this.rotationApplied = false;
      }
   }

   private void resetOperationState() {
      this.throwing = false;
      this.operationTicks = 0;
      this.potionContainerSlot = -1;
      this.originalHotbarSlot = -1;
      this.originalHotbarStack = ItemStack.EMPTY;
      this.throwYaw = 0.0F;
      this.rotationApplied = false;
   }

   private static boolean isOnOrNearGround(ClientPlayerEntity player, ClientWorld level) {
      return player.isOnGround() ? true : level.getBlockCollisions(player, player.getBoundingBox().stretch(0.0, -0.5, 0.0)).iterator().hasNext();
   }

   private static boolean containsEffect(ItemStack stack, RegistryEntry<StatusEffect> expected) {
      if (!stack.isOf(Items.SPLASH_POTION)) {
         return false;
      } else {
         PotionContentsComponent contents = (PotionContentsComponent)stack.get(DataComponentTypes.POTION_CONTENTS);
         if (contents == null) {
            return false;
         } else {
            for (StatusEffectInstance effect : contents.getEffects()) {
               if (effect.getEffectType().equals(expected)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private static boolean hasActivePlayerTarget() {
      if (PvpStateTracker.INSTANCE.isActive()) {
         return true;
      } else {
         AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
         return aura != null && aura.getCurrentTarget() instanceof PlayerEntity target && target.isAlive();
      }
   }

   static int selectPotionIndex(boolean[] selected, boolean[] active, int[] slots) {
      if (selected.length == active.length && selected.length == slots.length) {
         for (int index = 0; index < selected.length; index++) {
            if (selected[index] && !active[index] && slots[index] >= 0) {
               return index;
            }
         }

         return -1;
      } else {
         throw new IllegalArgumentException("Potion state arrays must have equal lengths");
      }
   }

   @Environment(EnvType.CLIENT)
   private static record PotionChoice(AutoPotionFeature.PotionKind kind, int containerSlot) {
   }

   @Environment(EnvType.CLIENT)
   private static enum PotionKind {
      FIRE_RESISTANCE("Fire Resistance", StatusEffects.FIRE_RESISTANCE),
      STRENGTH("Strength", StatusEffects.STRENGTH),
      SPEED("Speed", StatusEffects.SPEED);

      private final String settingName;
      private final RegistryEntry<StatusEffect> effect;

      private PotionKind(String settingName, RegistryEntry<StatusEffect> effect) {
         this.settingName = settingName;
         this.effect = effect;
      }

      String settingName() {
         return this.settingName;
      }

      RegistryEntry<StatusEffect> effect() {
         return this.effect;
      }
   }
}
