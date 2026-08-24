package org.ryzen.utils.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;

@Environment(EnvType.CLIENT)
public final class AttackReach {
   private AttackReach() {
   }

   public static AttackRangeComponent range(ClientPlayerEntity player) {
      return (AttackRangeComponent)player.getWeaponStack().getOrDefault(DataComponentTypes.ATTACK_RANGE, AttackRangeComponent.defaultForEntity(player));
   }

   public static double max(ClientPlayerEntity player) {
      return (double)range(player).getEffectiveMaxRange(player);
   }

   public static double min(ClientPlayerEntity player) {
      return (double)range(player).getEffectiveMinRange(player);
   }

   public static double margin(ClientPlayerEntity player) {
      return (double)range(player).hitboxMargin();
   }
}
