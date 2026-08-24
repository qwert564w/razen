package org.ryzen.mixin.world;

import java.util.Deque;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.ryzen.feature.impl.visual.RemovalsFeature;
import org.ryzen.utils.render.world.DynamicLightManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ClientWorld.class})
public abstract class ClientLevelMixin {
   @Shadow
   @Final
   private Deque<Runnable> chunkUpdaters;
   private boolean hadQueuedLightUpdates;

   @Inject(
      method = {"runQueuedChunkUpdates"},
      at = {@At("HEAD")}
   )
   private void captureQueuedLightUpdates(CallbackInfo ci) {
      this.hadQueuedLightUpdates = !this.chunkUpdaters.isEmpty();
   }

   @Inject(
      method = {"runQueuedChunkUpdates"},
      at = {@At("TAIL")}
   )
   private void restoreDynamicLightsAfterPackets(CallbackInfo ci) {
      if (this.hadQueuedLightUpdates) {
         DynamicLightManager.INSTANCE.revalidateAfterVanillaUpdates((ClientWorld)(Object)this);
      }
   }

   @Inject(
      method = {"playSoundFromEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPlaySeededEntitySound(
      Entity except, Entity sourceEntity, RegistryEntry<SoundEvent> sound, SoundCategory source, float volume, float pitch, long seed, CallbackInfo ci
   ) {
      if (RemovalsFeature.shouldRemoveSound((SoundEvent)sound.value())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"playSound(Lnet/minecraft/entity/Entity;DDDLnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPlaySeededPositionedSound(
      Entity except, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory source, float volume, float pitch, long seed, CallbackInfo ci
   ) {
      if (RemovalsFeature.shouldRemoveSound((SoundEvent)sound.value())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"playSoundFromEntityClient(Lnet/minecraft/entity/Entity;Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPlayLocalEntitySound(Entity sourceEntity, SoundEvent sound, SoundCategory source, float volume, float pitch, CallbackInfo ci) {
      if (RemovalsFeature.shouldRemoveSound(sound)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"playSoundClient(Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPlayPlayerSound(SoundEvent sound, SoundCategory source, float volume, float pitch, CallbackInfo ci) {
      if (RemovalsFeature.shouldRemoveSound(sound)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"playSoundClient(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZ)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPlayLocalPositionedSound(
      double x, double y, double z, SoundEvent sound, SoundCategory source, float volume, float pitch, boolean distanceDelay, CallbackInfo ci
   ) {
      if (RemovalsFeature.shouldRemoveSound(sound)) {
         ci.cancel();
      }
   }
}
