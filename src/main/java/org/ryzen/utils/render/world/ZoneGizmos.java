package org.ryzen.utils.render.world;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.Gizmo;
import net.minecraft.world.debug.gizmo.GizmoDrawer;
import org.ryzen.utils.ColorUtil;

@Environment(EnvType.CLIENT)
public final class ZoneGizmos {
   private static final float CROSS_ALPHA = 0.6F;
   private static final float CROSS_WIDTH_SCALE = 0.8F;
   private static final double STRATUM_HEIGHT = 5.0;
   private static final float STRATUM_SLAB_WIDTH = 3.0F;
   private static final float STRATUM_EDGE_WIDTH = 2.0F;
   private static final float STRATUM_CROSS_WIDTH = 1.6F;

   private ZoneGizmos() {
   }

   public static Gizmo cube(Box box, int strokeColor, int fillColor, float strokeWidth) {
      return (primitives, alpha) -> crossBox(primitives, box, strokeColor, fillColor, strokeWidth);
   }

   public static Gizmo ring(Vec3d center, double radius, int strokeColor, int fillColor, float strokeWidth) {
      return (primitives, alpha) -> {
         int cells = (int)Math.ceil(radius) + 1;

         for (int dx = -cells; dx <= cells; dx++) {
            for (int dz = -cells; dz <= cells; dz++) {
               if (onRadiusEdge(dx, dz, radius)) {
                  double x = center.x + (double)dx;
                  double z = center.z + (double)dz;
                  crossBox(primitives, new Box(x - 0.5, center.y, z - 0.5, x + 0.5, center.y + 1.0, z + 0.5), strokeColor, fillColor, strokeWidth);
               }
            }
         }
      };
   }

   public static Gizmo stratum(BlockPos playerPos, Vec3d smooth, float yRot, float xRot, Direction nearestViewDirection, int strokeColor, int fillColor) {
      return (primitives, alpha) -> {
         float yaw = MathHelper.wrapDegrees(yRot);
         if (Math.abs(xRot) > 60.0F) {
            BlockPos anchor = playerPos.up().offset(nearestViewDirection, 3);
            slab(primitives, anchor.east(3).south(3).down(), anchor.west(2).north(2).up(), smooth, strokeColor, fillColor);
         } else if (yaw <= -157.5F || yaw >= 157.5F) {
            BlockPos anchor = playerPos.north(3).up();
            slab(primitives, anchor.down(2).east(3), anchor.up(3).west(2).south(2), smooth, strokeColor, fillColor);
         } else if (yaw <= -112.5F) {
            steppedWall(primitives, playerPos.east(5).south().down(), smooth, strokeColor, fillColor, -1, true);
         } else if (yaw <= -67.5F) {
            BlockPos anchor = playerPos.east(2).up();
            slab(primitives, anchor.down(2).south(3), anchor.up(3).north(2).east(2), smooth, strokeColor, fillColor);
         } else if (yaw <= -22.5F) {
            steppedWall(primitives, playerPos.east(5).down(), smooth, strokeColor, fillColor, 1, false);
         } else if (yaw <= 22.5F) {
            BlockPos anchor = playerPos.south(2).up();
            slab(primitives, anchor.down(2).east(3), anchor.up(3).west(2).south(2), smooth, strokeColor, fillColor);
         } else if (yaw <= 67.5F) {
            steppedWall(primitives, playerPos.west(4).down(), smooth, strokeColor, fillColor, 1, true);
         } else if (yaw <= 112.5F) {
            BlockPos anchor = playerPos.west(3).up();
            slab(primitives, anchor.down(2).south(3), anchor.up(3).north(2).east(2), smooth, strokeColor, fillColor);
         } else if (yaw <= 157.5F) {
            steppedWall(primitives, playerPos.west(4).south().down(), smooth, strokeColor, fillColor, -1, false);
         }
      };
   }

   private static boolean onRadiusEdge(int dx, int dz, double radius) {
      boolean inside = false;
      boolean outside = false;

      for (double offsetX = -0.5; offsetX <= 0.5; offsetX++) {
         for (double offsetZ = -0.5; offsetZ <= 0.5; offsetZ++) {
            double x = (double)dx + offsetX;
            double z = (double)dz + offsetZ;
            if (Math.sqrt(x * x + z * z) <= radius) {
               inside = true;
            } else {
               outside = true;
            }
         }
      }

      return inside && outside;
   }

   private static void slab(GizmoDrawer primitives, BlockPos from, BlockPos to, Vec3d smooth, int strokeColor, int fillColor) {
      Vec3d min = Vec3d.of(from).add(smooth);
      Vec3d max = Vec3d.of(to).add(smooth);
      crossBox(primitives, new Box(min, max), strokeColor, fillColor, 3.0F);
   }

   private static void steppedWall(GizmoDrawer primitives, BlockPos anchor, Vec3d smooth, int strokeColor, int fillColor, int step, boolean mirrored) {
      Vec3d origin = Vec3d.of(anchor).add(smooth);
      int crossColor = ColorUtil.multiplyAlpha(strokeColor, 0.6F);
      double x = mirrored ? (double)step : (double)(-step);
      List<Vec3d> outline = new ArrayList<>();
      outline.add(origin);
      Vec3d current = origin.add(x, 0.0, 0.0);
      outline.add(current);

      for (int i = 0; i < 4; i++) {
         Vec3d var18 = current.add(0.0, 0.0, (double)step);
         outline.add(var18);
         current = var18.add(x, 0.0, 0.0);
         outline.add(current);
      }

      current = current.add(0.0, 0.0, (double)step);
      outline.add(current);
      current = current.add(x * -2.0, 0.0, 0.0);
      outline.add(current);

      for (int i = 0; i < 3; i++) {
         Vec3d var21 = current.add(0.0, 0.0, (double)(-step));
         outline.add(var21);
         current = var21.add(-x, 0.0, 0.0);
         outline.add(current);
      }

      outline.add(current.add(0.0, 0.0, (double)step * -2.0));

      for (Vec3d point : outline) {
         primitives.addLine(point, point.add(0.0, 5.0, 0.0), strokeColor, 2.0F);
      }

      for (int i = 0; i < outline.size() - 1; i++) {
         Vec3d first = outline.get(i);
         Vec3d second = outline.get(i + 1);
         Vec3d firstTop = first.add(0.0, 5.0, 0.0);
         Vec3d secondTop = second.add(0.0, 5.0, 0.0);
         primitives.addLine(first, second, strokeColor, 2.0F);
         primitives.addLine(firstTop, secondTop, strokeColor, 2.0F);
         quad(primitives, first, second, secondTop, firstTop, fillColor);
         primitives.addLine(first, secondTop, crossColor, 1.6F);
         primitives.addLine(second, firstTop, crossColor, 1.6F);
      }

      stratumFloor(primitives, origin, x, step, fillColor, crossColor);
      stratumFloor(primitives, origin.add(0.0, 5.0, 0.0), x, step, fillColor, crossColor);
   }

   private static void stratumFloor(GizmoDrawer primitives, Vec3d origin, double x, int step, int fillColor, int crossColor) {
      Vec3d cell = origin;
      stratumCell(primitives, origin, x, (double)step * 2.0, fillColor, crossColor);

      for (int i = 0; i < 3; i++) {
         cell = cell.add(x, 0.0, (double)step);
         stratumCell(primitives, cell, x, (double)step * 2.0, fillColor, crossColor);
      }

      stratumCell(primitives, cell.add(x, 0.0, (double)step), x, (double)step, fillColor, crossColor);
   }

   private static void stratumCell(GizmoDrawer primitives, Vec3d corner, double x, double depth, int fillColor, int crossColor) {
      Vec3d alongX = corner.add(x, 0.0, 0.0);
      Vec3d opposite = corner.add(x, 0.0, depth);
      Vec3d alongZ = corner.add(0.0, 0.0, depth);
      quad(primitives, corner, alongX, opposite, alongZ, fillColor);
      primitives.addLine(corner, opposite, crossColor, 1.6F);
      primitives.addLine(alongX, alongZ, crossColor, 1.6F);
   }

   private static void crossBox(GizmoDrawer primitives, Box box, int strokeColor, int fillColor, float strokeWidth) {
      Vec3d downNorthWest = new Vec3d(box.minX, box.minY, box.minZ);
      Vec3d downNorthEast = new Vec3d(box.maxX, box.minY, box.minZ);
      Vec3d downSouthEast = new Vec3d(box.maxX, box.minY, box.maxZ);
      Vec3d downSouthWest = new Vec3d(box.minX, box.minY, box.maxZ);
      Vec3d upNorthWest = new Vec3d(box.minX, box.maxY, box.minZ);
      Vec3d upNorthEast = new Vec3d(box.maxX, box.maxY, box.minZ);
      Vec3d upSouthEast = new Vec3d(box.maxX, box.maxY, box.maxZ);
      Vec3d upSouthWest = new Vec3d(box.minX, box.maxY, box.maxZ);
      quad(primitives, downNorthWest, downNorthEast, downSouthEast, downSouthWest, fillColor);
      quad(primitives, upNorthWest, upSouthWest, upSouthEast, upNorthEast, fillColor);
      quad(primitives, downNorthWest, upNorthWest, upNorthEast, downNorthEast, fillColor);
      quad(primitives, downSouthWest, downSouthEast, upSouthEast, upSouthWest, fillColor);
      quad(primitives, downNorthWest, downSouthWest, upSouthWest, upNorthWest, fillColor);
      quad(primitives, downNorthEast, upNorthEast, upSouthEast, downSouthEast, fillColor);
      primitives.addLine(downNorthWest, downNorthEast, strokeColor, strokeWidth);
      primitives.addLine(downNorthEast, downSouthEast, strokeColor, strokeWidth);
      primitives.addLine(downSouthEast, downSouthWest, strokeColor, strokeWidth);
      primitives.addLine(downSouthWest, downNorthWest, strokeColor, strokeWidth);
      primitives.addLine(downNorthWest, upNorthWest, strokeColor, strokeWidth);
      primitives.addLine(downNorthEast, upNorthEast, strokeColor, strokeWidth);
      primitives.addLine(downSouthEast, upSouthEast, strokeColor, strokeWidth);
      primitives.addLine(downSouthWest, upSouthWest, strokeColor, strokeWidth);
      primitives.addLine(upNorthWest, upNorthEast, strokeColor, strokeWidth);
      primitives.addLine(upNorthEast, upSouthEast, strokeColor, strokeWidth);
      primitives.addLine(upSouthEast, upSouthWest, strokeColor, strokeWidth);
      primitives.addLine(upSouthWest, upNorthWest, strokeColor, strokeWidth);
      int crossColor = ColorUtil.multiplyAlpha(strokeColor, 0.6F);
      float crossWidth = strokeWidth * 0.8F;
      primitives.addLine(downNorthWest, downSouthEast, crossColor, crossWidth);
      primitives.addLine(downNorthEast, downSouthWest, crossColor, crossWidth);
      primitives.addLine(upNorthWest, upSouthEast, crossColor, crossWidth);
      primitives.addLine(upNorthEast, upSouthWest, crossColor, crossWidth);
      primitives.addLine(downNorthWest, upNorthEast, crossColor, crossWidth);
      primitives.addLine(downNorthEast, upNorthWest, crossColor, crossWidth);
      primitives.addLine(downSouthWest, upSouthEast, crossColor, crossWidth);
      primitives.addLine(downSouthEast, upSouthWest, crossColor, crossWidth);
      primitives.addLine(downNorthWest, upSouthWest, crossColor, crossWidth);
      primitives.addLine(downSouthWest, upNorthWest, crossColor, crossWidth);
      primitives.addLine(downNorthEast, upSouthEast, crossColor, crossWidth);
      primitives.addLine(downSouthEast, upNorthEast, crossColor, crossWidth);
   }

   private static void quad(GizmoDrawer primitives, Vec3d first, Vec3d second, Vec3d third, Vec3d fourth, int fillColor) {
      primitives.addQuad(first, second, third, fourth, fillColor);
      primitives.addQuad(fourth, third, second, first, fillColor);
   }
}
