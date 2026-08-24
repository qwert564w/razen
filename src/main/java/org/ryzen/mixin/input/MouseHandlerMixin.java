package org.ryzen.mixin.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventManager;
import org.ryzen.event.events.input.MouseInputEvent;
import org.ryzen.feature.impl.combat.AimAssistFeature;
import org.ryzen.menu.core.MenuOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({Mouse.class})
public abstract class MouseHandlerMixin {
   @Inject(
      method = {"onMouseButton"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseButton(long window, MouseInput buttonInfo, int action, CallbackInfo ci) {
      if (EventManager.hasListeners(MouseInputEvent.class)) {
         MouseInputEvent event = EventManager.call(new MouseInputEvent(window, buttonInfo.button(), action, buttonInfo.modifiers()));
         if (event.isCancelled()) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"onMouseScroll"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void blockWorldScrollWhileMenuOpen(long window, double horizontal, double vertical, CallbackInfo ci) {
      if (MenuOverlay.blocksInput()) {
         MenuOverlay.handleScroll(vertical);
         ci.cancel();
      }
   }

   @Redirect(
      method = {"updateMouse"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"
      )
   )
   private void onTurnPlayer(ClientPlayerEntity player, double yawDelta, double pitchDelta) {
      if (!RotationContext.onMouseTurn(yawDelta, pitchDelta)) {
         AimAssistFeature assist = AimAssistFeature.getEnabled();
         if (assist != null) {
            player.changeLookDirection(assist.scaleYaw(player, yawDelta), assist.scalePitch(player, pitchDelta));
         } else {
            player.changeLookDirection(yawDelta, pitchDelta);
         }
      }
   }
}
