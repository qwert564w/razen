package org.ryzen.utils.render.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class GuiPipelines {
   public static final RenderPipeline RECT = builder("rect", "rect", false).build();
   public static final RenderPipeline TEXT = builder("text", "text", true).build();
   public static final RenderPipeline TEXTURE = builder("texture", "texture", true).build();
   public static final RenderPipeline GLASS_SHADOW = builder("glass_shadow", "rect", false).build();
   public static final RenderPipeline BLUR_RECT = builder("blur_rect", "blur_rect", true).build();
   public static final RenderPipeline COLOR_GRID = builder("color_grid", "color_grid", true).build();
   public static final RenderPipeline MENU_BACKGROUND = builder("menu_background", "menu_background", false).build();
   public static final RenderPipeline GUI_BLUR_DOWN = blurBuilder("gui_blur_down", "gui_kawase_down").build();
   public static final RenderPipeline GUI_BLUR_UP = blurBuilder("gui_blur_up", "gui_kawase_up").build();
   private static final RenderPipeline[] ITEM_DOWNSCALE = new RenderPipeline[]{
      itemDownscalePipeline(1),
      itemDownscalePipeline(2),
      itemDownscalePipeline(3),
      itemDownscalePipeline(4),
      itemDownscalePipeline(5),
      itemDownscalePipeline(6),
      itemDownscalePipeline(7),
      itemDownscalePipeline(8)
   };

   public static RenderPipeline itemDownscale(int guiScale) {
      return ITEM_DOWNSCALE[Math.clamp((long)guiScale, 1, ITEM_DOWNSCALE.length) - 1];
   }

   public static RenderPipeline[] itemDownscaleVariants() {
      return (RenderPipeline[])ITEM_DOWNSCALE.clone();
   }

   private static RenderPipeline itemDownscalePipeline(int texelSize) {
      return RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
         .withLocation(Identifier.of("ryzen:gui/item_downscale_" + texelSize))
         .withVertexShader(Identifier.of("ryzen:core/item_downscale"))
         .withFragmentShader(Identifier.of("ryzen:core/item_downscale"))
         .withShaderDefine("TEXEL_SIZE", texelSize)
         .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
         .build();
   }

   private static Builder blurBuilder(String name, String fragmentShader) {
      return RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.of("ryzen:gui/" + name))
         .withVertexShader(Identifier.of("ryzen:post/blurs/kawase_common"))
         .withFragmentShader(Identifier.of("ryzen:post/blurs/" + fragmentShader))
         .withSampler("CurrentInput")
         .withUniform("GuiKawaseUniforms", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withVertexFormat(VertexFormats.POSITION_TEXTURE, DrawMode.TRIANGLES)
         .withCull(false);
   }

   private static Builder builder(String name, String shader, boolean sampled) {
      Builder builder = RenderPipeline.builder(new Snippet[]{RenderPipelines.GUI_SNIPPET})
         .withLocation(Identifier.of("ryzen:gui/" + name))
         .withVertexShader(Identifier.of("ryzen:core/" + shader))
         .withFragmentShader(Identifier.of("ryzen:core/" + shader))
         .withBlend(BlendFunction.TRANSLUCENT)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withVertexFormat(UiVertexFormats.UI, DrawMode.QUADS)
         .withCull(false);
      if (sampled) {
         builder.withSampler("Sampler0");
      }

      return builder;
   }

   private GuiPipelines() {
   }
}
