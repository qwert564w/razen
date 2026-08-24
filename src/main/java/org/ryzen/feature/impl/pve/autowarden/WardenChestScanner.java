package org.ryzen.feature.impl.pve.autowarden;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Type;

@Environment(EnvType.CLIENT)
final class WardenChestScanner {
   private static final long OBSERVATION_TTL_MILLIS = 1800000L;
   private static final long FAILED_TTL_MILLIS = 90000L;
   private static final int TIMER_CHEST_SEARCH_RADIUS = 2;
   private final Map<BlockPos, WardenChestScanner.MutableChest> known = new HashMap<>();
   private final Map<BlockPos, Long> failedUntil = new HashMap<>();
   private final Set<BlockPos> looted = new HashSet<>();

   List<WardenChestScanner.ChestObservation> scanLootChests(ClientWorld level, ClientPlayerEntity player, int horizontalRadius, int crowdRadius, long nowMillis) {
      int radius = Math.max(8, Math.min(128, horizontalRadius));
      Box entityBounds = player.getBoundingBox().expand((double)radius, 32.0, (double)radius);

      for (ArmorStandEntity stand : level.getEntitiesByClass(
         ArmorStandEntity.class, entityBounds, entity -> entity.hasCustomName() && entity.getCustomName() != null
      )) {
         AutoWardenParsers.parseChestTimer(stand.getCustomName().getString())
            .ifPresent(timer -> findContainerNear(level, stand.getBlockPos()).ifPresent(position -> this.observeTimer(position, timer, nowMillis)));
      }

      int directRadius = Math.min(48, radius);
      BlockPos origin = player.getBlockPos();

      for (int x = -directRadius; x <= directRadius; x++) {
         for (int z = -directRadius; z <= directRadius; z++) {
            for (int y = -10; y <= 10; y++) {
               BlockPos position = origin.add(x, y, z);
               if (level.isChunkLoaded(position) && isContainer(level.getBlockState(position))) {
                  BlockPos canonical = canonicalContainer(level, position);
                  this.known.computeIfAbsent(canonical, ignored -> new WardenChestScanner.MutableChest(canonical, -1, nowMillis, false, false));
               }
            }
         }
      }

      this.known.values().removeIf(valuex -> nowMillis - valuex.lastSeenAt > 1800000L);
      this.failedUntil.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
      List<WardenChestScanner.ChestObservation> result = new ArrayList<>();

      for (WardenChestScanner.MutableChest value : this.known.values()) {
         if (!this.looted.contains(value.position) && this.failedUntil.getOrDefault(value.position, 0L) <= nowMillis) {
            int crowd = nearbyPlayers(level, player, value.position, crowdRadius);
            result.add(value.snapshot(crowd));
         }
      }

      result.sort(Comparator.comparingLong(valuex -> valuex.position().asLong()));
      return List.copyOf(result);
   }

   Optional<WardenChestScanner.ChestObservation> selectBest(
      Collection<WardenChestScanner.ChestObservation> candidates, Vec3d origin, int maxWaitSeconds, boolean avoidCrowded, double crowdPenalty, long nowMillis
   ) {
      WardenChestScanner.ChestObservation selected = null;
      double selectedScore = Double.POSITIVE_INFINITY;

      for (WardenChestScanner.ChestObservation candidate : candidates) {
         int remaining = candidate.remainingSeconds(nowMillis);
         if (!candidate.timerKnown() || remaining <= Math.max(0, maxWaitSeconds)) {
            double distance = Vec3d.ofCenter(candidate.position()).distanceTo(origin);
            double unknownPenalty = candidate.timerKnown() ? 0.0 : 30.0;
            double score = (double)remaining * 4.0
               + distance
               + unknownPenalty
               + (avoidCrowded ? (double)candidate.nearbyPlayers() * Math.max(0.0, crowdPenalty) : 0.0);
            if (score < selectedScore) {
               selected = candidate;
               selectedScore = score;
            }
         }
      }

      return Optional.ofNullable(selected);
   }

   List<WardenChestScanner.StorageChest> scanStorage(ClientWorld level, BlockPos center, int radius, String restockKeyword, String sellKeyword) {
      int scanRadius = Math.max(4, Math.min(48, radius));
      String restock = AutoWardenParsers.normalize(restockKeyword);
      String sell = AutoWardenParsers.normalize(sellKeyword);
      Map<BlockPos, WardenChestScanner.StorageChest> result = new LinkedHashMap<>();

      for (int x = -scanRadius; x <= scanRadius; x++) {
         for (int z = -scanRadius; z <= scanRadius; z++) {
            for (int y = -8; y <= 8; y++) {
               BlockPos position = center.add(x, y, z);
               if (level.isChunkLoaded(position) && isContainer(level.getBlockState(position))) {
                  BlockPos canonical = canonicalContainer(level, position);
                  if (!result.containsKey(canonical)) {
                     String sign = signText(level, canonical);
                     boolean signed = !sign.isBlank();
                     WardenChestScanner.StorageKind kind;
                     if (signed && !sell.isBlank() && sign.contains(sell)) {
                        kind = WardenChestScanner.StorageKind.SELL;
                     } else if (!signed || !restock.isBlank() && !sign.contains(restock)) {
                        if (signed) {
                           kind = WardenChestScanner.StorageKind.OTHER_SIGNED;
                        } else {
                           kind = WardenChestScanner.StorageKind.DEPOSIT;
                        }
                     } else {
                        kind = WardenChestScanner.StorageKind.RESTOCK;
                     }

                     result.put(canonical, new WardenChestScanner.StorageChest(canonical, kind, sign));
                  }
               }
            }
         }
      }

      return result.values().stream().sorted(Comparator.comparingDouble(value -> value.position().getSquaredDistance(center))).toList();
   }

   void markLooted(BlockPos position) {
      if (position != null) {
         this.looted.add(position.toImmutable());
         WardenChestScanner.MutableChest value = this.known.get(position);
         if (value != null) {
            value.stolen = true;
         }
      }
   }

   void markFailed(BlockPos position, long nowMillis) {
      if (position != null) {
         this.failedUntil.put(position.toImmutable(), nowMillis + 90000L);
      }
   }

   void clearLootedCycle() {
      this.looted.clear();
      this.known.values().forEach(value -> value.stolen = false);
   }

   void clearWorld() {
      this.known.clear();
      this.failedUntil.clear();
      this.looted.clear();
   }

   private void observeTimer(BlockPos position, AutoWardenParsers.TimerReading timer, long nowMillis) {
      BlockPos immutable = position.toImmutable();
      WardenChestScanner.MutableChest existing = this.known.get(immutable);
      if (existing == null) {
         this.known.put(immutable, new WardenChestScanner.MutableChest(immutable, timer.remainingSeconds(), nowMillis, true, timer.openNow()));
      } else {
         existing.observedSeconds = timer.remainingSeconds();
         existing.observedAt = nowMillis;
         existing.lastSeenAt = nowMillis;
         existing.timerKnown = true;
         existing.openNow = timer.openNow();
      }
   }

   private static Optional<BlockPos> findContainerNear(ClientWorld level, BlockPos stand) {
      BlockPos selected = null;
      double selectedDistance = Double.POSITIVE_INFINITY;

      for (int x = -2; x <= 2; x++) {
         for (int z = -2; z <= 2; z++) {
            for (int y = -3; y <= 1; y++) {
               BlockPos position = stand.add(x, y, z);
               if (isContainer(level.getBlockState(position))) {
                  double distance = position.getSquaredDistance(stand);
                  if (distance < selectedDistance) {
                     selected = canonicalContainer(level, position);
                     selectedDistance = distance;
                  }
               }
            }
         }
      }

      return Optional.ofNullable(selected);
   }

   private static int nearbyPlayers(ClientWorld level, ClientPlayerEntity self, BlockPos chest, int radius) {
      if (radius <= 0) {
         return 0;
      } else {
         Box bounds = new Box(chest).expand((double)radius);
         return level.getEntitiesByClass(PlayerEntity.class, bounds, player -> player != self).size();
      }
   }

   private static boolean isContainer(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL;
   }

   private static BlockPos canonicalContainer(ClientWorld level, BlockPos position) {
      Block block = level.getBlockState(position).getBlock();
      if (block != Blocks.CHEST && block != Blocks.TRAPPED_CHEST) {
         return position.toImmutable();
      } else {
         BlockPos selected = position;

         for (Direction direction : Type.HORIZONTAL) {
            BlockPos adjacent = position.offset(direction);
            if (level.getBlockState(adjacent).getBlock() == block && adjacent.asLong() < selected.asLong()) {
               selected = adjacent;
            }
         }

         return selected.toImmutable();
      }
   }

   private static String signText(ClientWorld level, BlockPos container) {
      StringBuilder result = new StringBuilder();
      appendAdjacentSigns(level, container, result);
      Block block = level.getBlockState(container).getBlock();
      if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
         for (Direction direction : Type.HORIZONTAL) {
            BlockPos adjacent = container.offset(direction);
            if (level.getBlockState(adjacent).getBlock() == block) {
               appendAdjacentSigns(level, adjacent, result);
            }
         }
      }

      return AutoWardenParsers.normalize(result.toString());
   }

   private static void appendAdjacentSigns(ClientWorld level, BlockPos container, StringBuilder output) {
      for (Direction direction : Direction.values()) {
         if (level.getBlockEntity(container.offset(direction)) instanceof SignBlockEntity sign) {
            appendSignSide(sign, "getFrontText", output);
            appendSignSide(sign, "getBackText", output);
         }
      }
   }

   private static void appendSignSide(SignBlockEntity sign, String accessor, StringBuilder output) {
      try {
         Method sideMethod = SignBlockEntity.class.getMethod(accessor);
         Object side = sideMethod.invoke(sign);
         Method messages = findMethod(side.getClass(), "getMessages", boolean.class);
         if (messages != null) {
            appendComponents(messages.invoke(side, false), output);
            return;
         }

         Method message = findMethod(side.getClass(), "getMessage", int.class, boolean.class);
         if (message != null) {
            for (int line = 0; line < 4; line++) {
               appendComponent(message.invoke(side, line, false), output);
            }
         }
      } catch (RuntimeException | ReflectiveOperationException var8) {
      }
   }

   private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
      try {
         return type.getMethod(name, parameters);
      } catch (NoSuchMethodException var4) {
         return null;
      }
   }

   private static void appendComponents(Object value, StringBuilder output) {
      if (value != null && value.getClass().isArray()) {
         for (int index = 0; index < Array.getLength(value); index++) {
            appendComponent(Array.get(value, index), output);
         }
      }
   }

   private static void appendComponent(Object value, StringBuilder output) {
      if (value instanceof Text component) {
         output.append(' ').append(component.getString());
      }
   }

   @Environment(EnvType.CLIENT)
   static record ChestObservation(
      BlockPos position, int observedSeconds, long observedAtMillis, boolean timerKnown, boolean openNow, boolean stolen, int nearbyPlayers
   ) {
      int remainingSeconds(long nowMillis) {
         if (this.timerKnown && !this.openNow) {
            long elapsed = Math.max(0L, nowMillis - this.observedAtMillis) / 1000L;
            return (int)Math.max(0L, (long)this.observedSeconds - elapsed);
         } else {
            return 0;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class MutableChest {
      private final BlockPos position;
      private int observedSeconds;
      private long observedAt;
      private long lastSeenAt;
      private boolean timerKnown;
      private boolean openNow;
      private boolean stolen;

      private MutableChest(BlockPos position, int observedSeconds, long observedAt, boolean timerKnown, boolean openNow) {
         this.position = position;
         this.observedSeconds = observedSeconds;
         this.observedAt = observedAt;
         this.lastSeenAt = observedAt;
         this.timerKnown = timerKnown;
         this.openNow = openNow;
      }

      private WardenChestScanner.ChestObservation snapshot(int nearbyPlayers) {
         return new WardenChestScanner.ChestObservation(
            this.position, this.observedSeconds, this.observedAt, this.timerKnown, this.openNow, this.stolen, nearbyPlayers
         );
      }
   }

   @Environment(EnvType.CLIENT)
   static record StorageChest(BlockPos position, WardenChestScanner.StorageKind kind, String signText) {
   }

   @Environment(EnvType.CLIENT)
   static enum StorageKind {
      DEPOSIT,
      RESTOCK,
      SELL,
      OTHER_SIGNED;
   }
}
