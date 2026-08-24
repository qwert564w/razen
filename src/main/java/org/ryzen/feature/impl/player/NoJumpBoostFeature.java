package org.ryzen.feature.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;

@Environment(EnvType.CLIENT)
public final class NoJumpBoostFeature extends Feature {
   public NoJumpBoostFeature() {
      super("NoJumpBoost", "Removes jump boost effect", FeatureCategory.PLAYER, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
         player.removeStatusEffect(StatusEffects.JUMP_BOOST);
      }
   }
}
