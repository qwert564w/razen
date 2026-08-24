package org.ryzen.pve.navigation;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public interface Navigator {
   boolean isAvailable();

   void begin(NavigationOptions var1);

   void pathTo(BlockPos var1, int var2);

   void mine(int var1, Block... var2);

   void setMineBounds(BlockPos var1, BlockPos var2);

   default void setMineRenderColor(int argb) {
   }

   default void setMineAoeLevel(int level) {
   }

   boolean isPathing();

   boolean isMining();

   List<BlockPos> miningTargets();

   Optional<Double> estimatedTicksToGoal();

   Optional<BlockPos> currentGoal();

   String diagnostics();

   void cancel();

   void end();
}
