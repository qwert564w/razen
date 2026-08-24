package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class OffhandGappleFeature extends Feature {
   public final NumberSetting health = this.register(new NumberSetting("Health", 14.0, 1.0, 20.0, 0.5, " HP"));
   public final BooleanSetting countAbsorption = this.register(new BooleanSetting("Golden Hearts", false));
   private boolean holdingUse;

   public OffhandGappleFeature() {
      super("Auto GApple", "Eats the golden apple already held in your offhand at low health", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.release();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.release();
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPost()) {
         ClientPlayerEntity player = event.getPlayer();
         if (player != null && isApple(player.getOffHandStack()) && !(this.effectiveHealth(player) > this.health.getValue())) {
            MinecraftClient client = MinecraftClient.getInstance();
            this.holdingUse = true;
            if (client.currentScreen != null) {
               if (!player.isUsingItem() && client.interactionManager != null) {
                  client.interactionManager.interactItem(player, Hand.OFF_HAND);
               }
            } else {
               client.options.useKey.setPressed(true);
            }
         } else {
            this.release();
         }
      }
   }

   private void release() {
      if (this.holdingUse) {
         this.holdingUse = false;
         MinecraftClient.getInstance().options.useKey.setPressed(false);
      }
   }

   private double effectiveHealth(ClientPlayerEntity player) {
      return (double)(player.getHealth() + (this.countAbsorption.getValue() ? player.getAbsorptionAmount() : 0.0F));
   }

   private static boolean isApple(ItemStack stack) {
      return stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
   }
}
