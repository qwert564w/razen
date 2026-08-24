package org.ryzen.pve.economy;

import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos.Mutable;

@Environment(EnvType.CLIENT)
public final class NearbyEconomyBlocks {
   private NearbyEconomyBlocks() {
   }

   public static BlockPos nearestCraftingTable(ClientWorld level, ClientPlayerEntity player, int radius) {
      return nearest(level, player, radius, Math.min(16, radius), state -> state.isOf(Blocks.CRAFTING_TABLE));
   }

   public static BlockPos nearestSignedChest(ClientWorld level, ClientPlayerEntity player, int radius, String label) {
      String target = EconomyTextParser.normalize(label);
      return target.isEmpty()
         ? null
         : nearest(
            level,
            player,
            radius,
            Math.min(16, radius),
            state -> state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL),
            target
         );
   }

   public static boolean adjacentSignContains(ClientWorld level, BlockPos block, String label) {
      if (level != null && block != null && !EconomyTextParser.normalize(label).isEmpty()) {
         for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(block.offset(direction)) instanceof SignBlockEntity sign
               && (contains(sign.getFrontText(), label) || contains(sign.getBackText(), label))) {
               return true;
            }
         }

         BlockState state = level.getBlockState(block);
         if (state.getBlock() instanceof ChestBlock && state.contains(Properties.CHEST_TYPE) && state.contains(Properties.HORIZONTAL_FACING)) {
            ChestType type = (ChestType)state.get(Properties.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
               Direction facing = (Direction)state.get(Properties.HORIZONTAL_FACING);
               Direction partnerDirection = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
               BlockPos partner = block.offset(partnerDirection);

               for (Direction directionx : Direction.values()) {
                  if (level.getBlockEntity(partner.offset(directionx)) instanceof SignBlockEntity sign
                     && (contains(sign.getFrontText(), label) || contains(sign.getBackText(), label))) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static BlockPos nearest(ClientWorld level, ClientPlayerEntity player, int horizontalRadius, int verticalRadius, Predicate<BlockState> predicate) {
      return nearest(level, player, horizontalRadius, verticalRadius, predicate, null);
   }

   private static BlockPos nearest(
      ClientWorld level, ClientPlayerEntity player, int horizontalRadius, int verticalRadius, Predicate<BlockState> predicate, String requiredSign
   ) {
      if (level != null && player != null) {
         int horizontal = Math.max(1, Math.min(32, horizontalRadius));
         int vertical = Math.max(1, Math.min(16, verticalRadius));
         BlockPos origin = player.getBlockPos();
         Mutable cursor = new Mutable();
         BlockPos nearest = null;
         double nearestDistance = Double.POSITIVE_INFINITY;

         for (int y = -vertical; y <= vertical; y++) {
            for (int x = -horizontal; x <= horizontal; x++) {
               for (int z = -horizontal; z <= horizontal; z++) {
                  if (x * x + z * z <= horizontal * horizontal) {
                     cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                     if (level.isChunkLoaded(cursor.getX() >> 4, cursor.getZ() >> 4)) {
                        BlockState state = level.getBlockState(cursor);
                        if (predicate.test(state) && (requiredSign == null || adjacentSignContains(level, cursor, requiredSign))) {
                           double distance = cursor.getSquaredDistanceFromCenter(
                              player.getX(), player.getY() + (double)player.getStandingEyeHeight(), player.getZ()
                           );
                           if (distance < nearestDistance) {
                              nearestDistance = distance;
                              nearest = cursor.toImmutable();
                           }
                        }
                     }
                  }
               }
            }
         }

         return nearest;
      } else {
         return null;
      }
   }

   private static boolean contains(SignText text, String label) {
      if (text == null) {
         return false;
      } else {
         for (Text line : text.getMessages(false)) {
            if (line != null && EconomyTextParser.containsAny(line.getString(), label)) {
               return true;
            }
         }

         return false;
      }
   }
}
