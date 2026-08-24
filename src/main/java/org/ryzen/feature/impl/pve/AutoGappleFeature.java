package org.ryzen.feature.impl.pve;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.utils.inventory.DropAllInventoryController;
import org.ryzen.utils.inventory.InventorySwap;

@Environment(EnvType.CLIENT)
public final class AutoGappleFeature extends PveFeature {
   private static final int RETRY_GUARD_TICKS = 10;
   public final NumberSetting health = this.register(new NumberSetting("Health", 15.0, 4.0, 20.0, 0.05, " HP"));
   public final BooleanSetting goldenApples = this.register(new BooleanSetting("Golden Apples", true));
   public final BooleanSetting enchantedGoldenApples = this.register(new BooleanSetting("Enchanted Golden Apples", true));
   private final ConsumableUseController useController = new ConsumableUseController();
   private int retryAfterTick;

   public AutoGappleFeature() {
      super("AutoGapple", "Eats the strongest allowed golden apple at low health", -1, AutomationPriority.EMERGENCY);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (this.useController.isActive()) {
         if (!isWorldUsable(client, player)) {
            this.cancelActive(client, player);
         } else if (!this.useController.tick(client, player)) {
            this.finishTransaction();
            this.retryAfterTick = player.age + 10;
         }
      } else if (isWorldUsable(client, player)
         && player.age >= this.retryAfterTick
         && client.currentScreen == null
         && player.currentScreenHandler == player.playerScreenHandler
         && player.playerScreenHandler.getCursorStack().isEmpty()
         && !player.isUsingItem()
         && !client.options.useKey.isPressed()
         && !DropAllInventoryController.blocksInventoryOperations()
         && !InventorySwap.isBusy()
         && !(effectiveHealth(player) > this.health.getValue())) {
         List<ConsumableSelector.Candidate<ItemStack>> candidates = ConsumableInventory.collect(player, AutoGappleFeature::appleProfile)
            .stream()
            .filter(candidate -> !player.getItemCooldownManager().isCoolingDown(candidate.value()))
            .toList();
         Optional<ConsumableSelector.Candidate<ItemStack>> selected = ConsumableSelector.selectApple(
            candidates, this.goldenApples.getValue(), this.enchantedGoldenApples.getValue()
         );
         selected.ifPresent(candidate -> this.startUse(client, player, (ConsumableSelector.Candidate<ItemStack>)candidate));
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.cancelActive(MinecraftClient.getInstance(), MinecraftClient.getInstance().player);
      this.retryAfterTick = 0;
   }

   @Override
   protected void onPveDisable() {
      this.cancelActive(MinecraftClient.getInstance(), MinecraftClient.getInstance().player);
      this.retryAfterTick = 0;
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelActive(MinecraftClient.getInstance(), MinecraftClient.getInstance().player);
   }

   private void startUse(MinecraftClient client, ClientPlayerEntity player, ConsumableSelector.Candidate<ItemStack> selected) {
      boolean claimed = selected.location() == ConsumableSelector.Location.OFF_HAND
         ? this.claim(AutomationResource.INVENTORY, new AutomationResource[0])
         : this.claim(AutomationResource.INVENTORY, new AutomationResource[]{AutomationResource.MOVEMENT, AutomationResource.SCREEN});
      if (claimed) {
         if (!this.useController.start(client, player, selected, true, 0)) {
            this.finishTransaction();
         }
      }
   }

   private void cancelActive(MinecraftClient client, ClientPlayerEntity player) {
      this.useController.cancel(client, player);
      this.finishTransaction();
   }

   private void finishTransaction() {
      PveAutomationCoordinator.INSTANCE.release(this);
   }

   private static ConsumableInventory.Profile appleProfile(ItemStack stack) {
      if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
         return new ConsumableInventory.Profile(ConsumableSelector.Kind.ENCHANTED_GOLDEN_APPLE, 0, 0.0F, true);
      } else {
         return stack.isOf(Items.GOLDEN_APPLE) ? new ConsumableInventory.Profile(ConsumableSelector.Kind.GOLDEN_APPLE, 0, 0.0F, true) : null;
      }
   }

   private static double effectiveHealth(ClientPlayerEntity player) {
      return (double)(player.getHealth() + player.getAbsorptionAmount());
   }

   private static boolean isWorldUsable(MinecraftClient client, ClientPlayerEntity player) {
      return player != null && client.world != null && client.interactionManager != null && player.isAlive() && !player.isSpectator();
   }
}
