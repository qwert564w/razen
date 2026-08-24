package org.ryzen.mixin.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.ryzen.event.EventManager;
import org.ryzen.event.Events;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.feature.impl.movement.NoPushFeature;
import org.ryzen.feature.impl.movement.NoSlowFeature;
import org.ryzen.utils.combat.LocalPlayerHistory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({ClientPlayerEntity.class})
public abstract class LocalPlayerMixin {
   @Shadow
   @Final
   private MinecraftClient client;

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void onTickPre(CallbackInfo ci) {
      if (EventManager.hasListeners(PlayerTickEvent.class)) {
         EventManager.call(Events.PLAYER_TICK.set((ClientPlayerEntity)(Object)this, PlayerTickEvent.Phase.PRE));
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void onTickPost(CallbackInfo ci) {
      ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
      LocalPlayerHistory.record(player);
      if (EventManager.hasListeners(PlayerTickEvent.class)) {
         EventManager.call(Events.PLAYER_TICK.set(player, PlayerTickEvent.Phase.POST));
      }
   }

   @Inject(
      method = {"pushOutOfBlocks(DD)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelClosestSpacePush(double x, double z, CallbackInfo ci) {
      if (NoPushFeature.shouldCancelClosestSpacePush((ClientPlayerEntity)(Object)this)) {
         ci.cancel();
      }
   }

   @Redirect(
      method = {"tickMovementInput"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"
      )
   )
   private float useCameraPitchForHandBob(ClientPlayerEntity player) {
      return this.client.options.getPerspective().isFirstPerson() ? this.client.gameRenderer.getCamera().getPitch() : player.getPitch();
   }

   @Redirect(
      method = {"tickMovementInput"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"
      )
   )
   private float useCameraYawForHandBob(ClientPlayerEntity player) {
      return this.client.options.getPerspective().isFirstPerson() ? this.client.gameRenderer.getCamera().getYaw() : player.getYaw();
   }

   @Inject(
      method = {"getActiveItemSpeedMultiplier()F"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ryzen$keepSpeedWhileUsing(CallbackInfoReturnable<Float> cir) {
      NoSlowFeature noSlow = NoSlowFeature.getEnabled();
      if (noSlow != null && noSlow.shouldKeepSpeed((ClientPlayerEntity)(Object)this)) {
         cir.setReturnValue(1.0F);
      }
   }
}
