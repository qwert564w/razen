package org.ryzen.mixin.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.ryzen.utils.render.ClientCape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({AbstractClientPlayerEntity.class})
public abstract class AbstractClientPlayerMixin {
   @Inject(
      method = {"getSkin"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void applyCape(CallbackInfoReturnable<SkinTextures> cir) {
      AbstractClientPlayerEntity player = (AbstractClientPlayerEntity)(Object)this;
      if (ClientCape.shouldForceCape(player.getUuid())) {
         cir.setReturnValue(ClientCape.apply((SkinTextures)cir.getReturnValue()));
      }
   }
}
