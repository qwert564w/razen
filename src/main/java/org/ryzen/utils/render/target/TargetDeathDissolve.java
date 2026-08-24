package org.ryzen.utils.render.target;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.particles.WorldParticleRenderer;

@Environment(EnvType.CLIENT)
final class TargetDeathDissolve {
   private static final int MAX_FRAGMENTS = 240;
   private final List<TargetDeathDissolve.Fragment> fragments = new ArrayList<>();
   private final List<WorldParticleRenderer.Sprite> sprites = new ArrayList<>();
   private final WorldParticleRenderer renderer = new WorldParticleRenderer();
   private final Random random = new Random();

   void burst(List<WorldParticleRenderer.Sprite> shape, LivingEntity target, int fallbackColor, long now) {
      this.burst(shape, target, fallbackColor, now, 240, 0L, 0.018F, 0.18F, 0.42F, 1.0F, 1200L, 1200, 1.0F);
   }

   private void burst(
      List<WorldParticleRenderer.Sprite> shape,
      LivingEntity target,
      int fallbackColor,
      long now,
      int maxFragments,
      long holdMillis,
      float minimumSize,
      float sizeFrom,
      float sizeTo,
      float outwardMultiplier,
      long lifeBase,
      int lifeVariance,
      float alphaScale
   ) {
      this.fragments.clear();
      Vec3d center = target.getEntityPos().add(0.0, (double)target.getHeight() * 0.5, 0.0);
      if (shape.isEmpty()) {
         this.spawnFallback(target, fallbackColor, center, now);
      } else {
         int stride = Math.max(1, (int)Math.ceil((double)shape.size() / (double)maxFragments));
         int sampledCount = (shape.size() + stride - 1) / stride;
         int copies = Math.max(1, Math.min(3, maxFragments / sampledCount));

         for (int sourceIndex = 0; sourceIndex < shape.size() && this.fragments.size() < maxFragments; sourceIndex += stride) {
            WorldParticleRenderer.Sprite source = shape.get(sourceIndex);

            for (int copy = 0; copy < copies && this.fragments.size() < maxFragments; copy++) {
               this.spawnFragment(
                  source.position(),
                  center,
                  Math.max(minimumSize, source.halfSize() * this.randomRange(sizeFrom, sizeTo)),
                  source.color(),
                  now,
                  holdMillis,
                  outwardMultiplier,
                  lifeBase,
                  lifeVariance,
                  alphaScale
               );
            }
         }
      }
   }

   void render(float delta, long now) {
      this.sprites.clear();
      Iterator<TargetDeathDissolve.Fragment> iterator = this.fragments.iterator();

      while (iterator.hasNext()) {
         TargetDeathDissolve.Fragment fragment = iterator.next();
         long age = now - fragment.bornAt;
         if (age >= fragment.lifeMillis) {
            iterator.remove();
         } else {
            fragment.phase = fragment.phase + fragment.twinkleRate * delta;
            if (age >= fragment.holdMillis) {
               float drag = (float)Math.exp((double)(-1.65F * delta));
               fragment.vx *= (double)drag;
               fragment.vz *= (double)drag;
               fragment.vy += (double)(0.22F * delta);
               fragment.vx = fragment.vx + Math.cos((double)fragment.phase) * 0.045F * (double)delta;
               fragment.vz = fragment.vz + Math.sin((double)(fragment.phase * 0.87F)) * 0.045F * (double)delta;
               fragment.x = fragment.x + fragment.vx * (double)delta;
               fragment.y = fragment.y + fragment.vy * (double)delta;
               fragment.z = fragment.z + fragment.vz * (double)delta;
            }

            long movingAge = Math.max(0L, age - fragment.holdMillis);
            long movingLife = Math.max(1L, fragment.lifeMillis - fragment.holdMillis);
            float progress = MathHelper.clamp((float)movingAge / (float)movingLife, 0.0F, 1.0F);
            float fadeIn = MathHelper.clamp((float)age / 90.0F, 0.0F, 1.0F);
            float fadeOut = (1.0F - progress) * (1.0F - progress);
            float twinkle = 0.72F + 0.28F * (float)Math.sin((double)fragment.phase);
            float alpha = fadeIn * fadeOut * twinkle * fragment.alphaScale;
            float size = fragment.halfSize * (1.0F + progress * 0.65F);
            this.sprites
               .add(new WorldParticleRenderer.Sprite(new Vec3d(fragment.x, fragment.y, fragment.z), size, ColorUtil.multiplyAlpha(fragment.color, alpha)));
         }
      }

      this.renderer.render(this.sprites, 1.65F, true);
   }

   void clear() {
      this.fragments.clear();
      this.sprites.clear();
   }

   private void spawnFallback(LivingEntity target, int color, Vec3d center, long now) {
      Box box = target.getBoundingBox();

      for (int i = 0; i < 90; i++) {
         Vec3d position = new Vec3d(
            MathHelper.lerp(this.random.nextDouble(), box.minX, box.maxX),
            MathHelper.lerp(this.random.nextDouble(), box.minY, box.maxY),
            MathHelper.lerp(this.random.nextDouble(), box.minZ, box.maxZ)
         );
         this.spawnFragment(position, center, this.randomRange(0.025F, 0.07F), color, now, 0L, 1.0F, 1200L, 1200, 1.0F);
      }
   }

   private void spawnFragment(
      Vec3d position,
      Vec3d center,
      float halfSize,
      int color,
      long now,
      long holdMillis,
      float outwardMultiplier,
      long lifeBase,
      int lifeVariance,
      float alphaScale
   ) {
      Vec3d radial = position.subtract(center);
      double horizontalLength = Math.sqrt(radial.x * radial.x + radial.z * radial.z);
      double radialX = horizontalLength > 1.0E-5 ? radial.x / horizontalLength : 0.0;
      double radialZ = horizontalLength > 1.0E-5 ? radial.z / horizontalLength : 0.0;
      float outward = this.randomRange(0.35F, 1.15F) * outwardMultiplier;
      TargetDeathDissolve.Fragment fragment = new TargetDeathDissolve.Fragment();
      fragment.x = position.x + (double)this.randomRange(-0.025F, 0.025F);
      fragment.y = position.y + (double)this.randomRange(-0.025F, 0.025F);
      fragment.z = position.z + (double)this.randomRange(-0.025F, 0.025F);
      fragment.vx = radialX * (double)outward + (double)this.randomRange(-0.24F, 0.24F);
      fragment.vy = (double)this.randomRange(0.28F, 1.05F) + Math.max(0.0, radial.y) * 0.04;
      fragment.vz = radialZ * (double)outward + (double)this.randomRange(-0.24F, 0.24F);
      fragment.halfSize = Math.min(0.18F, halfSize);
      fragment.color = ColorUtil.withAlpha(color, 255);
      fragment.bornAt = now;
      fragment.holdMillis = holdMillis;
      fragment.lifeMillis = holdMillis + lifeBase + (long)this.random.nextInt(Math.max(1, lifeVariance));
      fragment.alphaScale = alphaScale;
      fragment.phase = this.random.nextFloat() * (float) (Math.PI * 2);
      fragment.twinkleRate = this.randomRange(4.0F, 8.0F);
      this.fragments.add(fragment);
   }

   private float randomRange(float min, float max) {
      return min + (max - min) * this.random.nextFloat();
   }

   @Environment(EnvType.CLIENT)
   private static final class Fragment {
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      float halfSize;
      int color;
      long bornAt;
      long holdMillis;
      long lifeMillis;
      float alphaScale;
      float phase;
      float twinkleRate;
   }
}
