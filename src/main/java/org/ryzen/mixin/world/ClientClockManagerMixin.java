package org.ryzen.mixin.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.ryzen.feature.impl.visual.WorldTweaksFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({World.class})
public abstract class ClientClockManagerMixin {
   @Inject(
      method = {"getTimeOfDay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetDayTime(CallbackInfoReturnable<Long> cir) {
      if ((Object)this instanceof ClientWorld level && level.getRegistryKey() == World.OVERWORLD) {
         WorldTweaksFeature worldTweaks = WorldTweaksFeature.getEnabled();
         if (worldTweaks != null && worldTweaks.changeTime.getValue()) {
            cir.setReturnValue(worldTweaks.getCustomTime());
         }

         return;
      }
   }
}
