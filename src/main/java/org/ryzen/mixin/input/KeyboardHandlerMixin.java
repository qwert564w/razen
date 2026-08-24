package org.ryzen.mixin.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.ryzen.event.EventManager;
import org.ryzen.event.events.input.CharacterInputEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({Keyboard.class})
public abstract class KeyboardHandlerMixin {
   @Inject(
      method = {"onChar"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCharacterTyped(long window, CharInput characterEvent, CallbackInfo ci) {
      if (characterEvent.isValidChar() && EventManager.hasListeners(CharacterInputEvent.class)) {
         CharacterInputEvent event = EventManager.call(new CharacterInputEvent(window, characterEvent.codepoint()));
         if (event.isCancelled()) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"onKey"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPress(long window, int action, KeyInput keyEvent, CallbackInfo ci) {
      if (EventManager.hasListeners(KeyboardInputEvent.class)) {
         KeyboardInputEvent event = EventManager.call(new KeyboardInputEvent(window, keyEvent.key(), keyEvent.scancode(), action, keyEvent.modifiers()));
         if (event.isCancelled()) {
            ci.cancel();
         }
      }
   }
}
