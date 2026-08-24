package org.ryzen.utils.render.chams;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import org.ryzen.utils.render.post.PostPipelines;

@Environment(EnvType.CLIENT)
public final class PopChamsRenderTypes {
   private static final Map<PopChamsRenderTypes.Key, RenderLayer> CACHE = new HashMap<>();

   private PopChamsRenderTypes() {
   }

   public static RenderLayer model(Identifier texture, boolean textured, boolean additive) {
      PopChamsRenderTypes.Kind kind;
      if (additive) {
         kind = textured ? PopChamsRenderTypes.Kind.ADDITIVE : PopChamsRenderTypes.Kind.ADDITIVE_SOLID;
      } else {
         kind = textured ? PopChamsRenderTypes.Kind.TRANSLUCENT : PopChamsRenderTypes.Kind.TRANSLUCENT_SOLID;
      }

      return get(texture, kind);
   }

   public static RenderLayer mask(Identifier texture, boolean textured) {
      return get(texture, textured ? PopChamsRenderTypes.Kind.MASK : PopChamsRenderTypes.Kind.MASK_SOLID);
   }

   private static RenderLayer get(Identifier texture, PopChamsRenderTypes.Kind kind) {
      return CACHE.computeIfAbsent(new PopChamsRenderTypes.Key(texture, kind), key -> {
         RenderSetup setup = RenderSetup.builder(kind.pipeline).texture("Sampler0", texture).build();
         return RenderLayer.of("blade_popchams_" + kind.name().toLowerCase(), setup);
      });
   }

   @Environment(EnvType.CLIENT)
   private static record Key(Identifier texture, PopChamsRenderTypes.Kind kind) {
   }

   @Environment(EnvType.CLIENT)
   private static enum Kind {
      ADDITIVE(PostPipelines.POPCHAMS_ADDITIVE),
      ADDITIVE_SOLID(PostPipelines.POPCHAMS_ADDITIVE_SOLID),
      TRANSLUCENT(PostPipelines.POPCHAMS_TRANSLUCENT),
      TRANSLUCENT_SOLID(PostPipelines.POPCHAMS_TRANSLUCENT_SOLID),
      MASK(PostPipelines.POPCHAMS_MASK),
      MASK_SOLID(PostPipelines.POPCHAMS_MASK_SOLID);

      private final RenderPipeline pipeline;

      private Kind(RenderPipeline pipeline) {
         this.pipeline = pipeline;
      }
   }
}
