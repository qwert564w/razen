package org.ryzen.feature.impl.misc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.GizmoDrawing.CollectorScope;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.world.ZoneGizmos;

@Environment(EnvType.CLIENT)
public final class MineAssistantFeature extends Feature implements MinecraftContext {
   private static final String DIAMOND = "Diamond";
   private static final String REDSTONE = "Redstone";
   private static final String IRON = "Iron";
   private static final String LAPIS = "Lapis";
   private static final String GOLD = "Gold";
   private static final String ANCIENT = "Ancient Debris";
   private static final String COAL = "Coal";
   private static final int SCAN_INTERVAL = 10;
   private static final int MAX_MARKERS = 1200;
   private static final Map<Block, MineAssistantFeature.Ore> ORES = createOres();
   public final MultiSelectSetting ores = this.register(
      new MultiSelectSetting("Ores", List.of("Diamond", "Gold", "Ancient Debris"), "Diamond", "Redstone", "Iron", "Lapis", "Gold", "Ancient Debris", "Coal")
   );
   private final List<MineAssistantFeature.Marker> markers = new ArrayList<>();
   private BlockPos mineCenter;
   private int ticks;

   public MineAssistantFeature() {
      super("Mine Assistant", "Highlights selected resources in FunTime automatic mines", FeatureCategory.MISC, -1);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.reset();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && event.getClient().world != null && ++this.ticks >= 10) {
         this.ticks = 0;
         this.mineCenter = this.findMineCenter(player);
         this.markers.clear();
         if (this.mineCenter != null) {
            int centerX = this.mineCenter.getX();
            int centerY = this.mineCenter.getY() - 2;
            int centerZ = this.mineCenter.getZ();

            for (int y = centerY - 10; y <= centerY + 1 && this.markers.size() < 1200; y++) {
               for (int x = centerX - 18; x <= centerX + 18 && this.markers.size() < 1200; x++) {
                  for (int z = centerZ - 18; z <= centerZ + 18 && this.markers.size() < 1200; z++) {
                     BlockPos pos = new BlockPos(x, y, z);
                     if (event.getClient().world.isChunkLoaded(pos)) {
                        MineAssistantFeature.Ore ore = ORES.get(event.getClient().world.getBlockState(pos).getBlock());
                        if (ore != null && this.ores.isSelected(ore.option())) {
                           this.markers.add(new MineAssistantFeature.Marker(pos, ore.color()));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (!this.markers.isEmpty() && event.getClient().worldRenderer != null) {
         CollectorScope ignored = event.getClient().worldRenderer.startDrawingGizmos();

         try {
            for (MineAssistantFeature.Marker marker : List.copyOf(this.markers)) {
               int stroke = ColorUtil.withAlpha(marker.color(), 225);
               GizmoDrawing.collect(ZoneGizmos.cube(new Box(marker.pos()), stroke, ColorUtil.withAlpha(marker.color(), 42), 1.2F)).ignoreOcclusion();
            }
         } catch (Throwable var7) {
            if (ignored != null) {
               try {
                  ignored.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (ignored != null) {
            ignored.close();
         }
      }
   }

   private BlockPos findMineCenter(ClientPlayerEntity player) {
      Box search = player.getBoundingBox().expand(256.0);

      for (ArmorStandEntity stand : mc.world.getEntitiesByClass(ArmorStandEntity.class, search, entity -> entity.isAlive() && entity.hasCustomName())) {
         String name = stand.getName().getString().toLowerCase(Locale.ROOT);
         if (name.contains("авто-шахта") || name.contains("auto-mine") || name.contains("auto mine")) {
            return stand.getBlockPos();
         }
      }

      return null;
   }

   private void reset() {
      this.markers.clear();
      this.mineCenter = null;
      this.ticks = 0;
   }

   private static Map<Block, MineAssistantFeature.Ore> createOres() {
      Map<Block, MineAssistantFeature.Ore> result = new LinkedHashMap<>();
      put(result, "Diamond", 2287359, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
      put(result, "Redstone", 16726832, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
      put(result, "Iron", 14211288, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
      put(result, "Lapis", 2647295, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
      put(result, "Gold", 16766011, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
      put(result, "Ancient Debris", 11023136, Blocks.ANCIENT_DEBRIS);
      put(result, "Coal", 5592405, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
      return Map.copyOf(result);
   }

   private static void put(Map<Block, MineAssistantFeature.Ore> target, String option, int color, Block... blocks) {
      for (Block block : blocks) {
         target.put(block, new MineAssistantFeature.Ore(option, color));
      }
   }

   @Environment(EnvType.CLIENT)
   private static record Marker(BlockPos pos, int color) {
   }

   @Environment(EnvType.CLIENT)
   private static record Ore(String option, int color) {
   }
}
