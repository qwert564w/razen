package org.ryzen.feature.impl.visual;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.FriendManager;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.world.WorldMeshRenderer;

@Environment(EnvType.CLIENT)
public final class SkeletonEspFeature extends Feature implements MinecraftContext {
   private static final float HEAD_BONE = -0.32F;
   private static final float BODY_BONE = 0.52F;
   private static final float LIMB_BONE = 0.52F;
   private static final float MODEL_SCALE = 0.0625F;
   private static final float MODEL_OFFSET_Y = -1.501F;
   public final ColorSetting playerColor = this.register(new ColorSetting("Player Color", -1));
   public final ColorSetting friendColor = this.register(new ColorSetting("Friend Color", -16711816));
   public final BooleanSetting throughWalls = this.register(new BooleanSetting("Through Walls", true));
   public final NumberSetting range = this.register(new NumberSetting("Range", 96.0, 16.0, 192.0, 4.0, " blocks"));
   private PlayerEntityModel model;
   private PlayerEntityRenderState renderState;

   public SkeletonEspFeature() {
      super("SkeletonESP", "Draws a stick figure over nearby players", FeatureCategory.VISUAL, -1);
   }

   public static SkeletonEspFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(SkeletonEspFeature.class);
   }

   @Override
   protected void onDisable() {
      this.release();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.release();
   }

   private void release() {
      this.model = null;
      this.renderState = null;
   }

   public void renderWorld(float tickDelta) {
      if (this.inGame()) {
         this.ensureModel();
         if (this.model != null) {
            double maxDistance = this.range.getValue();
            double maxDistanceSquared = maxDistance * maxDistance;
            List<WorldMeshRenderer.Line> lines = new ArrayList<>();

            for (PlayerEntity other : this.level().getPlayers()) {
               if (this.isTarget(other, maxDistanceSquared)) {
                  this.appendSkeleton(lines, other, tickDelta);
               }
            }

            if (!lines.isEmpty()) {
               WorldMeshRenderer.render(new WorldMeshRenderer.WorldMesh(lines, List.of(), List.of()), this.throughWalls.getValue());
            }
         }
      }
   }

   private boolean isTarget(PlayerEntity other, double maxDistanceSquared) {
      return other != null
         && other != this.player()
         && other.isAlive()
         && !other.isSpectator()
         && !other.isInvisible()
         && this.player().squaredDistanceTo(other) <= maxDistanceSquared;
   }

   private int colorOf(PlayerEntity other) {
      return FriendManager.INSTANCE.isFriend(other.getName().getString()) ? this.friendColor.getValue() : this.playerColor.getValue();
   }

   private void ensureModel() {
      if (this.model == null) {
         if (mc.getLoadedEntityModels() != null) {
            this.model = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
            this.renderState = new PlayerEntityRenderState();
         }
      }
   }

   private void appendSkeleton(List<WorldMeshRenderer.Line> lines, PlayerEntity other, float tickDelta) {
      this.poseModel(other, tickDelta);
      Vec3d position = Render3DUtil.interpolatedPosition(other, tickDelta);
      Matrix4f pose = new Matrix4f()
         .translate((float)position.x, (float)position.y, (float)position.z)
         .rotateY((float)Math.toRadians((double)(180.0F - this.renderState.bodyYaw)))
         .scale(-1.0F, -1.0F, 1.0F)
         .translate(0.0F, -1.501F, 0.0F);
      int color = this.colorOf(other);
      this.appendBone(lines, pose, this.model.head, -0.32F, color);
      this.appendBone(lines, pose, this.model.body, 0.52F, color);
      this.appendBone(lines, pose, this.model.rightArm, 0.52F, color);
      this.appendBone(lines, pose, this.model.leftArm, 0.52F, color);
      this.appendBone(lines, pose, this.model.rightLeg, 0.52F, color);
      this.appendBone(lines, pose, this.model.leftLeg, 0.52F, color);
   }

   private void poseModel(PlayerEntity other, float tickDelta) {
      PlayerEntityRenderState state = this.renderState;
      state.bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, other.lastBodyYaw, other.bodyYaw);
      state.relativeHeadYaw = MathHelper.lerpAngleDegrees(tickDelta, other.lastHeadYaw, other.headYaw) - state.bodyYaw;
      state.pitch = MathHelper.lerp(tickDelta, other.lastPitch, other.getPitch());
      state.limbSwingAnimationProgress = other.limbAnimator.getAnimationProgress(tickDelta);
      state.limbSwingAmplitude = other.limbAnimator.getAmplitude(tickDelta);
      state.isInSneakingPose = other.isInSneakingPose();
      state.isGliding = other.isGliding();
      state.isSwimming = other.isInSwimmingPose();
      state.hasVehicle = other.hasVehicle();
      state.isUsingItem = false;
      state.preferredArm = Arm.RIGHT;
      state.activeHand = Hand.MAIN_HAND;
      state.pose = other.getPose() == null ? EntityPose.STANDING : other.getPose();
      state.equippedHeadStack = ItemStack.EMPTY;
      state.equippedChestStack = ItemStack.EMPTY;
      state.equippedLegsStack = ItemStack.EMPTY;
      state.equippedFeetStack = ItemStack.EMPTY;
      state.ageScale = 1.0F;
      state.baseScale = 1.0F;
      this.model.setAngles(state);
   }

   private void appendBone(List<WorldMeshRenderer.Line> lines, Matrix4f pose, ModelPart part, float length, int color) {
      Matrix4f bone = new Matrix4f(pose)
         .translate(part.originX * 0.0625F, part.originY * 0.0625F, part.originZ * 0.0625F)
         .rotateZ(part.roll)
         .rotateY(part.yaw)
         .rotateX(part.pitch);
      lines.add(new WorldMeshRenderer.Line(transform(bone, 0.0F, 0.0F, 0.0F), transform(bone, 0.0F, length, 0.0F), color));
   }

   private static Vec3d transform(Matrix4f matrix, float x, float y, float z) {
      Vector4f point = matrix.transform(new Vector4f(x, y, z, 1.0F));
      return new Vec3d((double)point.x, (double)point.y, (double)point.z);
   }
}
