package org.ryzen.utils.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;

@Environment(EnvType.CLIENT)
public final class EntityEspStateCache {
   private static volatile List<EntityRenderState> frameStates = Collections.emptyList();

   private EntityEspStateCache() {
   }

   public static void capture(List<EntityRenderState> states) {
      frameStates = (List<EntityRenderState>)(states != null && !states.isEmpty() ? new ArrayList<>(states) : Collections.emptyList());
   }

   public static List<EntityRenderState> currentStates() {
      return frameStates;
   }

   public static void clear() {
      frameStates = Collections.emptyList();
   }
}
