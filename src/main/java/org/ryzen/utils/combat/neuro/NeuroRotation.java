package org.ryzen.utils.combat.neuro;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.RotationContext;
import org.ryzen.utils.combat.rotations.AuraRotation;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class NeuroRotation implements AuraRotation {
   private static final float MAX_YAW_STEP = 90.0F;
   private static final float MAX_PITCH_STEP = 30.0F;
   private static final float JITTER_SCALE = 1.0F;
   private static final float TRACK_SCALE = 0.5F;
   private NeuroRotationData data;
   private int cursor;
   private float lastSampleYaw;
   private float lastSamplePitch;
   private boolean warnedEmpty;

   public void setData(NeuroRotationData data) {
      this.data = data;
      this.cursor = 0;
      this.warnedEmpty = false;
      if (data != null && data.sampleCount() > 0) {
         NeuroSample first = data.samples().get(0);
         this.lastSampleYaw = first.yaw();
         this.lastSamplePitch = first.pitch();
      }
   }

   public NeuroRotationData data() {
      return this.data;
   }

   public boolean hasData() {
      return this.data != null && this.data.sampleCount() > 0;
   }

   @Override
   public void tick(ClientPlayerEntity player, LivingEntity target, Vec3d targetEyePos, boolean attackLikely) {
      if (!this.hasData()) {
         if (!this.warnedEmpty) {
            this.warnedEmpty = true;
            ChatUtil.error("Нет нейро-ротации  •  Запиши через .ai и выбери .ai select <name>");
         }

         RotationContext.rotateToPosition(player, targetEyePos);
      } else {
         float fromYaw = player.getYaw();
         float fromPitch = player.getPitch();
         NeuroRotation.Rotation goal = toPoint(player, targetEyePos);
         NeuroSample sample = this.data.samples().get(this.cursor);
         this.cursor = (this.cursor + 1) % this.data.sampleCount();
         float sampleYawDelta = MathHelper.wrapDegrees(sample.yaw() - this.lastSampleYaw);
         float samplePitchDelta = sample.pitch() - this.lastSamplePitch;
         this.lastSampleYaw = sample.yaw();
         this.lastSamplePitch = sample.pitch();
         float goalYaw = goal.yaw();
         float goalPitch = goal.pitch();
         float scale = attackLikely ? 1.0F : 0.5F;
         goalYaw += MathHelper.clamp(sampleYawDelta, -25.0F, 25.0F) * scale;
         goalPitch += MathHelper.clamp(samplePitchDelta, -15.0F, 15.0F) * scale;
         float yaw = moveTowardsAngle(fromYaw, goalYaw, 90.0F);
         float pitch = fromPitch + MathHelper.clamp(goalPitch - fromPitch, -30.0F, 30.0F);
         NeuroRotation.Rotation corrected = correctRotation(fromYaw, fromPitch, yaw, pitch);
         RotationContext.setRotation(corrected.yaw(), corrected.pitch());
      }
   }

   @Override
   public void reset() {
      this.cursor = 0;
      this.warnedEmpty = false;
      if (this.hasData()) {
         NeuroSample first = this.data.samples().get(0);
         this.lastSampleYaw = first.yaw();
         this.lastSamplePitch = first.pitch();
      }
   }

   private static NeuroRotation.Rotation toPoint(ClientPlayerEntity player, Vec3d point) {
      Vec3d eye = player.getEyePos();
      double dx = point.x - eye.x;
      double dy = point.y - eye.y;
      double dz = point.z - eye.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
      float pitch = MathHelper.clamp((float)(-Math.toDegrees(Math.atan2(dy, horizontal))), -90.0F, 90.0F);
      return new NeuroRotation.Rotation(yaw, pitch);
   }

   private static float moveTowardsAngle(float current, float target, float maxStep) {
      float difference = MathHelper.wrapDegrees(target - current);
      return Math.abs(difference) <= maxStep ? target : current + Math.signum(difference) * maxStep;
   }

   private static NeuroRotation.Rotation correctRotation(float fromYaw, float fromPitch, float toYaw, float toPitch) {
      float step = gcdStep();
      float deltaYaw = MathHelper.wrapDegrees(toYaw - fromYaw);
      float deltaPitch = toPitch - fromPitch;
      if (step > 0.0F) {
         deltaYaw = (float)Math.round(deltaYaw / step) * step;
         deltaPitch = (float)Math.round(deltaPitch / step) * step;
      }

      return new NeuroRotation.Rotation(fromYaw + deltaYaw, MathHelper.clamp(fromPitch + deltaPitch, -90.0F, 90.0F));
   }

   private static float gcdStep() {
      double sensitivity = (Double)MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
      if (sensitivity <= 0.0) {
         return 0.0F;
      } else {
         double factor = sensitivity * 0.6 + 0.2;
         return (float)(factor * factor * factor * 1.2);
      }
   }

   @Environment(EnvType.CLIENT)
   private static record Rotation(float yaw, float pitch) {
   }
}
