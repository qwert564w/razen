package org.ryzen.mixin.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventManager;
import org.ryzen.event.Events;
import org.ryzen.event.events.game.AttackEvent;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.game.TickContext;
import org.ryzen.event.events.lifecycle.ClientStartEvent;
import org.ryzen.event.events.lifecycle.DisconnectEvent;
import org.ryzen.event.events.lifecycle.ResourceReloadEvent;
import org.ryzen.event.events.lifecycle.ShutdownEvent;
import org.ryzen.event.events.lifecycle.WorldJoinEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.screen.ScreenOpenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({MinecraftClient.class})
public abstract class MinecraftMixin {
   private static final TickContext TICK_CONTEXT = new TickContext();
   @Unique
   private ClientWorld previousLevel;
   @Shadow
   public ClientWorld world;

   @Inject(
      method = {"collectLoadTimes(Lnet/minecraft/client/MinecraftClient$LoadingContext;)V"},
      at = {@At("TAIL")}
   )
   private void onClientStart(CallbackInfo ci) {
      if (EventManager.hasListeners(ClientStartEvent.class)) {
         EventManager.call(Events.CLIENT_START.set((MinecraftClient)(Object)this));
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void onTick(CallbackInfo ci) {
      if (EventManager.hasListeners(GameTickEvent.class)) {
         MinecraftClient client = (MinecraftClient)(Object)this;
         EventManager.call(Events.GAME_TICK.set(client, TICK_CONTEXT.begin(client)));
      }
   }

   @Inject(
      method = {"doAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttack(CallbackInfoReturnable<Boolean> cir) {
      if (EventManager.hasListeners(AttackEvent.class)) {
         if (EventManager.call(Events.ATTACK.set((MinecraftClient)(Object)this)).isCancelled()) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(
      method = {"joinWorld"},
      at = {@At("HEAD")}
   )
   private void capturePreviousLevel(ClientWorld level, CallbackInfo ci) {
      this.previousLevel = this.world;
   }

   @Inject(
      method = {"joinWorld"},
      at = {@At("TAIL")}
   )
   private void onSetLevel(ClientWorld level, CallbackInfo ci) {
      MinecraftClient client = (MinecraftClient)(Object)this;
      ClientWorld previous = this.previousLevel;
      if (level == null) {
         RotationContext.clear();
      }

      if (previous == null && level != null && EventManager.hasListeners(WorldJoinEvent.class)) {
         EventManager.call(Events.WORLD_JOIN.set(client, level));
      }

      if (previous != null && level == null && EventManager.hasListeners(WorldLeaveEvent.class)) {
         EventManager.call(Events.WORLD_LEAVE.set(client, previous));
      }
   }

   @Inject(
      method = {"disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V"},
      at = {@At("HEAD")}
   )
   private void onDisconnect(Screen screen, boolean transferring, boolean resetting, CallbackInfo ci) {
      if (EventManager.hasListeners(DisconnectEvent.class)) {
         EventManager.call(Events.DISCONNECT.set((MinecraftClient)(Object)this, screen, transferring, resetting));
      }
   }

   @Inject(
      method = {"setScreenAndRender"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSetScreen(Screen screen, CallbackInfo ci) {
      MinecraftClient client = (MinecraftClient)(Object)this;
      if (screen != null && EventManager.hasListeners(ScreenOpenEvent.class)) {
         if (EventManager.call(Events.SCREEN_OPEN.set(client, screen)).isCancelled()) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"scheduleStop"},
      at = {@At("HEAD")}
   )
   private void onShutdown(CallbackInfo ci) {
      if (EventManager.hasListeners(ShutdownEvent.class)) {
         EventManager.call(Events.SHUTDOWN.set((MinecraftClient)(Object)this));
      }
   }

   @Inject(
      method = {"onFinishedLoading(Lnet/minecraft/client/MinecraftClient$LoadingContext;)V"},
      at = {@At("TAIL")}
   )
   private void onResourceReload(CallbackInfo ci) {
      if (EventManager.hasListeners(ResourceReloadEvent.class)) {
         EventManager.call(Events.RESOURCE_RELOAD.set((MinecraftClient)(Object)this));
      }
   }
}
