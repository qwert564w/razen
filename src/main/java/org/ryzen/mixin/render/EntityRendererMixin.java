package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.ryzen.feature.impl.visual.NameTagsFeature;
import org.ryzen.utils.text.NameProtectUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({EntityRenderer.class})
public abstract class EntityRendererMixin {
   @Inject(
      method = {"getDisplayName"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void protectNameTag(Entity entity, CallbackInfoReturnable<Text> cir) {
      if (entity instanceof PlayerEntity && NameTagsFeature.shouldHideVanillaTag()) {
         cir.setReturnValue(null);
      } else {
         cir.setReturnValue(NameProtectUtil.protect((Text)cir.getReturnValue()));
      }
   }
}
