package org.ryzen.utils.render.target;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
public final class CircleTargetRenderer {
   private static final float PERIOD_MILLIS = 1500.0F;
   private static final int ARMS = 4;
   private static final int PARTICLES_PER_ARM = 15;
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
   private float hurtPhase;

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
         float hurt = this.lastTarget.hurtTime > 0 ? Math.max(0.0F, ((float)this.lastTarget.hurtTime - tickDelta) / 10.0F) : 0.0F;
         this.hurtPhase += hurt * delta * 500.0F;
         Vec3d base = Render3DUtil.interpolatedPosition(this.lastTarget, tickDelta);
         float height = this.lastTarget.getHeight();
         float cycle = this.clock % 1500.0F / 1500.0F;
         float progress = 0.5F + 0.5F * (float)Math.cos((double)cycle * Math.PI * 2.0);
         double movingY = base.y + (double)(height * progress);
         float movingAngle = this.clock / 2.5F;
         float baseAlpha = Math.min(0.8F, innerValue * 1.2F);
         int animatedColor = HurtUtil.blend(color, this.lastTarget, 1.0F);
         this.sprites.clear();

         for (int arm = 0; arm < 4; arm++) {
            for (int particle = 0; particle < 15; particle++) {
               float particleProgress = (float)particle / 14.0F;
               int index = arm * 15 + particle;
               float assembly = TargetEffectMotion.assembly(innerValue, index, 60);
               float worldSize = 0.5F * innerValue * (0.6F + assembly * 0.4F);
               float angle = 0.2F * (movingAngle + this.hurtPhase - (float)particle * 3.5F) / 15.0F;
               float triangle = particleProgress < 0.5F ? particleProgress * 2.0F : (1.0F - particleProgress) * 2.0F;
               double amplitude = Math.sin((double)triangle * Math.PI) * 2.0;
               Random random = new Random((long)particle * 12345L);
               double offsetX = (random.nextDouble() - 0.5) * amplitude;
               double offsetY = (random.nextDouble() - 0.5) * amplitude;
               double offsetZ = (random.nextDouble() - 0.5) * amplitude;
               double animatedOffsetX = offsetX * (double)assembly - offsetX;
               double animatedOffsetY = offsetY * (double)assembly - offsetY;
               double animatedOffsetZ = offsetZ * (double)assembly - offsetZ;
               double radius = 0.7;
               double localX;
               double localZ;
               switch (arm) {
                  case 0:
                     localX = Math.cos((double)angle) * radius + animatedOffsetX;
                     localZ = Math.sin((double)angle) * radius + animatedOffsetZ;
                     break;
                  case 1:
                     localX = -Math.sin((double)angle) * radius + animatedOffsetX;
                     localZ = Math.cos((double)angle) * radius + animatedOffsetZ;
                     break;
                  case 2:
                     localX = -Math.cos((double)angle) * radius + animatedOffsetX;
                     localZ = -Math.sin((double)angle) * radius + animatedOffsetZ;
                     break;
                  default:
                     localX = Math.sin((double)angle) * radius + animatedOffsetX;
                     localZ = -Math.cos((double)angle) * radius + animatedOffsetZ;
               }

               float tail = (float)Math.pow((double)(1.0F - particleProgress), 1.3);
               float alpha = MathHelper.clamp(baseAlpha * tail * appearValue, 0.0F, 1.0F);
               if (!(alpha <= 0.004F)) {
                  this.sprites
                     .add(
                        new WorldParticleRenderer.Sprite(
                           new Vec3d(
                              base.x + localX + TargetEffectMotion.scatter(index, 0, 1.4, assembly),
                              movingY + animatedOffsetY + TargetEffectMotion.scatter(index, 1, 1.15, assembly) + (double)((1.0F - assembly) * 0.25F),
                              base.z + localZ + TargetEffectMotion.scatter(index, 2, 1.4, assembly)
                           ),
                           worldSize * 0.5F,
                           ColorUtil.multiplyAlpha(animatedColor, alpha)
                        )
                     );
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
