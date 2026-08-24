package org.ryzen.utils.render.chams;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import org.ryzen.feature.impl.visual.ChamsFeature;

@Environment(EnvType.CLIENT)
public final class ChamsTargetMatcher {
   private ChamsTargetMatcher() {
   }

   public static List<Entity> collectTargets(MinecraftClient minecraft, ChamsFeature feature) {
      if (minecraft.world != null && feature != null && feature.hasAnyVisual()) {
         List<Entity> targets = new ArrayList<>();

         for (Entity entity : minecraft.world.getEntities()) {
            if (feature.shouldRender(entity)) {
               targets.add(entity);
            }
         }

         return targets;
      } else {
         return List.of();
      }
   }

   public static Entity matchingTarget(EntityRenderState state, List<Entity> targets) {
      if (state instanceof PlayerEntityRenderState avatarState) {
         for (Entity entity : targets) {
            if (entity.getId() == avatarState.id) {
               return entity;
            }
         }
      }

      Entity best = null;
      double bestDistanceSq = Double.MAX_VALUE;

      for (Entity entityx : targets) {
         if (entityx.getType() == state.entityType) {
            double dx = entityx.getX() - state.x;
            double dy = entityx.getY() - state.y;
            double dz = entityx.getZ() - state.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq < bestDistanceSq) {
               best = entityx;
               bestDistanceSq = distanceSq;
            }
         }
      }

      return bestDistanceSq <= 9.0 ? best : null;
   }
}
