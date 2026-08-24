package org.ryzen.mixin.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.ryzen.feature.impl.visual.RemovalsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({BossBarHud.class})
public abstract class BossHealthOverlayMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRender(DrawContext guiGraphics, CallbackInfo ci) {
      if (RemovalsFeature.shouldRemoveBossBar()) {
         ci.cancel();
      }
   }
}
