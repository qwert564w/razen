package org.ryzen.utils.render.target;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.math.Animation;
import org.ryzen.utils.render.HurtUtil;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.particles.WorldParticleRenderer;

@Environment(EnvType.CLIENT)
public final class GhostTargetRenderer {
   private static final int SPIRAL_LIMIT = 9;
   private static final int SPIRAL_STEP = 3;
   private static final int PARTICLES_PER_SPIRAL = 20;
   private final Animation appear = new Animation(650L, Animation.Easing.EASE_OUT_EXPO);
   private final Animation inner = new Animation(850L, Animation.Easing.EASE_IN_OUT_QUAD);
   private final List<WorldParticleRenderer.Sprite> sprites = new ArrayList<>(60);
   private final WorldParticleRenderer renderer = new WorldParticleRenderer();
   private final TargetDeathDissolve deathDissolve = new TargetDeathDissolve();
   private LivingEntity lastTarget;
   private int dissolvedTargetId = Integer.MIN_VALUE;
   private boolean animationTarget;
   private long lastFrameNanos;
   private float clock;

   public void render(LivingEntity activeTarget, float tickDelta, int color) {
      long nowNanos = System.nanoTime();
      long nowMillis = System.currentTimeMillis();
      float delta = this.lastFrameNanos == 0L ? 0.016F : Math.min(0.1F, (float)(nowNanos - this.lastFrameNanos) / 1.0E9F);
      this.lastFrameNanos = nowNanos;
      boolean present = valid(activeTarget);
      if (present) {
         this.lastTarget = activeTarget;
         if (activeTarget.getId() == this.dissolvedTargetId) {
            this.dissolvedTargetId = Integer.MIN_VALUE;
         }
      }

      if (dead(this.lastTarget) && this.lastTarget.getId() != this.dissolvedTargetId) {
         this.deathDissolve.burst(this.sprites, this.lastTarget, color, nowMillis);
         this.dissolvedTargetId = this.lastTarget.getId();
         present = false;
      }

      this.updateAnimations(present);
      float appearValue = this.appear.getValue();
      float innerValue = this.inner.getValue();
      if (this.lastTarget != null && !(appearValue <= 0.01F) && !(innerValue <= 0.001F)) {
         this.clock += delta * 1000.0F;
         Vec3d base = Render3DUtil.interpolatedPosition(this.lastTarget, tickDelta);
         float animation = this.clock / 120.0F;
         float baseAlpha = Math.min(0.85F, innerValue * 1.6F);
         int animatedColor = HurtUtil.blend(color, this.lastTarget, 1.0F);
         this.sprites.clear();

         for (int spiral = 0; spiral < 9; spiral += 3) {
            int spiralPhase = (int)Math.pow((double)spiral, 2.0);

            for (int particle = 0; particle < 20; particle++) {
               float phase = animation + (float)particle * 0.1F;
               int index = spiral / 3 * 20 + particle;
               float assembly = TargetEffectMotion.assembly(innerValue, index, 60);
               double x = base.x + 0.8F * Math.sin((double)(phase + (float)spiralPhase)) + TargetEffectMotion.scatter(index, 0, 1.75, assembly);
               double y = base.y
                  + 0.5
                  + 0.3F * Math.sin((double)(animation + (float)particle * 0.2F))
                  + (double)(0.2F * (float)spiral)
                  + TargetEffectMotion.scatter(index, 1, 1.35, assembly)
                  + (double)((1.0F - assembly) * 0.35F);
               double z = base.z + 0.8F * Math.cos((double)(phase - (float)spiralPhase)) + TargetEffectMotion.scatter(index, 2, 1.75, assembly);
               float particleScale = innerValue * (0.005F + (float)particle / 2000.0F);
               float tail = (float)Math.pow((double)((float)particle / 11.0F), 1.3);
               float alpha = MathHelper.clamp(baseAlpha * tail * appearValue, 0.0F, 1.0F);
               float halfSize = 25.0F * particleScale * (0.55F + assembly * 0.45F);
               if (!(alpha <= 0.004F) && !(halfSize <= 0.001F)) {
                  this.sprites.add(new WorldParticleRenderer.Sprite(new Vec3d(x, y, z), halfSize, ColorUtil.multiplyAlpha(animatedColor, alpha)));
               }
            }
         }

         this.renderer.render(this.sprites, 1.0F, true);
         this.deathDissolve.render(delta, nowMillis);
      } else {
         if (!present && appearValue <= 0.01F) {
            this.lastTarget = null;
            this.sprites.clear();
         }

         this.deathDissolve.render(delta, nowMillis);
      }
   }

   public void reset() {
      this.lastTarget = null;
      this.animationTarget = false;
      this.lastFrameNanos = 0L;
      this.clock = 0.0F;
      this.dissolvedTargetId = Integer.MIN_VALUE;
      this.sprites.clear();
      this.deathDissolve.clear();
      this.appear.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_EXPO);
      this.inner.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_IN_OUT_QUAD);
   }

   private void updateAnimations(boolean present) {
      if (present != this.animationTarget) {
         this.animationTarget = present;
         float target = present ? 1.0F : 0.0F;
         this.appear.animate(this.appear.getValue(), target, 650L, Animation.Easing.EASE_OUT_EXPO);
         this.inner.animate(this.inner.getValue(), target, 850L, Animation.Easing.EASE_IN_OUT_QUAD);
      }
   }

   private static boolean valid(LivingEntity entity) {
      return entity != null && entity.isAlive() && !entity.isRemoved();
   }

   private static boolean dead(LivingEntity entity) {
      return entity != null && (entity.isDead() || entity.deathTime > 0 || entity.getHealth() <= 0.0F);
   }
}
