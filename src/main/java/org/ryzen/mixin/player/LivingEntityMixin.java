package org.ryzen.mixin.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventManager;
import org.ryzen.event.Events;
import org.ryzen.event.events.game.PlayerJumpEvent;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.visual.RemovalsFeature;
import org.ryzen.feature.impl.visual.SwingAnimationFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({LivingEntity.class})
public abstract class LivingEntityMixin {
   @Inject(
      method = {"jump"},
      at = {@At("HEAD")}
   )
   private void onPlayerJump(CallbackInfo ci) {
      ClientPlayerEntity player = MinecraftContext.mc.player;
      if (player != null && (Object)this == player && EventManager.hasListeners(PlayerJumpEvent.class)) {
         EventManager.call(Events.PLAYER_JUMP.set(player, player.getEntityPos()));
      }
   }

   @Inject(
      method = {"getEffectFadeFactor"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetEffectBlendFactor(RegistryEntry<StatusEffect> effect, float partialTick, CallbackInfoReturnable<Float> cir) {
      if ((Object)this == MinecraftContext.mc.player && RemovalsFeature.shouldRemoveBadEffectsVisuals()) {
         cir.setReturnValue(0.0F);
      }
   }

   @Inject(
      method = {"getHandSwingDuration"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void customSwingDuration(CallbackInfoReturnable<Integer> cir) {
      if ((Object)this == MinecraftContext.mc.player) {
         SwingAnimationFeature swing = FeatureManager.INSTANCE.getEnabled(SwingAnimationFeature.class);
         if (swing != null) {
            cir.setReturnValue(swing.swingDurationTicks());
         }
      }
   }
}
