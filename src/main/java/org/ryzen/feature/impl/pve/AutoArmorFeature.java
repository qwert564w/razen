package org.ryzen.feature.impl.pve;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.utils.inventory.DropAllInventoryController;
import org.ryzen.utils.inventory.InventorySwap;

@Environment(EnvType.CLIENT)
public final class AutoArmorFeature extends PveFeature implements MinecraftContext {
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   public final NumberSetting swapDelay = this.register(new NumberSetting("Swap Delay", 100.0, 0.0, 1000.0, 25.0, "ms"));
   private long lastSwapNanos;

   public AutoArmorFeature() {
      super("AutoArmor", "Equips the strongest armor in your inventory", -1, AutomationPriority.FEATURE);
   }

   @Override
   protected void onPveEnable() {
      this.lastSwapNanos = 0L;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (this.canManageInventory(player) && !InventorySwap.isBusy() && this.delayElapsed(System.nanoTime())) {
         List<AutoArmorFeature.Upgrade> upgrades = this.findUpgrades(player);
         if (!upgrades.isEmpty() && this.claim(AutomationResource.INVENTORY, new AutomationResource[0])) {
            boolean swapped = false;

            try {
               if (this.canManageInventory(player) && !InventorySwap.isBusy()) {
                  for (AutoArmorFeature.Upgrade upgrade : upgrades) {
                     swapped |= this.equipUpgrade(player, upgrade);
                  }
               }
            } finally {
               PveAutomationCoordinator.INSTANCE.release(this);
            }

            if (swapped) {
               this.lastSwapNanos = System.nanoTime();
            }
         }
      }
   }

   private boolean canManageInventory(ClientPlayerEntity player) {
      return player != null
         && player.isAlive()
         && mc.interactionManager != null
         && player.currentScreenHandler == player.playerScreenHandler
         && player.playerScreenHandler.getCursorStack().isEmpty()
         && !DropAllInventoryController.blocksInventoryOperations()
         && (mc.currentScreen == null || mc.currentScreen instanceof InventoryScreen);
   }

   private boolean delayElapsed(long nowNanos) {
      long delayNanos = (long)(this.swapDelay.getValue() * 1000000.0);
      return this.lastSwapNanos == 0L || nowNanos - this.lastSwapNanos >= delayNanos;
   }

   private List<AutoArmorFeature.Upgrade> findUpgrades(ClientPlayerEntity player) {
      List<AutoArmorFeature.Upgrade> upgrades = new ArrayList<>(ARMOR_SLOTS.length);

      for (EquipmentSlot equipmentSlot : ARMOR_SLOTS) {
         int armorSlot = armorMenuSlot(equipmentSlot);
         ItemStack equipped = player.playerScreenHandler.getSlot(armorSlot).getStack();
         if (!hasBindingCurse(equipped)) {
            double equippedScore = equipped.isEmpty() ? Double.NEGATIVE_INFINITY : armorScore(equipped);
            List<AutoArmorFeature.ScoredSlot> candidates = new ArrayList<>();

            for (int slot = 9; slot < 45; slot++) {
               ItemStack candidate = player.playerScreenHandler.getSlot(slot).getStack();
               if (isArmorFor(candidate, equipmentSlot) && !hasBindingCurse(candidate)) {
                  candidates.add(new AutoArmorFeature.ScoredSlot(slot, armorScore(candidate)));
               }
            }

            OptionalInt best = selectBestUpgrade(equippedScore, candidates);
            if (best.isPresent()) {
               upgrades.add(new AutoArmorFeature.Upgrade(best.getAsInt(), armorSlot, equipmentSlot));
            }
         }
      }

      return upgrades;
   }

   private boolean equipUpgrade(ClientPlayerEntity player, AutoArmorFeature.Upgrade upgrade) {
      if (player.playerScreenHandler.isValid(upgrade.sourceSlot())
         && player.playerScreenHandler.isValid(upgrade.armorSlot())
         && player.playerScreenHandler.getCursorStack().isEmpty()) {
         ItemStack candidate = player.playerScreenHandler.getSlot(upgrade.sourceSlot()).getStack();
         ItemStack equipped = player.playerScreenHandler.getSlot(upgrade.armorSlot()).getStack();
         if (isArmorFor(candidate, upgrade.equipmentSlot())
            && !hasBindingCurse(candidate)
            && !hasBindingCurse(equipped)
            && (equipped.isEmpty() || !(armorScore(candidate) <= armorScore(equipped)))) {
            this.click(player, upgrade.sourceSlot());
            if (player.playerScreenHandler.getCursorStack().isEmpty()) {
               return false;
            } else {
               this.click(player, upgrade.armorSlot());
               if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
                  this.click(player, upgrade.sourceSlot());
               }

               return player.playerScreenHandler.getCursorStack().isEmpty();
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void click(ClientPlayerEntity player, int slot) {
      mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, player);
   }

   private static int armorMenuSlot(EquipmentSlot equipmentSlot) {
      return switch (equipmentSlot) {
         case HEAD -> 5;
         case CHEST -> 6;
         case LEGS -> 7;
         case FEET -> 8;
         default -> throw new IllegalArgumentException("Not a humanoid armor slot: " + equipmentSlot);
      };
   }

   private static boolean isArmorFor(ItemStack stack, EquipmentSlot equipmentSlot) {
      if (stack.isEmpty()) {
         return false;
      } else {
         EquippableComponent equippable = (EquippableComponent)stack.get(DataComponentTypes.EQUIPPABLE);
         return equippable != null && equippable.slot() == equipmentSlot;
      }
   }

   private static boolean hasBindingCurse(ItemStack stack) {
      return enchantmentLevel(stack, "binding_curse") > 0;
   }

   private static double armorScore(ItemStack stack) {
      double armor = 0.0;
      double toughness = 0.0;
      AttributeModifiersComponent modifiers = (AttributeModifiersComponent)stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
      if (modifiers != null) {
         for (net.minecraft.component.type.AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(EntityAttributes.ARMOR)) {
               armor += entry.modifier().value();
            } else if (entry.attribute().equals(EntityAttributes.ARMOR_TOUGHNESS)) {
               toughness += entry.modifier().value();
            }
         }
      }

      return score(armor, toughness, enchantmentLevel(stack, "protection"), enchantmentLevel(stack, "unbreaking"), enchantmentLevel(stack, "mending"));
   }

   private static int enchantmentLevel(ItemStack stack, String path) {
      if (stack.isEmpty()) {
         return 0;
      } else {
         for (Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            Optional<RegistryKey<Enchantment>> key = ((RegistryEntry)entry.getKey()).getKey();
            if (key.isPresent() && key.get().getValue().getPath().equals(path)) {
               return entry.getIntValue();
            }
         }

         return 0;
      }
   }

   static double score(double armor, double toughness, int protection, int unbreaking, int mending) {
      return armor + toughness + (double)protection + (double)unbreaking * 0.1 + (double)mending * 0.2;
   }

   static OptionalInt selectBestUpgrade(double equippedScore, List<AutoArmorFeature.ScoredSlot> candidates) {
      int bestSlot = -1;
      double bestScore = equippedScore;

      for (AutoArmorFeature.ScoredSlot candidate : candidates) {
         if (candidate.score() > bestScore) {
            bestScore = candidate.score();
            bestSlot = candidate.slot();
         }
      }

      return bestSlot < 0 ? OptionalInt.empty() : OptionalInt.of(bestSlot);
   }

   @Environment(EnvType.CLIENT)
   static record ScoredSlot(int slot, double score) {
   }

   @Environment(EnvType.CLIENT)
   private static record Upgrade(int sourceSlot, int armorSlot, EquipmentSlot equipmentSlot) {
   }
}
