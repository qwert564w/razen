package org.ryzen.feature.impl.visual;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Theme;
import org.ryzen.utils.render.world.WorldMeshRenderer;

@Environment(EnvType.CLIENT)
public final class AncientXrayFeature extends Feature implements MinecraftContext {
   private static final int SCAN_BUDGET_PER_TICK = 65536;
   public final NumberSetting radius = this.register(new NumberSetting("Radius", 12.0, 4.0, 400.0, 1.0, " blocks"));
   public final BooleanSetting throughWalls = this.register(new BooleanSetting("Through Walls", true));
   private Block targetBlock;
   private final List<BlockPos> found = new ArrayList<>();
   private BlockPos scanCenter;
   private int scanRadius;
   private int scanCursor;
   private int scanRadiusSq;
   private Mutable cursor = new Mutable();
   private boolean scanComplete;
   private BlockPos lastCompletedCenter;
   private int lastCompletedRadius;

   public AncientXrayFeature() {
      super("AncientXray", "Highlights ancient debris through walls", FeatureCategory.VISUAL, -1);
   }

   public static AncientXrayFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(AncientXrayFeature.class);
   }

   @Override
   protected void onEnable() {
      this.targetBlock = (Block)Registries.BLOCK.get(Identifier.of("minecraft:ancient_debris"));
      if (this.targetBlock == Blocks.AIR) {
         this.targetBlock = null;
      }

      this.resetScan();
   }

   @Override
   protected void onDisable() {
      this.found.clear();
      this.resetScan();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.found.clear();
      this.resetScan();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      ClientWorld level = event.getClient().world;
      if (player != null && level != null && this.targetBlock != null) {
         BlockPos center = player.getBlockPos();
         int r = (int)Math.round(this.radius.getValue());
         if (this.needsRescan(center, r)) {
            this.resetScan();
            this.scanCenter = center;
            this.scanRadius = r;
            this.scanRadiusSq = r * r;
            this.scanCursor = 0;
            this.found.clear();
            this.scanComplete = false;
         }

         if (this.scanCenter != null && !this.scanComplete) {
            this.scanSlice(level);
         }
      }
   }

   private boolean needsRescan(BlockPos center, int r) {
      if (this.scanCenter != null && r == this.scanRadius) {
         double rescanDist = Math.max(3.0, (double)r * 0.25);
         double dx = (double)(center.getX() - this.scanCenter.getX());
         double dz = (double)(center.getZ() - this.scanCenter.getZ());
         return dx * dx + dz * dz > rescanDist * rescanDist;
      } else {
         return true;
      }
   }

   private void scanSlice(ClientWorld level) {
      int budget = 65536;
      int originX = this.scanCenter.getX();
      int originY = this.scanCenter.getY();
      int originZ = this.scanCenter.getZ();
      int r = this.scanRadius;
      int rSq = this.scanRadiusSq;
      int side = r * 2 + 1;
      int plane = side * side;
      int total = plane * side;

      while (this.scanCursor < total && budget > 0) {
         int index = this.scanCursor++;
         int dy = index / plane - r;
         int remainder = index % plane;
         int dz = remainder / side - r;
         int dx = remainder % side - r;
         if (dx * dx + dy * dy + dz * dz <= rSq) {
            budget--;
            this.cursor.set(originX + dx, originY + dy, originZ + dz);
            if (level.isChunkLoaded(this.cursor) && level.getBlockState(this.cursor).isOf(this.targetBlock)) {
               this.found.add(new BlockPos(this.cursor));
            }
         }
      }

      if (this.scanCursor >= total) {
         this.scanComplete = true;
         this.lastCompletedCenter = this.scanCenter;
         this.lastCompletedRadius = this.scanRadius;
      }
   }

   private void resetScan() {
      this.scanCenter = null;
      this.scanRadius = 0;
      this.scanRadiusSq = 0;
      this.scanCursor = 0;
      this.scanComplete = false;
   }

   public void renderWorld() {
      if (this.isEnabled() && this.targetBlock != null && mc.world != null && mc.player != null) {
         if (this.lastCompletedCenter != null) {
            int accent = Theme.getAccent();
            int edgeColor = ColorUtil.withAlpha(accent, 0.95F);
            int whiteCore = ColorUtil.rgba(255, 255, 255, 70);
            List<WorldMeshRenderer.Line> lines = new ArrayList<>(this.found.size() * 24);

            for (BlockPos pos : this.found) {
               addBoxEdges(lines, pos, edgeColor);
               addBoxEdges(lines, pos, whiteCore, 0.3);
            }

            WorldMeshRenderer.render(new WorldMeshRenderer.WorldMesh(lines, List.of(), List.of()), this.throughWalls.getValue());
         }
      }
   }

   private static void addBoxEdges(List<WorldMeshRenderer.Line> lines, BlockPos pos, int color) {
      addBoxEdges(lines, pos, color, 0.0);
   }

   private static void addBoxEdges(List<WorldMeshRenderer.Line> lines, BlockPos pos, int color, double inset) {
      double minX = (double)pos.getX() + inset;
      double minY = (double)pos.getY() + inset;
      double minZ = (double)pos.getZ() + inset;
      double maxX = (double)pos.getX() + 1.0 - inset;
      double maxY = (double)pos.getY() + 1.0 - inset;
      double maxZ = (double)pos.getZ() + 1.0 - inset;
      Vec3d a = new Vec3d(minX, minY, minZ);
      Vec3d b = new Vec3d(maxX, minY, minZ);
      Vec3d c = new Vec3d(maxX, minY, maxZ);
      Vec3d d = new Vec3d(minX, minY, maxZ);
      Vec3d e = new Vec3d(minX, maxY, minZ);
      Vec3d f = new Vec3d(maxX, maxY, minZ);
      Vec3d g = new Vec3d(maxX, maxY, maxZ);
      Vec3d h = new Vec3d(minX, maxY, maxZ);
      lines.add(new WorldMeshRenderer.Line(a, b, color));
      lines.add(new WorldMeshRenderer.Line(b, c, color));
      lines.add(new WorldMeshRenderer.Line(c, d, color));
      lines.add(new WorldMeshRenderer.Line(d, a, color));
      lines.add(new WorldMeshRenderer.Line(e, f, color));
      lines.add(new WorldMeshRenderer.Line(f, g, color));
      lines.add(new WorldMeshRenderer.Line(g, h, color));
      lines.add(new WorldMeshRenderer.Line(h, e, color));
      lines.add(new WorldMeshRenderer.Line(a, e, color));
      lines.add(new WorldMeshRenderer.Line(b, f, color));
      lines.add(new WorldMeshRenderer.Line(c, g, color));
      lines.add(new WorldMeshRenderer.Line(d, h, color));
   }
}
