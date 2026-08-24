package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.AttackEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.utils.FriendManager;

@Environment(EnvType.CLIENT)
public final class NoFriendDamageFeature extends Feature {
   public NoFriendDamageFeature() {
      super("NoFriendDamage", "Blocks manual hits on players in your friend list", FeatureCategory.COMBAT, -1);
   }

   @EventTarget
   public void onAttack(AttackEvent event) {
      Entity target = event.getClient().targetedEntity;
      if (target instanceof PlayerEntity player) {
         if (FriendManager.INSTANCE.isFriend(player.getGameProfile().name())) {
            AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
            if (aura == null || aura.getCurrentTarget() != target) {
               event.cancel();
            }
         }
      }
   }
}
