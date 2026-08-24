package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.combat.AuraFeature;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;

@Environment(EnvType.CLIENT)
public final class AutoJumpFeature extends Feature {
   public final BooleanSetting aura = this.register(new BooleanSetting("Aura", true));
   public final BooleanSetting negativeEffects = this.register(new BooleanSetting("Negative Effects", true));

   public AutoJumpFeature() {
      super("AutoJump", "Automatically jumps from ground", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && event.getClient().world != null) {
         if (!PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.MOVEMENT)
            && !PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.NAVIGATION)) {
            if (player.isOnGround() && !event.getClient().options.jumpKey.isPressed()) {
               if (!player.getAbilities().flying && !player.isGliding()) {
                  if (!player.isTouchingWater() && !player.isInLava() && !player.isClimbing()) {
                     if (this.shouldJumpForAura(player) || this.shouldJumpForNegativeEffects(player)) {
                        player.jump();
                     }
                  }
               }
            }
         }
      }
   }

   private boolean shouldJumpForAura(ClientPlayerEntity player) {
      if (!this.aura.getValue()) {
         return false;
      } else {
         AuraFeature aura = FeatureManager.INSTANCE.getFeature(AuraFeature.class);
         return aura != null && aura.shouldAutoJump(player);
      }
   }

   private boolean shouldJumpForNegativeEffects(ClientPlayerEntity player) {
      return !this.negativeEffects.getValue() ? false : player.input.getMovementInput().lengthSquared() > 0.0F && this.hasNegativeEffects(player);
   }

   private boolean hasNegativeEffects(ClientPlayerEntity player) {
      for (StatusEffectInstance effect : player.getStatusEffects()) {
         if (((StatusEffect)effect.getEffectType().value()).getCategory() == StatusEffectCategory.HARMFUL) {
            return true;
         }
      }

      return false;
   }
}
