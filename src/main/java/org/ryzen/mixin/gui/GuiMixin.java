package org.ryzen.mixin.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Overlay;
import org.ryzen.context.RenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({MinecraftClient.class})
public abstract class GuiMixin {
   @Inject(
      method = {"setOverlay"},
      at = {@At("HEAD")}
   )
   private void onSetOverlay(Overlay overlay, CallbackInfo ci) {
      if (overlay == null) {
         RenderContext.overlayStartTime = -1L;
      }
   }
}
