package org.ryzen.utils.render.chams;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.state.WorldRenderState;
import org.ryzen.feature.impl.visual.ChamsFeature;

@Environment(EnvType.CLIENT)
public final class ChamsPipeline {
   private final ChamsMaskRenderer maskRenderer = new ChamsMaskRenderer();
   private final ChamsCompositeEffect composite = new ChamsCompositeEffect();

   public void render(WorldRenderState levelRenderState, ChamsFeature feature) {
      this.maskRenderer.renderGroups(levelRenderState, feature, frame -> this.composite.render(frame, feature));
   }

   public void release() {
      this.maskRenderer.release();
      this.composite.release();
   }
}
