package org.ryzen.utils.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.ryzen.utils.ColorUtil;

@Environment(EnvType.CLIENT)
public final class HurtUtil {
   private static final int HURT_COLOR = -44459;
   private static final float HURT_TICKS = 10.0F;

   private HurtUtil() {
   }

   public static float factor(Entity entity) {
      if (entity instanceof LivingEntity living && living.hurtTime > 0) {
         return MathHelper.clamp((float)living.hurtTime / 10.0F, 0.0F, 1.0F);
      }

      return 0.0F;
   }

   public static float easedFactor(Entity entity) {
      float factor = factor(entity);
      return 1.0F - (1.0F - factor) * (1.0F - factor);
   }

   public static int blend(int baseColor, Entity entity, float alpha) {
      return blend(baseColor, easedFactor(entity), alpha);
   }

   public static int blend(int baseColor, float factor, float alpha) {
      factor = MathHelper.clamp(factor, 0.0F, 1.0F);
      int normal = ColorUtil.multiplyAlpha(baseColor, alpha);
      if (factor <= 0.0F) {
         return normal;
      } else {
         int hurt = ColorUtil.multiplyAlpha(-44459, alpha);
         return ColorUtil.lerp(normal, hurt, factor);
      }
   }

   public static float scale(Entity entity, float intensity) {
      return 1.0F + easedFactor(entity) * Math.max(0.0F, intensity);
   }
}
