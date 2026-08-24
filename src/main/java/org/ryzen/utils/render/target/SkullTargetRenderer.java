package org.ryzen.utils.render.target;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.HurtUtil;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.Textures;

@Environment(EnvType.CLIENT)
public final class SkullTargetRenderer {
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();
   private static final RenderPipeline PIPELINE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.of("ryzen:pipeline/world/target_skull"))
      .withVertexShader(Identifier.of("ryzen:core/aura_marker"))
      .withFragmentShader(Identifier.of("ryzen:core/aura_marker"))
      .withUniform("Projection", UniformType.UNIFORM_BUFFER)
      .withSampler("texSampler")
      .withUniform("params", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withVertexFormat(VertexFormats.POSITION_TEXTURE, DrawMode.TRIANGLE_STRIP)
      .withCull(false)
      .build();
   private GpuBuffer paramsBuffer;
   private LivingEntity lastTarget;
   private float alpha;
   private long lastFrameTime;

   public void render(LivingEntity activeTarget, float tickDelta, int baseColor) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.player != null && mc.world != null && mc.gameRenderer != null) {
         this.updateAnimation(activeTarget);
         LivingEntity target = this.lastTarget;
         if (target != null) {
            if (this.alpha <= 0.01F) {
               this.reset();
            } else {
               SkullTargetRenderer.SkullGeometry geometry = this.skullGeometry(mc, target, tickDelta);
               if (geometry != null) {
                  this.renderSkull(mc, geometry, HurtUtil.blend(baseColor, target, this.alpha));
               }
            }
         }
      } else {
         this.reset();
      }
   }

   public void reset() {
      this.lastTarget = null;
      this.alpha = 0.0F;
      this.lastFrameTime = 0L;
   }

   public void release() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   private void updateAnimation(LivingEntity activeTarget) {
      long now = System.currentTimeMillis();
      float delta = this.lastFrameTime == 0L ? 0.016F : (float)Math.min(100L, now - this.lastFrameTime) / 1000.0F;
      this.lastFrameTime = now;
      if (valid(activeTarget)) {
         this.lastTarget = activeTarget;
      }

      float targetAlpha = valid(activeTarget) ? 1.0F : 0.0F;
      float step = MathHelper.clamp(delta * 8.0F, 0.0F, 1.0F);
      this.alpha = this.alpha + (targetAlpha - this.alpha) * step;
   }

   private SkullTargetRenderer.SkullGeometry skullGeometry(MinecraftClient mc, LivingEntity target, float tickDelta) {
      Camera camera = mc.gameRenderer.getCamera();
      if (camera != null && camera.isReady()) {
         Vec3d position = Render3DUtil.interpolatedPosition(target, tickDelta);
         float widthScale;
         if (target.getWidth() < 1.0F) {
            widthScale = 0.95F;
         } else if (target.getWidth() > 2.0F) {
            widthScale = 1.45F;
         } else {
            widthScale = 1.0F;
         }

         float halfSize = 0.6F * widthScale * this.alpha * HurtUtil.scale(target, 0.12F);
         if (halfSize <= 0.001F) {
            return null;
         } else {
            Matrix4f pose = Render3DUtil.buildBillboardPose(camera, position, (double)target.getHeight() * 0.9, 0.0F);
            return new SkullTargetRenderer.SkullGeometry(pose, halfSize);
         }
      } else {
         return null;
      }
   }

   private void renderSkull(MinecraftClient mc, SkullTargetRenderer.SkullGeometry geometry, int color) {
      Framebuffer target = mc.getFramebuffer();
      GpuTextureView colorView = target != null ? target.getColorAttachmentView() : null;
      if (colorView != null) {
         AbstractTexture texture = mc.getTextureManager().getTexture(Textures.TARGET_SKULL);
         GpuTextureView textureView = texture != null ? texture.getGlTextureView() : null;
         if (textureView != null) {
            this.ensureParamsBuffer();
            this.writeParams(color);
            BuiltBuffer mesh = this.buildMesh(geometry);
            GpuBuffer vertexBuffer = null;

            try {
               vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Ryzen Skull Target Vertices", 32, mesh.getBuffer());
               GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
               RenderPass pass = RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(() -> "Ryzen Skull Target Pass", colorView, OptionalInt.empty());

               try {
                  pass.setPipeline(PIPELINE);
                  pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                  pass.setUniform("params", this.paramsBuffer);
                  pass.bindTexture("texSampler", textureView, sampler);
                  pass.setVertexBuffer(0, vertexBuffer);
                  pass.draw(0, 4);
               } catch (Throwable var19) {
                  if (pass != null) {
                     try {
                        pass.close();
                     } catch (Throwable var18) {
                        var19.addSuppressed(var18);
                     }
                  }

                  throw var19;
               }

               if (pass != null) {
                  pass.close();
               }
            } finally {
               if (vertexBuffer != null) {
                  vertexBuffer.close();
               }

               mesh.close();
            }
         }
      }
   }

   private BuiltBuffer buildMesh(SkullTargetRenderer.SkullGeometry geometry) {
      float halfSize = geometry.halfSize;
      BufferBuilder builder = new BufferBuilder(
         BufferAllocator.fixedSized(4 * VertexFormats.POSITION_TEXTURE.getVertexSize()), DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_TEXTURE
      );
      builder.vertex(geometry.pose, -halfSize, -halfSize, 0.0F).texture(0.0F, 1.0F);
      builder.vertex(geometry.pose, -halfSize, halfSize, 0.0F).texture(0.0F, 0.0F);
      builder.vertex(geometry.pose, halfSize, -halfSize, 0.0F).texture(1.0F, 1.0F);
      builder.vertex(geometry.pose, halfSize, halfSize, 0.0F).texture(1.0F, 0.0F);
      return builder.end();
   }

   private void ensureParamsBuffer() {
      if (this.paramsBuffer == null) {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> "Ryzen Skull Target UBO", 136, (long)UNIFORM_SIZE);
      }
   }

   private void writeParams(int color) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putVec4(
               (float)ColorUtil.red(color) / 255.0F,
               (float)ColorUtil.green(color) / 255.0F,
               (float)ColorUtil.blue(color) / 255.0F,
               (float)ColorUtil.alpha(color) / 255.0F
            )
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.paramsBuffer.slice(), data);
      } catch (Throwable var6) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }
         }

         throw var6;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private static boolean valid(LivingEntity entity) {
      return entity != null && entity.isAlive() && !entity.isRemoved();
   }

   @Environment(EnvType.CLIENT)
   private static record SkullGeometry(Matrix4f pose, float halfSize) {
   }
}
