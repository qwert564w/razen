package org.ryzen.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryStack;
import org.ryzen.feature.impl.player.FullBrightFeature;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.post.PostFx;
import org.ryzen.utils.render.post.PostPipelines;
import org.ryzen.utils.render.post.PostTarget;

@Environment(EnvType.CLIENT)
public final class DynamicLightRenderer {
   private static final int MAX_LIGHTS = 16;
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putMat4f().putVec4().get();
   private static final int LIGHTS_SIZE = lightsSize();
   private final GpuBuffer uniforms = PostFx.createUniforms("Ryzen Point Light UBO", UNIFORM_SIZE);
   private final GpuBuffer lights = PostFx.createUniforms("Ryzen Point Light Array UBO", LIGHTS_SIZE);
   private final PostTarget sceneCopy = new PostTarget("blade-dynamic-light-scene", PostPipelines.EFFECT_FORMAT, false);

   public void render(FullBrightFeature feature, CameraRenderState cameraState, float partialTick) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      Framebuffer mainTarget = minecraft.getFramebuffer();
      if (valid(mainTarget) && cameraState != null && cameraState.initialized) {
         Vec3d cameraPosition = new Vec3d(cameraState.pos.getX(), cameraState.pos.getY(), cameraState.pos.getZ());
         List<DynamicLightManager.RenderLight> renderLights = DynamicLightManager.INSTANCE.shaderLights(feature, partialTick, cameraPosition);
         if (!renderLights.isEmpty()) {
            SimpleFramebuffer scene = this.sceneCopy.ensure(mainTarget.textureWidth, mainTarget.textureHeight);
            if (scene != null && scene.getColorAttachment() != null) {
               RenderSystem.getDevice()
                  .createCommandEncoder()
                  .copyTextureToTexture(
                     mainTarget.getColorAttachment(), scene.getColorAttachment(), 0, 0, 0, 0, 0, mainTarget.textureWidth, mainTarget.textureHeight
                  );
               Matrix4f levelProjection = Render3DUtil.levelProjectionCopy();
               if (levelProjection != null) {
                  Matrix4f inverseViewProjection = levelProjection.mul(new Matrix4f().rotation(new Quaternionf(cameraState.orientation).conjugate())).invert();
                  this.writeUniforms(inverseViewProjection, feature.lightIntensity.getValue().floatValue(), renderLights.size(), false);
                  this.writeLights(renderLights, cameraPosition);
                  PostFx.pass("Ryzen Dynamic Point Lights", PostPipelines.POINT_LIGHTS, mainTarget, pass -> {
                     pass.setUniform("PointLightUniforms", this.uniforms);
                     pass.setUniform("PointLights", this.lights);
                     pass.bindTexture("SceneSampler", scene.getColorAttachmentView(), PostFx.linearSampler());
                     pass.bindTexture("DepthSampler", mainTarget.getDepthAttachmentView(), PostFx.nearestSampler());
                  });
               }
            }
         }
      }
   }

   private void writeUniforms(Matrix4fc inverseViewProjection, float intensity, int lightCount, boolean depthZeroToOne) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putMat4f(inverseViewProjection)
            .putVec4(PostFx.shaderTime(), intensity, (float)lightCount, depthZeroToOne ? 1.0F : 0.0F)
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
      } catch (Throwable var9) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void writeLights(List<DynamicLightManager.RenderLight> renderLights, Vec3d cameraPosition) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         Std140Builder builder = Std140Builder.onStack(stack, LIGHTS_SIZE);

         for (int index = 0; index < 16; index++) {
            if (index < renderLights.size()) {
               DynamicLightManager.RenderLight light = renderLights.get(index);
               Vec3d relative = light.position().subtract(cameraPosition);
               builder.putVec4((float)relative.x, (float)relative.y, (float)relative.z, light.radius());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for (int indexx = 0; indexx < 16; indexx++) {
            if (indexx < renderLights.size()) {
               DynamicLightManager.RenderLight light = renderLights.get(indexx);
               builder.putVec4(
                  (float)(light.rgb() >> 16 & 0xFF) / 255.0F, (float)(light.rgb() >> 8 & 0xFF) / 255.0F, (float)(light.rgb() & 0xFF) / 255.0F, light.flicker()
               );
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for (int indexxx = 0; indexxx < 16; indexxx++) {
            builder.putVec4(indexxx < renderLights.size() ? renderLights.get(indexxx).phase() : 0.0F, 0.0F, 0.0F, 0.0F);
         }

         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.lights.slice(), builder.get());
      } catch (Throwable var9) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private static boolean valid(Framebuffer target) {
      return target != null
         && target.textureWidth > 0
         && target.textureHeight > 0
         && target.getColorAttachment() != null
         && target.getColorAttachmentView() != null
         && target.getDepthAttachment() != null
         && target.getDepthAttachmentView() != null;
   }

   private static int lightsSize() {
      Std140SizeCalculator calculator = new Std140SizeCalculator();

      for (int index = 0; index < 48; index++) {
         calculator.putVec4();
      }

      return calculator.get();
   }

   public void release() {
      this.sceneCopy.release();
      this.uniforms.close();
      this.lights.close();
   }
}
