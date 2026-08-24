package org.ryzen.utils.combat.rotations;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public interface AuraRotation {
   void tick(ClientPlayerEntity var1, LivingEntity var2, Vec3d var3, boolean var4);

   default void onAttack() {
   }

   default void reset() {
   }
}
