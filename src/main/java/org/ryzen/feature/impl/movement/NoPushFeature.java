package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;

@Environment(EnvType.CLIENT)
public final class NoPushFeature extends Feature {
   public final BooleanSetting entity = this.register(new BooleanSetting("Entity", true));
   public final BooleanSetting blocks = this.register(new BooleanSetting("Blocks", true));
   public final BooleanSetting water = this.register(new BooleanSetting("Water", true));
   public final BooleanSetting fishingHook = this.register(new BooleanSetting("Fishing Hook", true));

   public NoPushFeature() {
      super("NoPush", "Prevents the player from being pushed", FeatureCategory.MOVEMENT, -1);
   }

   public static NoPushFeature getInstance() {
      return FeatureManager.INSTANCE.getFeature(NoPushFeature.class);
   }

   public static NoPushFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(NoPushFeature.class);
   }

   public static boolean shouldCancelEntityPush(Entity self, Entity other) {
      NoPushFeature feature = getEnabled();
      if (feature != null && feature.entity.getValue()) {
         PlayerEntity player = localPlayer();
         return player != null && self == player && other != player;
      } else {
         return false;
      }
   }

   public static boolean shouldCancelBlockPush(PlayerEntity player, BlockState state) {
      NoPushFeature feature = getEnabled();
      if (feature == null || !feature.blocks.getValue()) {
         return false;
      } else if (state != null && state.isOf(Blocks.COBWEB)) {
         return false;
      } else {
         PlayerEntity local = localPlayer();
         return local != null && player == local && state != null && !state.isAir();
      }
   }

   public static boolean shouldCancelClosestSpacePush(ClientPlayerEntity player) {
      NoPushFeature feature = getEnabled();
      if (feature != null && feature.blocks.getValue()) {
         PlayerEntity local = localPlayer();
         return local != null && player == local;
      } else {
         return false;
      }
   }

   public static boolean shouldCancelFluidPush(PlayerEntity player) {
      NoPushFeature feature = getEnabled();
      if (feature != null && feature.water.getValue()) {
         PlayerEntity local = localPlayer();
         return local != null && player == local;
      } else {
         return false;
      }
   }

   public static boolean shouldCancelFishingHookPull(FishingBobberEntity hook, Entity target) {
      NoPushFeature feature = getEnabled();
      if (feature != null && feature.fishingHook.getValue()) {
         PlayerEntity local = localPlayer();
         return local != null && target == local && hook != null && hook.getOwner() != local;
      } else {
         return false;
      }
   }

   private static PlayerEntity localPlayer() {
      return MinecraftClient.getInstance().player;
   }
}
