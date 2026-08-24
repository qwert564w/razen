package org.ryzen.utils.render.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.light.ChunkLightingView;
import org.ryzen.feature.impl.player.FullBrightFeature;
import org.ryzen.utils.render.Render3DUtil;

@Environment(EnvType.CLIENT)
public final class DynamicLightManager {
   public static final DynamicLightManager INSTANCE = new DynamicLightManager();
   private static final int MAX_CANDIDATES = 64;
   private static final int MAX_SHADER_LIGHTS = 16;
   private static final double MAX_DISTANCE_SQR = 4096.0;
   private volatile Map<Long, Integer> virtualLuminance = Map.of();
   private List<DynamicLightManager.Candidate> candidates = List.of();
   private ClientWorld engineLevel;

   private DynamicLightManager() {
   }

   public static int virtualLuminance(long blockPos) {
      return INSTANCE.virtualLuminance.getOrDefault(blockPos, 0);
   }

   public void tick(MinecraftClient minecraft, FullBrightFeature feature) {
      ClientPlayerEntity player = minecraft.player;
      ClientWorld level = minecraft.world;
      if (player != null && level != null) {
         List<DynamicLightManager.Candidate> collected = new ArrayList<>();
         DynamicLightManager.LightSpec localLight = strongest(lightFor(player.getMainHandStack()), lightFor(player.getOffHandStack()));
         if (localLight != null) {
            collected.add(DynamicLightManager.Candidate.entity(player, (double)player.getStandingEyeHeight() * 0.72, localLight));
         }

         for (Entity entity : level.getEntities()) {
            if (entity != player && !entity.isRemoved() && !(entity.squaredDistanceTo(player) > 4096.0)) {
               DynamicLightManager.LightSpec source = null;
               double yOffset = (double)entity.getHeight() * 0.5;
               if (feature.lightItems.getValue() && entity instanceof ItemEntity itemEntity) {
                  source = lightFor(itemEntity.getStack());
                  yOffset = 0.2;
               }

               if (feature.lightOthers.getValue() && entity instanceof LivingEntity living) {
                  source = strongest(source, strongest(lightFor(living.getMainHandStack()), lightFor(living.getOffHandStack())));
               }

               if (feature.lightOthers.getValue() && entity.doesRenderOnFire()) {
                  source = strongest(source, DynamicLightManager.LightSpec.FIRE);
               }

               if (source != null) {
                  collected.add(DynamicLightManager.Candidate.entity(entity, yOffset, source));
               }
            }
         }

         collected.sort(Comparator.comparingDouble(candidate -> candidate.position(1.0F).squaredDistanceTo(player.getEntityPos())));
         if (collected.size() > 64) {
            collected = new ArrayList<>(collected.subList(0, 64));
         }

         this.candidates = List.copyOf(collected);
         this.updateEngineLights(level, feature);
      } else {
         this.clear();
      }
   }

   public List<DynamicLightManager.RenderLight> shaderLights(FullBrightFeature feature, float partialTick, Vec3d cameraPosition) {
      if (feature.usesShaderLights() && !this.candidates.isEmpty()) {
         float radiusMultiplier = feature.lightRadius.getValue().floatValue();
         return this.candidates
            .stream()
            .filter(candidate -> !candidate.entity.isRemoved())
            .map(candidate -> candidate.toRenderLight(partialTick, radiusMultiplier))
            .sorted(Comparator.comparingDouble(light -> light.position.squaredDistanceTo(cameraPosition)))
            .limit(16L)
            .toList();
      } else {
         return List.of();
      }
   }

   public void clear() {
      this.candidates = List.of();
      this.clearEngineLights();
   }

   public void revalidateAfterVanillaUpdates(ClientWorld level) {
      Map<Long, Integer> sources = this.virtualLuminance;
      if (level != null && level == this.engineLevel && !sources.isEmpty()) {
         ChunkLightingView blockLight = level.getChunkManager().getLightingProvider().get(LightType.BLOCK);

         for (long packedPos : sources.keySet()) {
            blockLight.checkBlock(BlockPos.fromLong(packedPos));
         }

         blockLight.doLightUpdates();
      }
   }

   private void updateEngineLights(ClientWorld level, FullBrightFeature feature) {
      if (this.engineLevel != null && this.engineLevel != level) {
         this.clearEngineLights();
      }

      this.engineLevel = level;
      Map<Long, Integer> next = new HashMap<>();
      if (feature.usesEngineLights()) {
         float radiusMultiplier = feature.lightRadius.getValue().floatValue();
         float intensity = feature.lightIntensity.getValue().floatValue();

         for (DynamicLightManager.Candidate candidate : this.candidates) {
            Vec3d position = candidate.position(1.0F);
            BlockPos blockPos = BlockPos.ofFloored(position);
            if (level.isChunkLoaded(blockPos)) {
               int luminance = Math.clamp((long)Math.round((float)candidate.spec.luminance * radiusMultiplier * Math.min(1.5F, intensity)), 1, 15);
               next.merge(blockPos.asLong(), luminance, Math::max);
            }
         }
      }

      Map<Long, Integer> previous = this.virtualLuminance;
      this.virtualLuminance = Map.copyOf(next);
      if (!previous.equals(next)) {
         Set<Long> changed = new HashSet<>(previous.keySet());
         changed.addAll(next.keySet());
         ChunkLightingView lightEngine = level.getChunkManager().getLightingProvider().get(LightType.BLOCK);

         for (long packedPos : changed) {
            if (previous.getOrDefault(packedPos, 0) != next.getOrDefault(packedPos, 0)) {
               lightEngine.checkBlock(BlockPos.fromLong(packedPos));
            }
         }
      }
   }

   private void clearEngineLights() {
      Map<Long, Integer> previous = this.virtualLuminance;
      this.virtualLuminance = Map.of();
      ClientWorld level = this.engineLevel;
      this.engineLevel = null;
      if (level != null && !previous.isEmpty()) {
         ChunkLightingView lightEngine = level.getChunkManager().getLightingProvider().get(LightType.BLOCK);

         for (long packedPos : previous.keySet()) {
            lightEngine.checkBlock(BlockPos.fromLong(packedPos));
         }
      }
   }

   private static DynamicLightManager.LightSpec lightFor(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return null;
      } else if (stack.isOf(Items.SOUL_TORCH) || stack.isOf(Items.SOUL_LANTERN) || stack.isOf(Items.SOUL_CAMPFIRE)) {
         return new DynamicLightManager.LightSpec(7002623, 12, 0.08F);
      } else if (stack.isOf(Items.REDSTONE_TORCH)) {
         return new DynamicLightManager.LightSpec(16727332, 9, 0.04F);
      } else if (stack.isOf(Items.SEA_LANTERN) || stack.isOf(Items.END_ROD)) {
         return new DynamicLightManager.LightSpec(14219519, 14, 0.02F);
      } else if (stack.isOf(Items.OCHRE_FROGLIGHT)) {
         return new DynamicLightManager.LightSpec(16765802, 15, 0.01F);
      } else if (stack.isOf(Items.VERDANT_FROGLIGHT)) {
         return new DynamicLightManager.LightSpec(9306049, 15, 0.01F);
      } else if (stack.isOf(Items.PEARLESCENT_FROGLIGHT)) {
         return new DynamicLightManager.LightSpec(14919935, 15, 0.01F);
      } else if (stack.isOf(Items.LAVA_BUCKET)
         || stack.isOf(Items.FIRE_CHARGE)
         || stack.isOf(Items.BLAZE_ROD)
         || stack.isOf(Items.BLAZE_POWDER)
         || stack.isOf(Items.MAGMA_CREAM)) {
         return new DynamicLightManager.LightSpec(16738852, 15, 0.18F);
      } else if (stack.isOf(Items.GLOW_BERRIES) || stack.isOf(Items.GLOW_INK_SAC)) {
         return new DynamicLightManager.LightSpec(9306032, 10, 0.04F);
      } else if (stack.isOf(Items.NETHER_STAR)) {
         return new DynamicLightManager.LightSpec(13101311, 15, 0.08F);
      } else {
         if (stack.getItem() instanceof BlockItem blockItem) {
            int luminance = blockItem.getBlock().getDefaultState().getLuminance();
            if (luminance > 0) {
               return new DynamicLightManager.LightSpec(16765072, luminance, luminance >= 14 ? 0.07F : 0.03F);
            }
         }

         return null;
      }
   }

   private static DynamicLightManager.LightSpec strongest(DynamicLightManager.LightSpec first, DynamicLightManager.LightSpec second) {
      if (first == null) {
         return second;
      } else if (second == null) {
         return first;
      } else {
         return first.luminance >= second.luminance ? first : second;
      }
   }

   @Environment(EnvType.CLIENT)
   private static record Candidate(Entity entity, double yOffset, DynamicLightManager.LightSpec spec) {
      private static DynamicLightManager.Candidate entity(Entity entity, double yOffset, DynamicLightManager.LightSpec spec) {
         return new DynamicLightManager.Candidate(entity, yOffset, spec);
      }

      private Vec3d position(float partialTick) {
         return Render3DUtil.interpolatedPosition(this.entity, partialTick).add(0.0, this.yOffset, 0.0);
      }

      private DynamicLightManager.RenderLight toRenderLight(float partialTick, float radiusMultiplier) {
         return new DynamicLightManager.RenderLight(
            this.position(partialTick),
            this.spec.rgb,
            (3.0F + (float)this.spec.luminance * 0.62F) * radiusMultiplier,
            this.spec.flicker,
            (float)this.entity.getId() * 0.731F
         );
      }
   }

   @Environment(EnvType.CLIENT)
   private static record LightSpec(int rgb, int luminance, float flicker) {
      private static final DynamicLightManager.LightSpec FIRE = new DynamicLightManager.LightSpec(16738852, 15, 0.22F);
   }

   @Environment(EnvType.CLIENT)
   public static record RenderLight(Vec3d position, int rgb, float radius, float flicker, float phase) {
   }
}
