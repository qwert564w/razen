package org.ryzen.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryStack;
import org.ryzen.feature.impl.visual.BlockOutlineFeature;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.post.PostFx;
import org.ryzen.utils.render.post.PostPipelines;

@Environment(EnvType.CLIENT)
public final class BlockOutlineRenderer {
   private static final float BOX_EPSILON = 0.0025F;
   private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();
   private static final int STYLE_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
   private final GpuBuffer transformUniforms = uniformBuffer("Ryzen Block Outline Transform UBO", TRANSFORM_SIZE);
   private final GpuBuffer styleUniforms = uniformBuffer("Ryzen Block Outline Style UBO", STYLE_SIZE);
   private BlockPos selectedPos;
   private BlockState selectedState;
   private long lastFrameNanos;
   private float transition;
   private GpuBuffer cachedVertexBuffer;
   private int cachedVertexCount;

   public void render(BlockOutlineFeature feature, CameraRenderState cameraState) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      if (minecraft.world != null
         && minecraft.gameRenderer != null
         && cameraState != null
         && cameraState.initialized
         && minecraft.crosshairTarget instanceof BlockHitResult hit
         && hit.getType() == Type.BLOCK) {
         BlockPos blockPos = hit.getBlockPos();
         BlockState state = minecraft.world.getBlockState(blockPos);
         if (state.isAir()) {
            this.resetSelection();
         } else {
            boolean selectionChanged = !blockPos.equals(this.selectedPos);
            if (selectionChanged) {
               this.selectedPos = blockPos.toImmutable();
               this.transition = 0.0F;
               this.lastFrameNanos = System.nanoTime();
            }

            this.advanceTransition(feature.animationSpeed.getValue().floatValue());
            if (selectionChanged || state != this.selectedState || this.cachedVertexBuffer == null) {
               this.selectedState = state;
               this.rebuildMesh(minecraft, blockPos, state);
            }

            if (this.cachedVertexBuffer != null && this.cachedVertexCount != 0) {
               Framebuffer target = minecraft.getFramebuffer();
               if (target != null && target.getColorAttachmentView() != null && target.getDepthAttachmentView() != null) {
                  float eased = 1.0F - (float)Math.pow((double)(1.0F - this.transition), 3.0);
                  float scale = 0.92F + eased * 0.08F;
                  Matrix4f projection = Render3DUtil.levelProjectionCopy();
                  if (projection != null) {
                     Matrix4f modelViewProjection = projection.mul(new Matrix4f().rotation(new Quaternionf(cameraState.orientation).conjugate()))
                        .translate(
                           (float)((double)blockPos.getX() - cameraState.pos.getX() + 0.5),
                           (float)((double)blockPos.getY() - cameraState.pos.getY() + 0.5),
                           (float)((double)blockPos.getZ() - cameraState.pos.getZ() + 0.5)
                        )
                        .scale(scale)
                        .translate(-0.5F, -0.5F, -0.5F);
                     this.writeUniforms(feature, modelViewProjection, target.textureWidth, target.textureHeight, eased);
                     RenderPass pass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                           () -> "Ryzen Block Outline",
                           target.getColorAttachmentView(),
                           OptionalInt.empty(),
                           target.getDepthAttachmentView(),
                           OptionalDouble.empty()
                        );

                     try {
                        pass.setPipeline(this.pipeline(feature));
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("BlockOutlineTransform", this.transformUniforms);
                        pass.setUniform("BlockOutlineStyle", this.styleUniforms);
                        pass.setVertexBuffer(0, this.cachedVertexBuffer);
                        pass.draw(0, this.cachedVertexCount);
                     } finally {
                        pass.close();
                     }
                  }
               }
            }
         }
      } else {
         this.resetSelection();
      }
   }

   private void rebuildMesh(MinecraftClient minecraft, BlockPos blockPos, BlockState state) {
      this.releaseMesh();
      List<Box> boxes = state.getOutlineShape(minecraft.world, blockPos).getBoundingBoxes();
      if (boxes.isEmpty()) {
         boxes = List.of(new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
      }

      BuiltBuffer mesh = this.buildMesh(boxes);
      if (mesh != null) {
         BuiltBuffer var6 = mesh;

         try {
            this.cachedVertexCount = mesh.getDrawParameters().vertexCount();
            this.cachedVertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Ryzen Block Outline Vertices", 32, mesh.getBuffer());
         } catch (Throwable var10) {
            if (mesh != null) {
               try {
                  var6.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (mesh != null) {
            mesh.close();
         }
      }
   }

   private void releaseMesh() {
      if (this.cachedVertexBuffer != null) {
         this.cachedVertexBuffer.close();
         this.cachedVertexBuffer = null;
      }

      this.cachedVertexCount = 0;
   }

   public void resetSelection() {
      this.selectedPos = null;
      this.selectedState = null;
      this.transition = 0.0F;
      this.lastFrameNanos = 0L;
      this.releaseMesh();
   }

   public void release() {
      this.resetSelection();
      this.transformUniforms.close();
      this.styleUniforms.close();
   }

   private void advanceTransition(float speed) {
      long now = System.nanoTime();
      if (this.lastFrameNanos == 0L) {
         this.lastFrameNanos = now;
      } else {
         float seconds = Math.min(0.1F, (float)(now - this.lastFrameNanos) / 1.0E9F);
         this.lastFrameNanos = now;
         this.transition = Math.min(1.0F, this.transition + seconds * speed * 0.12F);
      }
   }

   private void writeUniforms(BlockOutlineFeature feature, Matrix4f modelViewProjection, int width, int height, float alpha) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer transform = Std140Builder.onStack(stack, TRANSFORM_SIZE).putMat4f(modelViewProjection).get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.transformUniforms.slice(), transform);
         int tint = feature.resolvedTint();
         ByteBuffer style = Std140Builder.onStack(stack, STYLE_SIZE)
            .putVec4((float)ColorUtil.red(tint) / 255.0F, (float)ColorUtil.green(tint) / 255.0F, (float)ColorUtil.blue(tint) / 255.0F, alpha * 0.82F)
            .putVec4(
               (float)width, (float)height, PostFx.shaderTime() * feature.shaderSpeed.getValue().floatValue(), feature.shaderIntensity.getValue().floatValue()
            )
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.styleUniforms.slice(), style);
      } catch (Throwable var11) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }
         }

         throw var11;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private RenderPipeline pipeline(BlockOutlineFeature feature) {
      boolean through = feature.ignoreDepth.getValue();
      String var3 = feature.variant.getValue();

      return switch (var3) {
         case "Water Caustics" -> through ? PostPipelines.BLOCK_OUTLINE_CAUSTICS_THROUGH : PostPipelines.BLOCK_OUTLINE_CAUSTICS;
         case "Prismatic Flow" -> through ? PostPipelines.BLOCK_OUTLINE_PRISMATIC_THROUGH : PostPipelines.BLOCK_OUTLINE_PRISMATIC;
         case "Glossy Gradients" -> through ? PostPipelines.BLOCK_OUTLINE_GLOSSY_THROUGH : PostPipelines.BLOCK_OUTLINE_GLOSSY;
         case "Deep Space" -> through ? PostPipelines.BLOCK_OUTLINE_DEEP_SPACE_THROUGH : PostPipelines.BLOCK_OUTLINE_DEEP_SPACE;
         case "Nebula" -> through ? PostPipelines.BLOCK_OUTLINE_NEBULA_THROUGH : PostPipelines.BLOCK_OUTLINE_NEBULA;
         default -> through ? PostPipelines.BLOCK_OUTLINE_CLASSIC_THROUGH : PostPipelines.BLOCK_OUTLINE_CLASSIC;
      };
   }

   private BuiltBuffer buildMesh(List<Box> boxes) {
      int vertexCount = boxes.size() * 36;
      if (vertexCount == 0) {
         return null;
      } else {
         BufferBuilder builder = new BufferBuilder(
            new BufferAllocator(Math.max(256, vertexCount * VertexFormats.POSITION.getVertexSize())), DrawMode.TRIANGLES, VertexFormats.POSITION
         );

         for (Box box : boxes) {
            float minX = (float)box.minX - 0.0025F;
            float minY = (float)box.minY - 0.0025F;
            float minZ = (float)box.minZ - 0.0025F;
            float maxX = (float)box.maxX + 0.0025F;
            float maxY = (float)box.maxY + 0.0025F;
            float maxZ = (float)box.maxZ + 0.0025F;
            this.face(builder, minX, minY, minZ, maxX, maxY, minZ);
            this.face(builder, maxX, minY, maxZ, minX, maxY, maxZ);
            this.face(builder, minX, minY, maxZ, minX, maxY, minZ);
            this.face(builder, maxX, minY, minZ, maxX, maxY, maxZ);
            this.face(builder, minX, maxY, minZ, maxX, maxY, maxZ);
            this.face(builder, minX, minY, maxZ, maxX, minY, minZ);
         }

         return builder.end();
      }
   }

   private void face(BufferBuilder builder, float x1, float y1, float z1, float x2, float y2, float z2) {
      boolean constantX = x1 == x2;
      boolean constantY = y1 == y2;
      if (constantX) {
         this.vertex(builder, x1, y1, z1);
         this.vertex(builder, x1, y1, z2);
         this.vertex(builder, x1, y2, z2);
         this.vertex(builder, x1, y2, z2);
         this.vertex(builder, x1, y2, z1);
         this.vertex(builder, x1, y1, z1);
      } else if (constantY) {
         this.vertex(builder, x1, y1, z1);
         this.vertex(builder, x2, y1, z1);
         this.vertex(builder, x2, y1, z2);
         this.vertex(builder, x2, y1, z2);
         this.vertex(builder, x1, y1, z2);
         this.vertex(builder, x1, y1, z1);
      } else {
         this.vertex(builder, x1, y1, z1);
         this.vertex(builder, x2, y1, z1);
         this.vertex(builder, x2, y2, z1);
         this.vertex(builder, x2, y2, z1);
         this.vertex(builder, x1, y2, z1);
         this.vertex(builder, x1, y1, z1);
      }
   }

   private void vertex(BufferBuilder builder, float x, float y, float z) {
      builder.vertex(x, y, z);
   }

   private static GpuBuffer uniformBuffer(String label, int size) {
      return RenderSystem.getDevice().createBuffer(() -> label, 136, (long)size);
   }
}
