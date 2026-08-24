package org.ryzen.utils.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import org.ryzen.context.PlayerContext;
import org.ryzen.feature.impl.visual.HoldMyItemsCompat;

@Environment(EnvType.CLIENT)
public class AttackWindow implements PlayerContext {
   public static void attack(Entity target) {
      if (mc.interactionManager != null && mc.player != null) {
         HoldMyItemsCompat.beginMainHandAttack(mc.player);
         PiercingWeaponComponent piercing = (PiercingWeaponComponent)mc.player.getWeaponStack().get(DataComponentTypes.PIERCING_WEAPON);
         if (piercing != null) {
            mc.interactionManager.attackWithPiercingWeapon(piercing);
         } else {
            mc.interactionManager.attackEntity(mc.player, target);
         }

         mc.player.swingHand(Hand.MAIN_HAND);
         SprintManager.onAttack();
      }
   }
}
