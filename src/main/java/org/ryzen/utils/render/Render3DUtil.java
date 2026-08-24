package org.ryzen.utils.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class Render3DUtil {
   private static final Matrix4f LEVEL_PROJECTION = new Matrix4f();
   private static boolean levelProjectionCaptured;

   private Render3DUtil() {
   }

   public static Vec3d interpolatedPosition(Entity entity, float tickDelta) {
      return new Vec3d(
         MathHelper.lerp((double)tickDelta, entity.lastRenderX, entity.getX()),
         MathHelper.lerp((double)tickDelta, entity.lastRenderY, entity.getY()),
         MathHelper.lerp((double)tickDelta, entity.lastRenderZ, entity.getZ())
      );
   }

   public static Matrix4f buildBillboardPose(Camera camera, Vec3d worldPos, double offsetY, float zRotationDegrees) {
      Vec3d cameraPos = camera.getCameraPos();
      MatrixStack poseStack = new MatrixStack();
      poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
      poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
      poseStack.translate(worldPos.x - cameraPos.x, worldPos.y - cameraPos.y + offsetY, worldPos.z - cameraPos.z);
      poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
      poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
      if (zRotationDegrees != 0.0F) {
         poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(zRotationDegrees));
      }

      return new Matrix4f(poseStack.peek().getPositionMatrix());
   }

   public static Matrix4f cameraViewPose(Camera camera) {
      return new Matrix4f().rotation(new Quaternionf(camera.getRotation()).conjugate());
   }

   public static Vector4f toViewSpace(Vec3d point, Vec3d cameraPos, Matrix4f pose) {
      return new Vector4f((float)(point.x - cameraPos.x), (float)(point.y - cameraPos.y), (float)(point.z - cameraPos.z), 1.0F).mul(pose);
   }

   public static void captureLevelProjection(Matrix4fc projection) {
      LEVEL_PROJECTION.set(projection);
      levelProjectionCaptured = true;
   }

   public static Matrix4f levelProjectionCopy() {
      return levelProjectionCaptured ? new Matrix4f(LEVEL_PROJECTION) : null;
   }

   public static Render3DUtil.ScreenPoint projectToScreen(MinecraftClient mc, Vec3d pos) {
      if (pos != null && mc.getWindow() != null && mc.gameRenderer != null && levelProjectionCaptured) {
         CameraRenderState cameraState = mc.gameRenderer.getEntityRenderStates().cameraRenderState;
         if (!cameraState.initialized) {
            return null;
         } else {
            int guiWidth = mc.getWindow().getScaledWidth();
            int guiHeight = mc.getWindow().getScaledHeight();
            if (guiWidth > 0 && guiHeight > 0) {
               Vector4f clip = new Vector4f(
                  (float)(pos.x - cameraState.pos.getX()), (float)(pos.y - cameraState.pos.getY()), (float)(pos.z - cameraState.pos.getZ()), 1.0F
               );
               new Quaternionf(cameraState.orientation).conjugate().transform(clip);
               LEVEL_PROJECTION.transform(clip);
               if (clip.w <= 1.0E-4F) {
                  return null;
               } else {
                  float ndcX = clip.x / clip.w;
                  float ndcY = clip.y / clip.w;
                  if (!(Math.abs(ndcX) > 2.0F) && !(Math.abs(ndcY) > 2.0F)) {
                     float screenX = (ndcX + 1.0F) * 0.5F * (float)guiWidth;
                     float screenY = (1.0F - ndcY) * 0.5F * (float)guiHeight;
                     return new Render3DUtil.ScreenPoint(screenX, screenY);
                  } else {
                     return null;
                  }
               }
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static Render3DUtil.ScreenBounds includeInBounds(MinecraftClient mc, Render3DUtil.ScreenBounds bounds, double x, double y, double z) {
      Render3DUtil.ScreenPoint point = projectToScreen(mc, new Vec3d(x, y, z));
      if (point == null) {
         return bounds;
      } else {
         return bounds == null
            ? new Render3DUtil.ScreenBounds(point.x(), point.y(), point.x(), point.y())
            : new Render3DUtil.ScreenBounds(
               Math.min(bounds.minX(), point.x()), Math.min(bounds.minY(), point.y()), Math.max(bounds.maxX(), point.x()), Math.max(bounds.maxY(), point.y())
            );
      }
   }

   public static Render3DUtil.ScreenBounds projectBoxBounds(MinecraftClient mc, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      Render3DUtil.ScreenBounds bounds = null;
      bounds = includeInBounds(mc, bounds, minX, minY, minZ);
      bounds = includeInBounds(mc, bounds, minX, minY, maxZ);
      bounds = includeInBounds(mc, bounds, minX, maxY, minZ);
      bounds = includeInBounds(mc, bounds, minX, maxY, maxZ);
      bounds = includeInBounds(mc, bounds, maxX, minY, minZ);
      bounds = includeInBounds(mc, bounds, maxX, minY, maxZ);
      bounds = includeInBounds(mc, bounds, maxX, maxY, minZ);
      return includeInBounds(mc, bounds, maxX, maxY, maxZ);
   }

   @Environment(EnvType.CLIENT)
   public static record ScreenBounds(float minX, float minY, float maxX, float maxY) {
      public float width() {
         return this.maxX - this.minX;
      }

      public float height() {
         return this.maxY - this.minY;
      }
   }

   @Environment(EnvType.CLIENT)
   public static record ScreenPoint(float x, float y) {
   }
}
