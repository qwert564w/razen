package org.ryzen.utils.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;

/**
 * Intpol — rotation engineered for AI / neural anticheats
 * (FunTime, SpookyTime, HolyWorld, MineBlaze, VonTam etc.)
 *
 * Based on:
 * - multi-easing interpolation (non-linear, always different Δyaw / Δpitch)
 * - floating aim-point inside hitbox with min-distance change
 * - coupled yaw+pitch movement (never one axis alone)
 * - human-like delta clamps (~60 yaw, ~23-26 pitch)
 * - 65-75% of instant rotation speed
 * - micro-jitter + rare overshoot + post-hit lookaway
 * - autoregressive delta memory for RNN resistance
 */
@Environment(EnvType.CLIENT)
public final class IntpolRotation implements AuraRotation {

   // ─── Easing set ────────────────────────────────────────────────────────────
   private enum Easing {
      EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
      EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
      EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
      EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
      EASE_IN_QUINT, EASE_OUT_QUINT, EASE_IN_OUT_QUINT,
      EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
      EASE_IN_CIRC, EASE_OUT_CIRC, EASE_IN_OUT_CIRC,
      EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
      EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
      EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE
   }

   private static final Easing[] EASINGS = Easing.values();

   // Human delta stats from the theory + paper
   private static final float YAW_CLAMP_BASE = 60.0F;
   private static final float YAW_CLAMP_RAND = 1.0329834F;
   private static final float PITCH_CLAMP_MIN = 23.133F;
   private static final float PITCH_CLAMP_MAX = 26.477F;

   // Speed factor ≈ 65-75 % of instant
   private static final float BASE_SPEED = 0.68F;
   private static final float SPEED_VARIANCE = 0.09F;

   // ─── State ─────────────────────────────────────────────────────────────────
   private Easing currentEasing = Easing.EASE_IN_OUT_SINE;
   private float progress;
   private float progressSpeed;
   private float startYaw, startPitch;
   private float targetYaw, targetPitch;
   private float lastAppliedYaw, lastAppliedPitch;
   private boolean hasSegment;

   // Floating aim point
   private double aimOffX, aimOffY, aimOffZ;
   private double prevAimOffX, prevAimOffY, prevAimOffZ;
   private long nextAimChange;
   private static final long AIM_MIN_MS = 110L;
   private static final long AIM_MAX_MS = 340L;
   private static final double MIN_AIM_DIST = 0.18; // min distance between consecutive points

   // Jitter / noise
   private float noisePhase;
   private float microYaw, microPitch;

   // AR memory
   private float prevΔYaw, prevΔPitch;
   private float yawVel, pitchVel;

   // Overshoot & post-hit lookaway
   private float overYaw, overPitch;
   private int overTicks;
   private float lookAwayYaw, lookAwayPitch;
   private int lookAwayTicks;

   // Pattern cycling for high entropy
   private int patternIdx;
   private static final int[][] PATTERNS = {
      {0, 2, 5, 8, 11, 14},
      {3, 6, 9, 12, 15, 18},
      {1, 4, 7, 10, 13, 16},
      {19, 20, 21, 22, 23, 24},
      {25, 26, 27, 2, 5, 8},
      {8, 14, 20, 5, 11, 17},
      {0, 25, 3, 26, 6, 27},
      {12, 15, 18, 21, 24, 9}
   };

   // ──────────────────────────────────────────────────────────────────────────
   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      if (target == null) {
         reset();
         return;
      }

      float curYaw = RotationContext.isActive() ? RotationContext.getFreeYaw() : player.getYaw();
      float curPitch = RotationContext.isActive() ? RotationContext.getFreePitch() : player.getPitch();

      // ── 1. Floating aim point (min distance + both axes move) ───────────────
      updateFloatingPoint(target);
      Vec3d aim = computeAim(target);
      Vec3d eye = player.getEyePos();
      Vec3d d = aim.subtract(eye);
      double horiz = Math.sqrt(d.x * d.x + d.z * d.z);

      float wantedYaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
      float wantedPitch = (float) (-Math.toDegrees(Math.atan2(d.y, horiz)));

      float rawYawΔ = MathHelper.wrapDegrees(wantedYaw - curYaw);
      float rawPitchΔ = wantedPitch - curPitch;

      // ── 2. Human delta clamps (theory) ─────────────────────────────────────
      float yawLimit = YAW_CLAMP_BASE + ThreadLocalRandom.current().nextFloat() * YAW_CLAMP_RAND;
      float pitchLimit = PITCH_CLAMP_MIN + ThreadLocalRandom.current().nextFloat() * (PITCH_CLAMP_MAX - PITCH_CLAMP_MIN);

      float yawΔ = MathHelper.clamp(rawYawΔ, -yawLimit, yawLimit);
      float pitchΔ = MathHelper.clamp(rawPitchΔ, -pitchLimit, pitchLimit);

      // ── 3. Coupled axes fix (never one axis alone) ─────────────────────────
      if (Math.abs(yawΔ) < 1e-4F && Math.abs(pitchΔ) > 0.05F) {
         yawΔ += (ThreadLocalRandom.current().nextFloat() * 0.4F + 0.1F) * Math.signum(pitchΔ == 0 ? 1 : pitchΔ) * 1.0313F;
      }
      if (Math.abs(pitchΔ) < 1e-4F && Math.abs(yawΔ) > 0.05F) {
         pitchΔ += (ThreadLocalRandom.current().nextFloat() * 0.4F + 0.1F) * Math.signum(yawΔ == 0 ? 1 : yawΔ) * 1.0313F;
      }

      float hypot = (float) Math.hypot(Math.abs(yawΔ), Math.abs(pitchΔ));

      // Already close enough → only micro-tremor
      if (hypot < 0.4F && lookAwayTicks <= 0) {
         float jy = microTremor(0.07F);
         float jp = microTremor(0.045F);
         // keep coupling
         if (Math.abs(jy) < 1e-4F) jy += 0.02F * Math.signum(jp == 0 ? 1 : jp);
         if (Math.abs(jp) < 1e-4F) jp += 0.015F * Math.signum(jy == 0 ? 1 : jy);
         apply(curYaw + jy, curPitch + jp);
         prevΔYaw = jy;
         prevΔPitch = jp;
         return;
      }

      // ── 4. Distance + player-state speed modulation ────────────────────────
      double dist = eye.distanceTo(aim);
      float distFactor = distanceFactor(dist);               // close slow, mid fast, far slow
      double hSpeed = player.getVelocity().horizontalLength();
      float fall = (float) player.fallDistance;
      float swing = player.getHandSwingProgress(1.0F);

      float stateMod = 1.0F
         + (float) MathHelper.clamp(hSpeed * 0.16, -0.22, 0.30)
         - MathHelper.clamp(fall * 0.035F, 0.0F, 0.28F)
         + (swing > 0.12F && swing < 0.88F ? 0.10F : -0.04F);

      float angleFactor = 0.40F + MathHelper.clamp(hypot / 52.0F, 0.0F, 1.05F);

      // ── 5. Segment / easing management ─────────────────────────────────────
      if (!hasSegment || hypot > 38.0F || ThreadLocalRandom.current().nextFloat() < 0.035F) {
         pickEasing();
         startYaw = curYaw;
         startPitch = curPitch;
         targetYaw = wantedYaw;
         targetPitch = wantedPitch;
         progress = 0.0F;
         progressSpeed = (BASE_SPEED + ThreadLocalRandom.current().nextFloat() * SPEED_VARIANCE)
            * 0.055F * distFactor * stateMod * angleFactor;
         hasSegment = true;
      } else {
         // soft retarget while interpolating
         targetYaw = MathHelper.lerp(0.16F, targetYaw, wantedYaw);
         targetPitch = MathHelper.lerp(0.13F, targetPitch, wantedPitch);
      }

      progress = MathHelper.clamp(progress + progressSpeed, 0.0F, 1.0F);
      float t = applyEasing(currentEasing, progress);

      float interYaw = lerpAngle(startYaw, targetYaw, t);
      float interPitch = MathHelper.lerp(t, startPitch, targetPitch);

      float stepYaw = MathHelper.wrapDegrees(interYaw - curYaw);
      float stepPitch = interPitch - curPitch;

      // Pitch always slower
      stepPitch *= 0.58F + ThreadLocalRandom.current().nextFloat() * 0.10F;

      // Re-clamp after easing
      stepYaw = MathHelper.clamp(stepYaw, -yawLimit * 0.92F, yawLimit * 0.92F);
      stepPitch = MathHelper.clamp(stepPitch, -pitchLimit * 0.92F, pitchLimit * 0.92F);

      // ── 6. Velocity + AR(1) ────────────────────────────────────────────────
      yawVel = MathHelper.lerp(0.40F, yawVel, stepYaw) * 0.91F + prevΔYaw * 0.09F;
      pitchVel = MathHelper.lerp(0.36F, pitchVel, stepPitch) * 0.89F + prevΔPitch * 0.08F;

      // ── 7. Rare overshoot ──────────────────────────────────────────────────
      if (overTicks > 0) {
         yawVel += overYaw;
         pitchVel += overPitch;
         overTicks--;
         overYaw *= 0.76F;
         overPitch *= 0.70F;
      } else if (ThreadLocalRandom.current().nextFloat() < 0.016F && hypot > 7.0F) {
         overYaw = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 3.4F;
         overPitch = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 1.7F;
         overTicks = 2 + ThreadLocalRandom.current().nextInt(4);
      }

      // ── 8. Post-hit lookaway ───────────────────────────────────────────────
      if (lookAwayTicks > 0) {
         yawVel += lookAwayYaw * 0.55F;
         pitchVel += lookAwayPitch * 0.45F;
         lookAwayTicks--;
         lookAwayYaw *= 0.82F;
         lookAwayPitch *= 0.78F;
      }

      // ── 9. Micro-jitter + sinus ────────────────────────────────────────────
      noisePhase += 0.15F + ThreadLocalRandom.current().nextFloat() * 0.12F;
      float sinJ = (float) Math.sin(noisePhase) * 0.10F;
      float nY = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 0.20F;
      float nP = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 0.12F;

      microYaw = MathHelper.lerp(0.27F, microYaw, nY + sinJ);
      microPitch = MathHelper.lerp(0.24F, microPitch, nP + sinJ * 0.55F);

      float finalYaw = curYaw + yawVel + microYaw;
      float finalPitch = curPitch + pitchVel + microPitch;

      // If almost identical to previous applied → keep old (no zero-delta spam)
      if (Math.abs(MathHelper.wrapDegrees(finalYaw - lastAppliedYaw)) < 0.035F
         && Math.abs(finalPitch - lastAppliedPitch) < 0.025F) {
         finalYaw = lastAppliedYaw;
         finalPitch = lastAppliedPitch;
      }

      // Final coupling safety
      float outYawΔ = MathHelper.wrapDegrees(finalYaw - curYaw);
      float outPitchΔ = finalPitch - curPitch;
      if (Math.abs(outYawΔ) < 1e-4F && Math.abs(outPitchΔ) > 0.03F) {
         finalYaw += 0.08F * Math.signum(outPitchΔ);
      }
      if (Math.abs(outPitchΔ) < 1e-4F && Math.abs(outYawΔ) > 0.03F) {
         finalPitch += 0.05F * Math.signum(outYawΔ);
      }

      apply(finalYaw, finalPitch);

      prevΔYaw = yawVel;
      prevΔPitch = pitchVel;
      lastAppliedYaw = finalYaw;
      lastAppliedPitch = finalPitch;

      if (progress >= 0.97F) {
         hasSegment = false;
         pickEasing();
      }
   }

   @Override
   public void onAttack() {
      // kick
      yawVel += (ThreadLocalRandom.current().nextFloat() - 0.5F) * 2.2F;
      pitchVel += (ThreadLocalRandom.current().nextFloat() - 0.5F) * 1.0F;

      // post-hit lookaway (30-50° or small random)
      if (ThreadLocalRandom.current().nextFloat() < 0.55F) {
         float angle = 28.0F + ThreadLocalRandom.current().nextFloat() * 22.0F;
         float dir = ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F;
         lookAwayYaw = dir * angle * (0.6F + ThreadLocalRandom.current().nextFloat() * 0.5F);
         lookAwayPitch = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 12.0F;
         lookAwayTicks = 4 + ThreadLocalRandom.current().nextInt(6);
      }

      // force new aim point
      nextAimChange = 0L;
   }

   @Override
   public void reset() {
      progress = 0.0F;
      hasSegment = false;
      yawVel = pitchVel = 0.0F;
      prevΔYaw = prevΔPitch = 0.0F;
      microYaw = microPitch = 0.0F;
      overTicks = lookAwayTicks = 0;
      aimOffX = aimOffY = aimOffZ = 0.0;
      prevAimOffX = prevAimOffY = prevAimOffZ = 0.0;
      nextAimChange = 0L;
   }

   // ─── Helpers ───────────────────────────────────────────────────────────────
   private void pickEasing() {
      int[] pat = PATTERNS[patternIdx % PATTERNS.length];
      int idx = pat[ThreadLocalRandom.current().nextInt(pat.length)];
      currentEasing = EASINGS[MathHelper.clamp(idx, 0, EASINGS.length - 1)];
      patternIdx++;
      if (ThreadLocalRandom.current().nextFloat() < 0.20F) {
         currentEasing = EASINGS[ThreadLocalRandom.current().nextInt(EASINGS.length)];
      }
   }

   private void updateFloatingPoint(LivingEntity target) {
      long now = System.currentTimeMillis();
      if (now < nextAimChange) return;

      Box box = target.getBoundingBox();
      double w = box.getLengthX();
      double h = box.getLengthY();
      double d = box.getLengthZ();

      // try several candidates until min distance is satisfied
      for (int i = 0; i < 8; i++) {
         double nx = (ThreadLocalRandom.current().nextDouble() * 0.68 - 0.34) * w;
         double ny = (0.15 + ThreadLocalRandom.current().nextDouble() * 0.60) * h - h * 0.5;
         double nz = (ThreadLocalRandom.current().nextDouble() * 0.68 - 0.34) * d;

         double dist = Math.sqrt(
            (nx - prevAimOffX) * (nx - prevAimOffX)
            + (ny - prevAimOffY) * (ny - prevAimOffY)
            + (nz - prevAimOffZ) * (nz - prevAimOffZ)
         );
         if (dist >= MIN_AIM_DIST || i == 7) {
            prevAimOffX = aimOffX;
            prevAimOffY = aimOffY;
            prevAimOffZ = aimOffZ;
            aimOffX = nx;
            aimOffY = ny;
            aimOffZ = nz;
            break;
         }
      }
      nextAimChange = now + AIM_MIN_MS + ThreadLocalRandom.current().nextLong(AIM_MAX_MS - AIM_MIN_MS);
   }

   private Vec3d computeAim(LivingEntity target) {
      return target.getBoundingBox().getCenter().add(aimOffX, aimOffY, aimOffZ);
   }

   private static float distanceFactor(double dist) {
      if (dist < 1.55) return 0.58F + (float) (dist / 1.55) * 0.28F;
      if (dist < 3.35) return 0.92F + (float) ((dist - 1.55) / 1.8) * 0.32F;
      return 1.12F - MathHelper.clamp((float) ((dist - 3.35) / 4.0), 0.0F, 0.50F);
   }

   private static float microTremor(float amp) {
      return (ThreadLocalRandom.current().nextFloat() - 0.5F) * 2.0F * amp;
   }

   private static float lerpAngle(float from, float to, float t) {
      float diff = MathHelper.wrapDegrees(to - from);
      return from + diff * t;
   }

   private void apply(float yaw, float pitch) {
      RotationContext.setRotation(yaw, MathHelper.clamp(pitch, -89.5F, 89.5F));
   }

   // ─── Easing implementations ────────────────────────────────────────────────
   private static float applyEasing(Easing e, float t) {
      t = MathHelper.clamp(t, 0.0F, 1.0F);
      return switch (e) {
         case EASE_IN_SINE -> 1.0F - (float) Math.cos(t * Math.PI * 0.5);
         case EASE_OUT_SINE -> (float) Math.sin(t * Math.PI * 0.5);
         case EASE_IN_OUT_SINE -> -(float) Math.cos(Math.PI * t) * 0.5F + 0.5F;

         case EASE_IN_QUAD -> t * t;
         case EASE_OUT_QUAD -> 1.0F - (1.0F - t) * (1.0F - t);
         case EASE_IN_OUT_QUAD -> t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0 * t + 2.0, 2) * 0.5F;

         case EASE_IN_CUBIC -> t * t * t;
         case EASE_OUT_CUBIC -> 1.0F - (float) Math.pow(1.0 - t, 3);
         case EASE_IN_OUT_CUBIC -> t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0 * t + 2.0, 3) * 0.5F;

         case EASE_IN_QUART -> t * t * t * t;
         case EASE_OUT_QUART -> 1.0F - (float) Math.pow(1.0 - t, 4);
         case EASE_IN_OUT_QUART -> t < 0.5F ? 8.0F * t * t * t * t : 1.0F - (float) Math.pow(-2.0 * t + 2.0, 4) * 0.5F;

         case EASE_IN_QUINT -> t * t * t * t * t;
         case EASE_OUT_QUINT -> 1.0F - (float) Math.pow(1.0 - t, 5);
         case EASE_IN_OUT_QUINT -> t < 0.5F ? 16.0F * t * t * t * t * t : 1.0F - (float) Math.pow(-2.0 * t + 2.0, 5) * 0.5F;

         case EASE_IN_EXPO -> t == 0.0F ? 0.0F : (float) Math.pow(2.0, 10.0 * t - 10.0);
         case EASE_OUT_EXPO -> t == 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * t);
         case EASE_IN_OUT_EXPO -> {
            if (t == 0.0F) yield 0.0F;
            if (t == 1.0F) yield 1.0F;
            yield t < 0.5F
               ? (float) Math.pow(2.0, 20.0 * t - 10.0) * 0.5F
               : (2.0F - (float) Math.pow(2.0, -20.0 * t + 10.0)) * 0.5F;
         }

         case EASE_IN_CIRC -> 1.0F - (float) Math.sqrt(1.0 - (double) (t * t));
         case EASE_OUT_CIRC -> (float) Math.sqrt(1.0 - Math.pow(t - 1.0, 2));
         case EASE_IN_OUT_CIRC -> t < 0.5F
            ? (1.0F - (float) Math.sqrt(1.0 - Math.pow(2.0 * t, 2))) * 0.5F
            : ((float) Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2)) + 1.0F) * 0.5F;

         case EASE_IN_BACK -> {
            final float c1 = 1.70158F, c3 = c1 + 1.0F;
            yield c3 * t * t * t - c1 * t * t;
         }
         case EASE_OUT_BACK -> {
            final float c1 = 1.70158F, c3 = c1 + 1.0F;
            yield 1.0F + c3 * (float) Math.pow(t - 1.0, 3) + c1 * (float) Math.pow(t - 1.0, 2);
         }
         case EASE_IN_OUT_BACK -> {
            final float c1 = 1.70158F, c2 = c1 * 1.525F;
            yield t < 0.5F
               ? ((float) Math.pow(2.0 * t, 2) * ((c2 + 1.0F) * 2.0F * t - c2)) * 0.5F
               : ((float) Math.pow(2.0 * t - 2.0, 2) * ((c2 + 1.0F) * (t * 2.0F - 2.0F) + c2) + 2.0F) * 0.5F;
         }

         case EASE_OUT_ELASTIC -> {
            final float c4 = (2.0F * (float) Math.PI) / 3.0F;
            if (t == 0.0F) yield 0.0F;
            if (t == 1.0F) yield 1.0F;
            yield (float) Math.pow(2.0, -10.0 * t) * (float) Math.sin((t * 10.0 - 0.75) * c4) + 1.0F;
         }
         case EASE_IN_OUT_ELASTIC -> {
            final float c5 = (2.0F * (float) Math.PI) / 4.5F;
            if (t == 0.0F) yield 0.0F;
            if (t == 1.0F) yield 1.0F;
            yield t < 0.5F
               ? -(float) Math.pow(2.0, 20.0 * t - 10.0) * (float) Math.sin((20.0 * t - 11.125) * c5) * 0.5F
               : (float) Math.pow(2.0, -20.0 * t + 10.0) * (float) Math.sin((20.0 * t - 11.125) * c5) * 0.5F + 1.0F;
         }

         case EASE_OUT_BOUNCE -> bounceOut(t);
         case EASE_IN_OUT_BOUNCE -> t < 0.5F
            ? (1.0F - bounceOut(1.0F - 2.0F * t)) * 0.5F
            : (1.0F + bounceOut(2.0F * t - 1.0F)) * 0.5F;
      };
   }

   private static float bounceOut(float t) {
      final float n1 = 7.5625F, d1 = 2.75F;
      if (t < 1.0F / d1) return n1 * t * t;
      if (t < 2.0F / d1) { t -= 1.5F / d1; return n1 * t * t + 0.75F; }
      if (t < 2.5F / d1) { t -= 2.25F / d1; return n1 * t * t + 0.9375F; }
      t -= 2.625F / d1;
      return n1 * t * t + 0.984375F;
   }
}
