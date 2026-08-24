package org.ryzen.mixin.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.ryzen.event.EventManager;
import org.ryzen.event.Events;
import org.ryzen.event.events.screen.ScreenCloseEvent;
import org.ryzen.event.events.screen.ScreenKeyEvent;
import org.ryzen.event.events.screen.ScreenRenderEvent;
import org.ryzen.menu.core.MenuOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({Screen.class})
public abstract class ScreenMixin {
   @Shadow
   @Final
   protected MinecraftClient client;

   @Inject(
      method = {"close"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onClose(CallbackInfo ci) {
      if (EventManager.hasListeners(ScreenCloseEvent.class)) {
         if (EventManager.call(Events.SCREEN_CLOSE.set(this.client, (Screen)(Object)this)).isCancelled()) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRender(DrawContext guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if (EventManager.hasListeners(ScreenRenderEvent.class)) {
         if (EventManager.call(Events.SCREEN_RENDER.set((Screen)(Object)this, guiGraphics, mouseX, mouseY, partialTick)).isCancelled()) {
            ci.cancel();
         }
      }
   }

   @ModifyVariable(
      method = {"render"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private int maskMouseXWhenOverlayOpen(int mouseX) {
      return MenuOverlay.isOpen() ? -536870912 : mouseX;
   }

   @ModifyVariable(
      method = {"render"},
      at = @At("HEAD"),
      ordinal = 1,
      argsOnly = true
   )
   private int maskMouseYWhenOverlayOpen(int mouseY) {
      return MenuOverlay.isOpen() ? -536870912 : mouseY;
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPressed(KeyInput keyEvent, CallbackInfoReturnable<Boolean> cir) {
      if (EventManager.hasListeners(ScreenKeyEvent.class)) {
         if (EventManager.call(Events.SCREEN_KEY.set((Screen)(Object)this, keyEvent, ScreenKeyEvent.Action.PRESS)).isCancelled()) {
            cir.setReturnValue(true);
         }
      }
   }
}
