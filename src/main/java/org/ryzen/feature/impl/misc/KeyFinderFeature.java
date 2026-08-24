package org.ryzen.feature.impl.misc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.render.Render2DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.gui.MsdfFont;
import org.ryzen.utils.render.gui.Render2DUtil;
import org.ryzen.utils.render.gui.UiFontStyle;
import org.ryzen.utils.render.gui.UiFonts;

@Environment(EnvType.CLIENT)
public final class KeyFinderFeature extends Feature {
   private static final int SCAN_INTERVAL_TICKS = 20;
   private static final double CHEST_SEARCH_RADIUS = 8.0;
   private static final float TAG_HEIGHT = 12.0F;
   private static final float TEXT_SIZE = 7.5F;
   private static final int LOOTED_COLOR = 11184810;
   public final NumberSetting range = this.register(new NumberSetting("Range", 64.0, 16.0, 256.0, 8.0, " blocks"));
   public final BooleanSetting spawners = this.register(new BooleanSetting("Spawners", true));
   public final BooleanSetting minecarts = this.register(new BooleanSetting("Minecarts", true));
   public final BooleanSetting showDistance = this.register(new BooleanSetting("Show Distance", true));
   public final ColorSetting color = this.register(new ColorSetting("Color", 5636095));
   private final List<KeyFinderFeature.Marker> markers = new ArrayList<>();
   private int ticksUntilScan;

   public KeyFinderFeature() {
      super("KeyFinder", "Marks spawners and loot minecarts through walls", FeatureCategory.MISC, -1);
   }

   @Override
   protected void onDisable() {
      this.markers.clear();
      this.ticksUntilScan = 0;
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.markers.clear();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      ClientWorld level = client.world;
      if (player != null && level != null) {
         if (--this.ticksUntilScan <= 0) {
            this.ticksUntilScan = 20;
            this.rescan(player, level);
         }
      } else {
         this.markers.clear();
      }
   }

   private void rescan(ClientPlayerEntity player, ClientWorld level) {
      this.markers.clear();
      double rangeSqr = this.range.getValue() * this.range.getValue();
      if (this.spawners.getValue()) {
         Set<BlockPos> chests = new HashSet<>();
         List<BlockPos> spawnerPositions = new ArrayList<>();
         int viewDistance = MinecraftClient.getInstance().options.getClampedViewDistance();
         int chunkX = player.getChunkPos().x;
         int chunkZ = player.getChunkPos().z;

         for (int x = chunkX - viewDistance; x <= chunkX + viewDistance; x++) {
            for (int z = chunkZ - viewDistance; z <= chunkZ + viewDistance; z++) {
               WorldChunk chunk = level.getChunkManager().getWorldChunk(x, z, false);
               if (chunk != null) {
                  for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                     BlockPos pos = blockEntity.getPos();
                     if (!(player.getBlockPos().getSquaredDistance(pos) > rangeSqr)) {
                        if (blockEntity instanceof MobSpawnerBlockEntity) {
                           spawnerPositions.add(pos);
                        } else if (blockEntity instanceof ChestBlockEntity) {
                           chests.add(pos);
                        }
                     }
                  }
               }
            }
         }

         for (BlockPos spawner : spawnerPositions) {
            int chestCount = 0;

            for (BlockPos chest : chests) {
               if (chest.getSquaredDistance(spawner) <= 64.0) {
                  chestCount++;
               }
            }

            this.markers
               .add(new KeyFinderFeature.Marker(Vec3d.ofCenter(spawner), chestCount > 0 ? "Спавнер (" + chestCount + ")" : "Спавнер (залутан)", chestCount > 0));
         }
      }

      if (this.minecarts.getValue()) {
         for (Entity entity : level.getEntities()) {
            if (entity instanceof AbstractMinecartEntity) {
               AbstractMinecartEntity minecart = (AbstractMinecartEntity)entity;
               if (player.squaredDistanceTo(minecart) <= rangeSqr) {
                  this.markers.add(new KeyFinderFeature.Marker(minecart.getEntityPos(), "Вагонетка", true));
               }
            }
         }
      }
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      MinecraftClient mc = event.getClient();
      if (mc.player != null && !this.markers.isEmpty()) {
         MsdfFont font = UiFonts.sfProDisplay();
         float unit = 1.0F / (float)mc.getWindow().getScaleFactor();
         float textSize = 7.5F * unit;
         float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();
         float height = 12.0F * unit;

         for (KeyFinderFeature.Marker marker : List.copyOf(this.markers)) {
            Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, marker.position());
            if (anchor != null) {
               String label = marker.label();
               if (this.showDistance.getValue()) {
                  label = label + String.format(Locale.ROOT, "  %.0fм", mc.player.getEntityPos().distanceTo(marker.position()));
               }

               float width = font.measureWidth(label, textSize, letterSpacing) + 8.0F * unit;
               float x = anchor.x() - width / 2.0F;
               float y = anchor.y() - height / 2.0F;
               int tint = marker.active() ? this.color.getValue() : 11184810;
               Render2DUtil.rect(x, y, width, height).color(ColorUtil.withAlpha(0, 150)).radius(height / 2.0F).draw();
               Render2DUtil.text(x + 4.0F * unit, y + (height - textSize) / 2.0F, textSize, label).font(font).color(ColorUtil.withAlpha(tint, 255)).draw();
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static record Marker(Vec3d position, String label, boolean active) {
   }
}
