package org.ryzen.utils.render.particles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.Custom;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.LayeredCustom;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState.LeashData;
import net.minecraft.client.render.entity.state.EntityRenderState.ShadowPiece;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.ryzen.utils.render.EntityEspDispatcherBridge;

@Environment(EnvType.CLIENT)
public final class ModelPointSampler implements OrderedRenderCommandQueue {
   private final List<Vector3f[]> quads = new ArrayList<>();
   private final ModelPointSampler.CapturingConsumer consumer = new ModelPointSampler.CapturingConsumer();

   private ModelPointSampler() {
   }

   public static List<Vector3f> sample(EntityRenderState state, CameraRenderState cameraState, int budget, Random random) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (cameraState != null && mc.player != null && mc.getEntityRenderDispatcher() instanceof EntityEspDispatcherBridge bridge) {
         mc.getEntityRenderDispatcher().configure(mc.gameRenderer.getCamera(), (Entity)(mc.targetedEntity != null ? mc.targetedEntity : mc.player));
         ModelPointSampler sampler = new ModelPointSampler();

         try {
            bridge.submitForGlow(state, cameraState, 0.0, 0.0, 0.0, new MatrixStack(), sampler);
         } catch (Exception var8) {
            return List.of();
         }

         return sampler.scatterPoints(budget, random);
      } else {
         return List.of();
      }
   }

   private List<Vector3f> scatterPoints(int budget, Random random) {
      if (this.quads.isEmpty()) {
         return List.of();
      } else {
         float totalArea = 0.0F;
         float[] areas = new float[this.quads.size()];

         for (int i = 0; i < this.quads.size(); i++) {
            areas[i] = quadArea(this.quads.get(i));
            totalArea += areas[i];
         }

         if (totalArea <= 1.0E-4F) {
            return List.of();
         } else {
            List<Vector3f> points = new ArrayList<>(budget);

            for (int i = 0; i < this.quads.size(); i++) {
               Vector3f[] quad = this.quads.get(i);
               float share = areas[i] / totalArea * (float)budget;
               int count = (int)share + (random.nextFloat() < share - (float)((int)share) ? 1 : 0);

               for (int p = 0; p < count; p++) {
                  float u = random.nextFloat();
                  float v = random.nextFloat();
                  points.add(bilinear(quad, u, v));
               }
            }

            return points;
         }
      }
   }

   private static float quadArea(Vector3f[] quad) {
      Vector3f edge1 = new Vector3f(quad[1]).sub(quad[0]);
      Vector3f edge2 = new Vector3f(quad[3]).sub(quad[0]);
      return new Vector3f(edge1).cross(edge2).length();
   }

   private static Vector3f bilinear(Vector3f[] quad, float u, float v) {
      Vector3f top = new Vector3f(quad[0]).lerp(quad[1], u);
      Vector3f bottom = new Vector3f(quad[3]).lerp(quad[2], u);
      return top.lerp(bottom, v);
   }

   public RenderCommandQueue getBatchingQueue(int order) {
      return this;
   }

   public <S> void submitModel(
      Model<? super S> model,
      S state,
      MatrixStack matrices,
      RenderLayer renderLayer,
      int light,
      int overlay,
      int tintedColor,
      Sprite sprite,
      int outlineColor,
      CrumblingOverlayCommand crumblingOverlay
   ) {
      model.setAngles(state);
      model.render(matrices, this.consumer, light, overlay, -1);
      this.consumer.finishQuad();
      model.resetTransforms();
   }

   public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<ShadowPiece> shadowPieces) {
   }

   public void submitLabel(
      MatrixStack matrices,
      Vec3d nameLabelPos,
      int y,
      Text label,
      boolean notSneaking,
      int light,
      double squaredDistanceToCamera,
      CameraRenderState cameraState
   ) {
   }

   public void submitText(
      MatrixStack matrices,
      float x,
      float y,
      OrderedText text,
      boolean dropShadow,
      TextLayerType layerType,
      int light,
      int color,
      int backgroundColor,
      int outlineColor
   ) {
   }

   public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation) {
   }

   public void submitLeash(MatrixStack matrices, LeashData leashData) {
   }

   public void submitModelPart(
      ModelPart part,
      MatrixStack matrices,
      RenderLayer renderLayer,
      int light,
      int overlay,
      Sprite sprite,
      boolean sheeted,
      boolean hasGlint,
      int tintedColor,
      CrumblingOverlayCommand crumblingOverlay,
      int outlineColor
   ) {
   }

   public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
   }

   public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
   }

   public void submitBlockStateModel(
      MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor
   ) {
   }

   public void submitItem(
      MatrixStack matrices,
      ItemDisplayContext displayContext,
      int light,
      int overlay,
      int outlineColors,
      int[] tintLayers,
      List<BakedQuad> quads,
      RenderLayer renderLayer,
      Glint glintType
   ) {
   }

   public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, Custom customRenderer) {
   }

   public void submitCustom(LayeredCustom customRenderer) {
   }

   @Environment(EnvType.CLIENT)
   private final class CapturingConsumer implements VertexConsumer {
      private final Vector3f[] pending = new Vector3f[4];
      private int pendingCount;

      public VertexConsumer vertex(float x, float y, float z) {
         this.pending[this.pendingCount++] = new Vector3f(x, y, z);
         if (this.pendingCount == 4) {
            ModelPointSampler.this.quads.add((Vector3f[])this.pending.clone());
            this.pendingCount = 0;
         }

         return this;
      }

      private void finishQuad() {
         this.pendingCount = 0;
      }

      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this;
      }

      public VertexConsumer color(int argb) {
         return this;
      }

      public VertexConsumer texture(float u, float v) {
         return this;
      }

      public VertexConsumer overlay(int u, int v) {
         return this;
      }

      public VertexConsumer light(int u, int v) {
         return this;
      }

      public VertexConsumer normal(float x, float y, float z) {
         return this;
      }

      public VertexConsumer lineWidth(float width) {
         return this;
      }
   }
}
