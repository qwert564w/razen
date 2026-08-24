package org.ryzen.mixin.accessor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({LivingEntity.class})
public interface LivingEntityAccessor {
   @Accessor("jumpingCooldown")
   int getNoJumpDelay();

   @Accessor("jumpingCooldown")
   void setNoJumpDelay(int var1);
}
