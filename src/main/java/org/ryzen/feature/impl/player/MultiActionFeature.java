package org.ryzen.feature.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;

@Environment(EnvType.CLIENT)
public final class MultiActionFeature extends Feature {
   public MultiActionFeature() {
      super("MultiAction", "Attack and mine while using items", FeatureCategory.PLAYER, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && client.interactionManager != null) {
         if (player.isUsingItem() && client.options.attackKey.isPressed()) {
            HitResult hit = client.crosshairTarget;
            if (hit instanceof EntityHitResult entityHit) {
               if (player.getAttackCooldownProgress(0.0F) >= 1.0F) {
                  client.interactionManager.attackEntity(player, entityHit.getEntity());
                  player.swingHand(Hand.MAIN_HAND);
               }
            } else {
               if (hit instanceof BlockHitResult blockHit && hit.getType() == Type.BLOCK) {
                  client.interactionManager.updateBlockBreakingProgress(blockHit.getBlockPos(), blockHit.getSide());
               }

               player.swingHand(Hand.MAIN_HAND);
            }
         }
      }
   }
}
