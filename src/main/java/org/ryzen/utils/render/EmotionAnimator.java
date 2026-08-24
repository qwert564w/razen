package org.ryzen.utils.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.ryzen.feature.impl.visual.EmotionsFeature;

@Environment(EnvType.CLIENT)
public final class EmotionAnimator {
   public static final String GREETING = "Приветствие";
   public static final String DANCE = "Танец";
   public static final String WANK = "Дрочка";
   public static final String ALPHA_WALK = "Альфа ходьба";
   public static final String ALPHA_MAN = "Альфа-Мужик";
   public static final String[] EMOTIONS = new String[]{"Приветствие", "Танец", "Дрочка", "Альфа ходьба", "Альфа-Мужик"};
   private static final float SWING_DAMP_THRESHOLD = 0.2F;
   private static final long LONG_CYCLE_MS = 10000L;
   private static final long DANCE_CYCLE_MS = 6000L;

   private EmotionAnimator() {
   }

   public static void apply(BipedEntityModel<?> model, BipedEntityRenderState state) {
      EmotionsFeature emotions = EmotionsFeature.getInstance();
      if (emotions != null && emotions.isEnabled()) {
         String preview = emotions.getPreviewEmotion();
         if (preview == null) {
            String selected = emotions.getSelectedEmotion();
            if (selected != null) {
               PlayerEntity player = resolvePlayer(state);
               if (player != null && emotions.appliesTo(player)) {
                  float swingDamp = swingDamp(player);
                  applyAnimatedPose(model, selected, state.limbSwingAnimationProgress, state.limbSwingAmplitude, swingDamp);
                  model.hat.setTransform(model.head.getTransform());
               }
            }
         } else if (isLocalPlayer(state)) {
            resetToBasePose(model);
            applyStillPose(model, preview, state);
            model.hat.setTransform(model.head.getTransform());
         }
      }
   }

   private static boolean isLocalPlayer(BipedEntityRenderState state) {
      if (state instanceof PlayerEntityRenderState avatar
         && MinecraftClient.getInstance().player != null
         && avatar.id == MinecraftClient.getInstance().player.getId()) {
         return true;
      }

      return false;
   }

   private static PlayerEntity resolvePlayer(BipedEntityRenderState state) {
      if (state instanceof PlayerEntityRenderState avatar && MinecraftClient.getInstance().world != null) {
         return MinecraftClient.getInstance().world.getEntityById(avatar.id) instanceof PlayerEntity player ? player : null;
      }

      return null;
   }

   private static float swingDamp(PlayerEntity player) {
      float damp = Math.max(1.0F, (float)player.getVelocity().lengthSquared() / 0.2F);
      return damp * damp * damp;
   }

   private static void resetToBasePose(BipedEntityModel<?> model) {
      for (ModelPart part : new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
         part.setAngles(0.0F, 0.0F, 0.0F);
      }

      model.head.originY = 0.0F;
      model.body.originY = 0.0F;
      model.rightArm.originX = -5.0F;
      model.rightArm.originY = 2.0F;
      model.rightArm.originZ = 0.0F;
      model.leftArm.originX = 5.0F;
      model.leftArm.originY = 2.0F;
      model.leftArm.originZ = 0.0F;
      model.rightLeg.originY = 12.0F;
      model.rightLeg.originZ = 0.1F;
      model.leftLeg.originY = 12.0F;
      model.leftLeg.originZ = 0.1F;
   }

   private static void applyStillPose(BipedEntityModel<?> model, String emotion, BipedEntityRenderState state) {
      switch (emotion) {
         case "Приветствие":
            model.rightArm.pitch = -3.0F;
            model.rightArm.roll = -0.5F;
            break;
         case "Танец":
            model.body.yaw = 0.2F;
            model.head.yaw = 0.3F;
            model.rightArm.pitch = -1.5F;
            model.rightArm.roll = 0.5F;
            model.leftArm.pitch = -1.2F;
            model.leftArm.roll = -0.5F;
            model.rightLeg.pitch = 0.3F;
            model.leftLeg.pitch = -0.3F;
            break;
         case "Дрочка":
            model.rightArm.pitch = -0.3F;
            model.rightArm.roll = -0.5F;
            model.head.pitch = 0.3F;
            break;
         case "Альфа ходьба":
            alphaWalk(model, state.limbSwingAnimationProgress, state.limbSwingAmplitude, 1.0F);
            break;
         case "Альфа-Мужик":
            alphaMan(model, state.limbSwingAnimationProgress, state.limbSwingAmplitude, 1.0F);
      }
   }

   private static void applyAnimatedPose(BipedEntityModel<?> model, String emotion, float walkPos, float walkSpeed, float damp) {
      float time = (float)(System.currentTimeMillis() % 10000L) / 1000.0F;
      switch (emotion) {
         case "Приветствие":
            waveArm(model, walkPos, walkSpeed, damp, time, 10.0F, 0.2F, -3.0F, false);
            break;
         case "Танец":
            dance(model);
            break;
         case "Дрочка":
            waveArm(model, walkPos, walkSpeed, damp, time, 30.0F, 0.2F, -1.1F, true);
            break;
         case "Альфа ходьба":
            alphaWalk(model, walkPos, walkSpeed, damp);
            break;
         case "Альфа-Мужик":
            alphaMan(model, walkPos, walkSpeed, damp);
      }
   }

   private static void waveArm(
      BipedEntityModel<?> model, float walkPos, float walkSpeed, float damp, float time, float frequency, float amplitude, float pitchBase, boolean swingPitch
   ) {
      float wave = MathHelper.sin((double)(time * frequency)) * amplitude;
      model.rightArm.pitch = swingPitch ? pitchBase + wave : pitchBase;
      model.rightArm.yaw = wave;
      model.rightArm.roll = -0.5F + wave;
      model.leftArm.pitch = 0.0F;
      model.rightLeg.pitch = MathHelper.cos((double)(walkPos * 0.6662F)) * 1.4F * walkSpeed / damp;
      model.leftLeg.pitch = MathHelper.cos((double)(walkPos * 0.6662F + (float) Math.PI)) * 1.4F * walkSpeed / damp;
      model.head.pitch = 0.0F;
   }

   private static void dance(BipedEntityModel<?> model) {
      float time = (float)(System.currentTimeMillis() % 6000L) / 1000.0F;
      model.body.yaw = MathHelper.sin((double)(time * 2.0F)) * 0.3F;
      model.body.pitch = MathHelper.sin((double)(time * 1.5F)) * 0.1F;
      model.head.yaw = MathHelper.sin((double)(time * 1.8F)) * 0.4F;
      model.head.pitch = MathHelper.sin((double)(time * 2.2F)) * 0.2F;
      model.rightArm.pitch = -1.5F + MathHelper.sin((double)(time * 3.0F)) * 0.8F;
      model.rightArm.yaw = MathHelper.sin((double)(time * 1.5F)) * 0.5F;
      model.rightArm.roll = MathHelper.sin((double)(time * 2.5F)) * 0.7F;
      model.leftArm.pitch = -1.5F + MathHelper.sin((double)(time * 3.0F + (float) Math.PI)) * 0.8F;
      model.leftArm.yaw = MathHelper.sin((double)(time * 1.5F + (float) Math.PI)) * 0.5F;
      model.leftArm.roll = MathHelper.sin((double)(time * 2.5F + (float) Math.PI)) * 0.7F;
      model.rightLeg.pitch = 0.5F + MathHelper.sin((double)(time * 2.0F)) * 0.6F;
      model.leftLeg.pitch = 0.5F + MathHelper.sin((double)(time * 2.0F + (float) Math.PI)) * 0.6F;
      if (time > 3.0F && time < 4.0F) {
         float lift = (time - 3.0F) * 2.0F;
         model.body.originY = lift;
         model.rightLeg.originY = 12.0F + lift;
         model.leftLeg.originY = 12.0F + lift;
         model.rightArm.originY = 2.0F + lift;
         model.leftArm.originY = 2.0F + lift;
      } else {
         model.body.originY = 0.0F;
         model.rightLeg.originY = 12.0F;
         model.leftLeg.originY = 12.0F;
         model.rightArm.originY = 2.0F;
         model.leftArm.originY = 2.0F;
      }

      if (time > 4.5F && time < 5.0F) {
         float dip = MathHelper.sin((double)((time - 4.5F) * (float) Math.PI * 2.0F));
         model.body.originY = -dip * 1.5F;
         model.head.originY = dip * 1.5F;
      }
   }

   private static void alphaWalk(BipedEntityModel<?> model, float walkPos, float walkSpeed, float damp) {
      model.rightArm.pitch = MathHelper.cos((double)(walkPos * 0.6662F + (float) Math.PI)) * 2.0F * walkSpeed / damp;
      model.leftArm.pitch = MathHelper.cos((double)(walkPos * 0.6662F)) * 2.0F * walkSpeed / damp;
      float armRoll = (MathHelper.cos((double)(walkPos * 0.2312F)) + 1.0F) * walkSpeed / damp;
      model.rightArm.roll = armRoll;
      model.leftArm.roll = -armRoll;
      model.rightArm.yaw = 0.0F;
      model.leftArm.yaw = 0.0F;
      model.rightLeg.pitch = MathHelper.cos((double)(walkPos * 0.6662F)) * 1.4F * walkSpeed / damp;
      model.leftLeg.pitch = MathHelper.cos((double)(walkPos * 0.6662F + (float) Math.PI)) * 1.4F * walkSpeed / damp;
      float legRoll = MathHelper.cos((double)(walkPos * 0.6662F)) * 0.4F * walkSpeed / damp;
      model.rightLeg.roll = legRoll;
      model.leftLeg.roll = -legRoll;
      model.body.pitch = 0.0F;
      model.body.yaw = 0.0F;
      model.body.roll = 0.0F;
   }

   private static void alphaMan(BipedEntityModel<?> model, float walkPos, float walkSpeed, float damp) {
      float time = (float)(System.currentTimeMillis() % 10000L) / 1000.0F;
      float swing = Math.max(0.25F, walkSpeed);
      model.body.pitch = -0.1F + MathHelper.sin((double)(time * 1.15F)) * 0.04F;
      model.body.roll = MathHelper.sin((double)(time * 2.1F)) * 0.14F;
      model.body.yaw = MathHelper.sin((double)(time * 1.4F)) * 0.09F;
      model.head.pitch = -0.18F + MathHelper.sin((double)(time * 1.05F)) * 0.05F;
      model.head.roll = MathHelper.sin((double)(time * 2.25F)) * 0.11F;
      model.head.yaw = MathHelper.sin((double)(time * 0.95F)) * 0.13F;
      float flexPeriod = 2.8F;
      float phase = time % flexPeriod / flexPeriod;
      if (phase < 0.32F) {
         float rise = MathHelper.sin((double)(phase / 0.32F * (float) Math.PI));
         model.rightArm.pitch = -0.55F - rise * 1.05F;
         model.leftArm.pitch = -0.55F - rise * 1.05F;
         model.rightArm.yaw = -0.55F * rise;
         model.leftArm.yaw = 0.55F * rise;
         model.rightArm.roll = -0.35F * rise;
         model.leftArm.roll = 0.35F * rise;
      } else {
         float beat = time * 2.0F;
         model.rightArm.pitch = -0.42F + MathHelper.sin((double)beat) * 0.32F;
         model.leftArm.pitch = -0.32F + MathHelper.sin((double)(beat + (float) Math.PI)) * 0.38F;
         model.rightArm.roll = 0.22F + MathHelper.cos((double)(beat * 0.65F)) * 0.22F;
         model.leftArm.roll = -0.28F + MathHelper.cos((double)(beat * 0.65F)) * 0.18F;
         model.rightArm.yaw = 0.28F + MathHelper.sin((double)beat) * 0.18F;
         model.leftArm.yaw = -0.32F + MathHelper.sin((double)beat) * 0.14F;
      }

      model.rightLeg.pitch = 0.12F + MathHelper.cos((double)(walkPos * 0.6662F)) * 1.15F * swing / damp + MathHelper.sin((double)(time * 3.2F)) * 0.07F;
      model.leftLeg.pitch = 0.1F
         + MathHelper.cos((double)(walkPos * 0.6662F + (float) Math.PI)) * 1.15F * swing / damp
         + MathHelper.sin((double)(time * 3.2F)) * 0.07F;
      model.rightLeg.roll = 0.1F + MathHelper.sin((double)(time * 2.05F)) * 0.07F;
      model.leftLeg.roll = -0.1F - MathHelper.sin((double)(time * 2.05F)) * 0.07F;
   }
}
