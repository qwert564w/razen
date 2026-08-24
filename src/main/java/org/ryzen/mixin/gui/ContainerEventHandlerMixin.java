package org.ryzen.mixin.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.ryzen.event.EventManager;
import org.ryzen.event.Events;
import org.ryzen.event.events.screen.ScreenKeyEvent;
import org.ryzen.event.events.screen.ScreenMouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({ParentElement.class})
public interface ContainerEventHandlerMixin {
   @Inject(
      method = {"keyReleased"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyReleased(KeyInput keyEvent, CallbackInfoReturnable<Boolean> cir) {
      if (this instanceof Screen screen && EventManager.hasListeners(ScreenKeyEvent.class)) {
         if (EventManager.call(Events.SCREEN_KEY.set(screen, keyEvent, ScreenKeyEvent.Action.RELEASE)).isCancelled()) {
            cir.setReturnValue(true);
         }

         return;
      }
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseClicked(Click mouseButtonEvent, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
      if (this instanceof Screen screen && EventManager.hasListeners(ScreenMouseButtonEvent.class)) {
         if (EventManager.call(Events.SCREEN_MOUSE_BUTTON.set(screen, mouseButtonEvent, ScreenMouseButtonEvent.Action.CLICK, 0.0, 0.0)).isCancelled()) {
            cir.setReturnValue(true);
         }

         return;
      }
   }

   @Inject(
      method = {"mouseReleased"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseReleased(Click mouseButtonEvent, CallbackInfoReturnable<Boolean> cir) {
      if (this instanceof Screen screen && EventManager.hasListeners(ScreenMouseButtonEvent.class)) {
         if (EventManager.call(Events.SCREEN_MOUSE_BUTTON.set(screen, mouseButtonEvent, ScreenMouseButtonEvent.Action.RELEASE, 0.0, 0.0)).isCancelled()) {
            cir.setReturnValue(true);
         }

         return;
      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseDragged(Click mouseButtonEvent, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
      if (this instanceof Screen screen && EventManager.hasListeners(ScreenMouseButtonEvent.class)) {
         if (EventManager.call(Events.SCREEN_MOUSE_BUTTON.set(screen, mouseButtonEvent, ScreenMouseButtonEvent.Action.DRAG, dragX, dragY)).isCancelled()) {
            cir.setReturnValue(true);
         }

         return;
      }
   }
}
