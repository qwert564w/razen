package org.ryzen.feature.impl.pve;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.item.consume.ConsumeEffect;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.impl.combat.AuraFeature;
import org.ryzen.feature.impl.misc.DonItems;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.utils.inventory.DropAllInventoryController;
import org.ryzen.utils.inventory.InventorySwap;

@Environment(EnvType.CLIENT)
public final class AutoUseFeature extends PveFeature {
   public static final String AUTO_EAT = "Auto Eat";
   public static final String AUTO_INVISIBILITY = "Auto Invisibility";
   private static final long INVISIBILITY_RETRY_NANOS = 4000000000L;
   public final MultiSelectSetting features = this.register(new MultiSelectSetting("Features", Set.of("Auto Eat"), "Auto Eat", "Auto Invisibility"));
   public final BooleanSetting ignoreGoldenApples = this.register(
      new BooleanSetting("Ignore Golden Apples", false).visibleWhen(() -> this.features.isSelected("Auto Eat"))
   );
   public final BooleanSetting ignoreEnchantedGoldenApples = this.register(
      new BooleanSetting("Ignore Enchanted Golden Apples", false).visibleWhen(() -> this.features.isSelected("Auto Eat"))
   );
   private final ConsumableUseController useController = new ConsumableUseController();
   private AutoUseFeature.Action activeAction;
   private boolean rotationApplied;
   private long invisibilityRetryAt;

   public AutoUseFeature() {
      super("AutoUse", "Automatically eats food and maintains invisibility", -1, AutomationPriority.BACKGROUND);
   }

   public boolean isEating() {
      return this.activeAction == AutoUseFeature.Action.EATING && this.useController.isActive();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (this.useController.isActive()) {
         if (isWorldUsable(client, player) && !this.combatActive()) {
            this.keepThrowableRotation(player);
            if (!this.useController.tick(client, player)) {
               if (this.activeAction == AutoUseFeature.Action.INVISIBILITY) {
                  this.invisibilityRetryAt = System.nanoTime() + 4000000000L;
               }

               this.finishTransaction();
            }
         } else {
            this.cancelActive(client, player);
         }
      } else if (isWorldUsable(client, player)
         && !this.combatActive()
         && client.currentScreen == null
         && player.currentScreenHandler == player.playerScreenHandler
         && player.playerScreenHandler.getCursorStack().isEmpty()
         && !player.isUsingItem()
         && !client.options.useKey.isPressed()
         && !DropAllInventoryController.blocksInventoryOperations()
         && !InventorySwap.isBusy()) {
         if (!this.features.isSelected("Auto Eat") || !player.getHungerManager().isNotFull() || !this.tryAutoEat(client, player)) {
            if (this.features.isSelected("Auto Invisibility")) {
               this.tryAutoInvisibility(client, player);
            }
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.cancelActive(MinecraftClient.getInstance(), MinecraftClient.getInstance().player);
      this.invisibilityRetryAt = 0L;
   }

   @Override
   protected void onPveDisable() {
      this.cancelActive(MinecraftClient.getInstance(), MinecraftClient.getInstance().player);
      this.invisibilityRetryAt = 0L;
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelActive(MinecraftClient.getInstance(), MinecraftClient.getInstance().player);
   }

   private boolean tryAutoEat(MinecraftClient client, ClientPlayerEntity player) {
      List<ConsumableSelector.Candidate<ItemStack>> candidates = ConsumableInventory.collect(player, AutoUseFeature::foodProfile)
         .stream()
         .filter(candidate -> !player.getItemCooldownManager().isCoolingDown(candidate.value()))
         .toList();
      Optional<ConsumableSelector.Candidate<ItemStack>> selected = ConsumableSelector.selectFood(
         candidates, this.ignoreGoldenApples.getValue(), this.ignoreEnchantedGoldenApples.getValue()
      );
      return selected.isPresent() && this.startUse(client, player, selected.get(), AutoUseFeature.Action.EATING);
   }

   private boolean tryAutoInvisibility(MinecraftClient client, ClientPlayerEntity player) {
      long now = System.nanoTime();
      if (now >= this.invisibilityRetryAt
         && player.isOnGround()
         && player.age > 100
         && !player.hasStatusEffect(StatusEffects.INVISIBILITY)
         && !player.hasStatusEffect(StatusEffects.GLOWING)) {
         Optional<ConsumableSelector.Candidate<ItemStack>> selected = ConsumableInventory.collect(player, AutoUseFeature::invisibilityProfile)
            .stream()
            .filter(candidate -> !player.getItemCooldownManager().isCoolingDown(candidate.value()))
            .min(
               Comparator.<ConsumableSelector.Candidate<ItemStack>>comparingInt(candidate -> locationRank(candidate.location()))
                  .thenComparingInt(ConsumableSelector.Candidate::containerSlot)
            );
         if (selected.isEmpty()) {
            return false;
         } else {
            boolean started = this.startUse(client, player, selected.get(), AutoUseFeature.Action.INVISIBILITY);
            if (started) {
               this.invisibilityRetryAt = now + 4000000000L;
            }

            return started;
         }
      } else {
         return false;
      }
   }

   private boolean startUse(MinecraftClient client, ClientPlayerEntity player, ConsumableSelector.Candidate<ItemStack> selected, AutoUseFeature.Action action) {
      boolean throwable = action == AutoUseFeature.Action.INVISIBILITY && isThrowablePotion(selected.value());
      boolean rotateThrowable = throwable && PveManagerFeature.INSTANCE.rotate.getValue();
      if (rotateThrowable && RotationContext.isActive()) {
         return false;
      } else {
         boolean swappedUse = selected.location() != ConsumableSelector.Location.OFF_HAND;
         boolean claimed;
         if (rotateThrowable && swappedUse) {
            claimed = this.claim(
               AutomationResource.INVENTORY, new AutomationResource[]{AutomationResource.ROTATION, AutomationResource.MOVEMENT, AutomationResource.SCREEN}
            );
         } else if (rotateThrowable) {
            claimed = this.claim(AutomationResource.INVENTORY, new AutomationResource[]{AutomationResource.ROTATION});
         } else if (swappedUse) {
            claimed = this.claim(AutomationResource.INVENTORY, new AutomationResource[]{AutomationResource.MOVEMENT, AutomationResource.SCREEN});
         } else {
            claimed = this.claim(AutomationResource.INVENTORY, new AutomationResource[0]);
         }

         if (!claimed) {
            return false;
         } else {
            if (rotateThrowable) {
               RotationContext.setRotation(player.getYaw(), 90.0F);
               this.rotationApplied = true;
            }

            boolean heldUse = action == AutoUseFeature.Action.EATING || selected.value().isOf(Items.POTION);
            int directDelay = throwable && selected.location() == ConsumableSelector.Location.OFF_HAND ? 1 : 0;
            if (!this.useController.start(client, player, selected, heldUse, directDelay)) {
               this.finishTransaction();
               return false;
            } else {
               this.activeAction = action;
               return true;
            }
         }
      }
   }

   private void keepThrowableRotation(ClientPlayerEntity player) {
      if (this.rotationApplied && player != null) {
         RotationContext.setRotation(player.getYaw(), 90.0F);
      }
   }

   private void cancelActive(MinecraftClient client, ClientPlayerEntity player) {
      this.useController.cancel(client, player);
      this.finishTransaction();
   }

   private void finishTransaction() {
      if (this.rotationApplied) {
         if (this.owns(AutomationResource.ROTATION) || !PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.ROTATION)) {
            RotationContext.clear();
         }

         this.rotationApplied = false;
      }

      this.activeAction = null;
      PveAutomationCoordinator.INSTANCE.release(this);
   }

   private boolean combatActive() {
      AuraFeature aura = AuraFeature.getMarkerFeature();
      return aura != null && aura.getCurrentTarget() != null
         ? true
         : PveAutomationCoordinator.INSTANCE.isClaimedByOther(this, AutomationResource.COMBAT)
            || PveAutomationCoordinator.INSTANCE.isClaimedByOther(this, AutomationResource.ROTATION);
   }

   private static boolean isWorldUsable(MinecraftClient client, ClientPlayerEntity player) {
      return player != null && client.world != null && client.interactionManager != null && player.isAlive() && !player.isSpectator();
   }

   private static ConsumableInventory.Profile foodProfile(ItemStack stack) {
      FoodComponent food = (FoodComponent)stack.get(DataComponentTypes.FOOD);
      if (food != null && !stack.isOf(Items.DRIED_KELP)) {
         ConsumableSelector.Kind kind;
         if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            kind = ConsumableSelector.Kind.ENCHANTED_GOLDEN_APPLE;
         } else if (stack.isOf(Items.GOLDEN_APPLE)) {
            kind = ConsumableSelector.Kind.GOLDEN_APPLE;
         } else {
            kind = ConsumableSelector.Kind.FOOD;
         }

         return new ConsumableInventory.Profile(kind, food.nutrition(), food.saturation(), hasNoHarmfulFoodEffect(stack));
      } else {
         return null;
      }
   }

   private static ConsumableInventory.Profile invisibilityProfile(ItemStack stack) {
      if (!stack.isOf(Items.POTION) && !stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) {
         return null;
      } else {
         boolean cataloguedInvisibility = DonItems.FunTime.ENHANCED_INVISIBILITY_POTION.matches(stack);
         PotionContentsComponent contents = (PotionContentsComponent)stack.get(DataComponentTypes.POTION_CONTENTS);
         int effectCount = 0;
         boolean invisibility = false;
         if (contents != null) {
            for (StatusEffectInstance effect : contents.getEffects()) {
               effectCount++;
               invisibility |= effect.equals(StatusEffects.INVISIBILITY);
            }
         }

         return cataloguedInvisibility || invisibility && effectCount == 1
            ? new ConsumableInventory.Profile(ConsumableSelector.Kind.INVISIBILITY_POTION, 0, 0.0F, true)
            : null;
      }
   }

   private static boolean hasNoHarmfulFoodEffect(ItemStack stack) {
      ConsumableComponent consumable = (ConsumableComponent)stack.get(DataComponentTypes.CONSUMABLE);
      if (consumable == null) {
         return true;
      } else {
         for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (effect instanceof ApplyEffectsConsumeEffect) {
               ApplyEffectsConsumeEffect statusEffects = (ApplyEffectsConsumeEffect)effect;

               for (StatusEffectInstance statusEffect : statusEffects.effects()) {
                  if (!((StatusEffect)statusEffect.getEffectType().value()).isBeneficial()) {
                     return false;
                  }
               }
            }
         }

         return true;
      }
   }

   private static boolean isThrowablePotion(ItemStack stack) {
      return stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION);
   }

   private static int locationRank(ConsumableSelector.Location location) {
      return switch (location) {
         case OFF_HAND -> 0;
         case MAIN_HAND -> 1;
         case HOTBAR -> 2;
         case INVENTORY -> 3;
      };
   }

   @Environment(EnvType.CLIENT)
   private static enum Action {
      EATING,
      INVISIBILITY;
   }
}
