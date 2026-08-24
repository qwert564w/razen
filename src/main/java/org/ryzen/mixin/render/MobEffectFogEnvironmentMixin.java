package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.ryzen.feature.impl.visual.RemovalsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({StatusEffectFogModifier.class})
public abstract class MobEffectFogEnvironmentMixin {
   @Shadow
   public abstract RegistryEntry<StatusEffect> getStatusEffect();

   @Inject(
      method = {"shouldApply"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsApplicable(CameraSubmersionType fogType, Entity entity, CallbackInfoReturnable<Boolean> cir) {
      RegistryEntry<StatusEffect> effect = this.getStatusEffect();
      if (RemovalsFeature.shouldRemoveBadEffectsVisuals() && (effect == StatusEffects.DARKNESS || effect == StatusEffects.BLINDNESS)) {
         cir.setReturnValue(false);
      }
   }
}
