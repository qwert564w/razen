package org.ryzen.pve.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;

@Environment(EnvType.CLIENT)
public final class ContainerClusterScanner {
   private BlockPos center;
   private int radius;
   private int verticalRadius;
   private int cursor;
   private int volume;
   private Set<Block> targets = Set.of();
   private final List<BlockPos> matches = new ArrayList<>();

   public void begin(BlockPos center, int radius, Set<Block> targets) {
      this.center = center == null ? null : center.toImmutable();
      this.radius = Math.max(1, radius);
      this.verticalRadius = Math.min(12, this.radius);
      int width = this.radius * 2 + 1;
      int height = this.verticalRadius * 2 + 1;
      this.volume = width * width * height;
      this.cursor = 0;
      this.targets = targets == null ? Set.of() : Set.copyOf(targets);
      this.matches.clear();
   }

   public Optional<ContainerClusterScanner.Result> scan(ClientWorld level, int budget) {
      if (level != null && this.center != null && !this.targets.isEmpty()) {
         int width = this.radius * 2 + 1;
         int height = this.verticalRadius * 2 + 1;
         int remaining = Math.max(1, budget);
         Mutable mutable = new Mutable();

         while (this.cursor < this.volume && remaining-- > 0) {
            int index = this.cursor++;
            int xIndex = index % width;
            int zIndex = index / width % width;
            int yIndex = index / (width * width) % height;
            mutable.set(this.center.getX() + xIndex - this.radius, this.center.getY() + yIndex - this.verticalRadius, this.center.getZ() + zIndex - this.radius);
            if (level.isChunkLoaded(mutable) && this.targets.contains(level.getBlockState(mutable).getBlock())) {
               this.matches.add(mutable.toImmutable());
            }
         }

         return this.cursor < this.volume ? Optional.empty() : Optional.of(new ContainerClusterScanner.Result(this.center, List.copyOf(this.matches)));
      } else {
         return Optional.empty();
      }
   }

   public boolean isRunning() {
      return this.center != null && this.cursor < this.volume;
   }

   public void reset() {
      this.center = null;
      this.cursor = 0;
      this.volume = 0;
      this.targets = Set.of();
      this.matches.clear();
   }

   @Environment(EnvType.CLIENT)
   public static record Result(BlockPos scanCenter, List<BlockPos> matches) {
      public Optional<BlockPos> nearestTo(BlockPos position) {
         return position == null
            ? Optional.empty()
            : this.matches.stream().min((first, second) -> Double.compare(first.getSquaredDistance(position), second.getSquaredDistance(position)));
      }
   }
}
