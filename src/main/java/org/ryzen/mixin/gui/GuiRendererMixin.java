package org.ryzen.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SamplerCache;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import org.ryzen.utils.render.gui.GuiBackdrop;
import org.ryzen.utils.render.gui.GuiPipelines;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({GuiRenderer.class})
public abstract class GuiRendererMixin {
   @Shadow
   @Final
   private List<?> draws;
   @Unique
   private final IntList blade$glassSplits = new IntArrayList();
   @Unique
   private boolean blade$previousElementWasGlass;

   @Inject(
      method = {"prepare"},
      at = {@At("HEAD")}
   )
   private void blade$resetGlassSplits(CallbackInfo ci) {
      this.blade$glassSplits.clear();
      this.blade$previousElementWasGlass = false;
   }

   @Inject(
      method = {"prepareSimpleElement"},
      at = {@At("HEAD")}
   )
   private void blade$markGlassRuns(SimpleGuiElementRenderState elementState, CallbackInfo ci) {
      boolean glass = blade$isGlassPipeline(elementState.pipeline());
      if (glass && !this.blade$previousElementWasGlass) {
         this.blade$glassSplits.add(this.draws.size());
      }

      this.blade$previousElementWasGlass = glass;
   }

   @WrapOperation(
      method = {"renderPreparedDraws"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Ljava/util/function/Supplier;Lnet/minecraft/client/gl/Framebuffer;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;II)V"
      )}
   )
   private void blade$captureBackdropBeforeGlassRuns(
      GuiRenderer instance,
      Supplier<String> label,
      Framebuffer mainRenderTarget,
      GpuBufferSlice projectionMatrix,
      GpuBufferSlice fogBuffer,
      GpuBuffer vertexBuffer,
      IndexType indexType,
      int startIndex,
      int endIndex,
      Operation<Void> original
   ) {
      int cursor = startIndex;

      for (int i = 0; i < this.blade$glassSplits.size(); i++) {
         int split = this.blade$glassSplits.getInt(i);
         if (split >= cursor && split < endIndex) {
            if (split > cursor) {
               original.call(new Object[]{instance, label, mainRenderTarget, projectionMatrix, fogBuffer, vertexBuffer, indexType, cursor, split});
            }

            GuiBackdrop.captureNow();
            cursor = split;
         }
      }

      if (cursor < endIndex) {
         original.call(new Object[]{instance, label, mainRenderTarget, projectionMatrix, fogBuffer, vertexBuffer, indexType, cursor, endIndex});
      }
   }

   @Unique
   private static boolean blade$isGlassPipeline(RenderPipeline pipeline) {
      return pipeline == GuiPipelines.BLUR_RECT || pipeline == GuiPipelines.GLASS_SHADOW;
   }

   @WrapOperation(
      method = {"prepareItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gl/SamplerCache;getRepeated(Lcom/mojang/blaze3d/textures/FilterMode;)Lnet/minecraft/client/gl/GpuSampler;"
      )}
   )
   private GpuSampler blade$smoothDownscaledItems(
      SamplerCache cache, FilterMode filterMode, Operation<GpuSampler> original, @Local(argsOnly = true) ItemGuiElementRenderState itemState
   ) {
      return blade$isDownscaled(itemState)
         ? (GpuSampler)original.call(new Object[]{cache, FilterMode.LINEAR})
         : (GpuSampler)original.call(new Object[]{cache, filterMode});
   }

   @WrapOperation(
      method = {"prepareItem"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/gl/RenderPipelines;GUI_TEXTURED_PREMULTIPLIED_ALPHA:Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
      )}
   )
   private RenderPipeline blade$bicubicDownscalePipeline(Operation<RenderPipeline> original, @Local(argsOnly = true) ItemGuiElementRenderState itemState) {
      return blade$isDownscaled(itemState)
         ? GuiPipelines.itemDownscale(MinecraftClient.getInstance().getWindow().getScaleFactor())
         : (RenderPipeline)original.call(new Object[0]);
   }

   @Unique
   private static boolean blade$isDownscaled(ItemGuiElementRenderState itemState) {
      return itemState.pose().m00 < 0.999F;
   }
}
