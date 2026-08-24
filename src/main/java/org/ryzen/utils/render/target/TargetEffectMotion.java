package org.ryzen.utils.render.target;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
final class TargetEffectMotion {
   private TargetEffectMotion() {
   }

   static float assembly(float globalProgress, int index, int count) {
      float position = count <= 1 ? 0.0F : (float)index / (float)(count - 1);
      float delay = position * 0.22F;
      float progress = MathHelper.clamp((globalProgress - delay) / (1.0F - delay), 0.0F, 1.0F);
      return progress * progress * (3.0F - 2.0F * progress);
   }

   static double scatter(int seed, int axis, double distance, float assembly) {
      int hash = seed * -1640531527 + axis * 2135587861;
      hash ^= hash >>> 16;
      hash *= -2048144789;
      hash ^= hash >>> 13;
      float signed = (float)(hash >>> 8 & 65535) / 32767.5F - 1.0F;
      return (double)signed * distance * (double)(1.0F - assembly);
   }
}
