package org.ryzen.mixin.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import net.minecraft.util.thread.ThreadExecutor;
import org.ryzen.context.RenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ThreadExecutor.class})
public abstract class BlockableEventLoopMixin {
   @Inject(
      method = {"runTasks()V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRunAllTasks(CallbackInfo ci) {
      if (RenderContext.overlayStartTime > -1L) {
         long elapsed = Util.getMeasuringTimeMs() - RenderContext.overlayStartTime;
         MinecraftClient mc = MinecraftClient.getInstance();
         boolean isStartup = mc.currentScreen == null;
         long maxDeferTime = isStartup ? 3000L : 1800L;
         if (elapsed < maxDeferTime) {
            ci.cancel();
         }
      }
   }
}
