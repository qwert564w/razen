package org.ryzen.utils.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class LocalPlayerHistory {
   private static final int CAPACITY = 16;
   private static final LocalPlayerHistory.Snapshot[] BUFFER = new LocalPlayerHistory.Snapshot[16];
   private static int head;
   private static int size;

   private LocalPlayerHistory() {
   }

   public static void record(ClientPlayerEntity player) {
      head = (head + 1) % 16;
      BUFFER[head] = new LocalPlayerHistory.Snapshot(
         player.getEntityPos(), player.getVelocity(), player.isOnGround(), player.verticalCollision, player.groundCollision, (float)player.fallDistance
      );
      size = Math.min(size + 1, 16);
   }

   public static LocalPlayerHistory.Snapshot get(int ticksAgo) {
      return ticksAgo >= 0 && ticksAgo < size ? BUFFER[(head - ticksAgo % 16 + 16) % 16] : null;
   }

   public static boolean verticalCollision(int ticksAgo, boolean fallback) {
      LocalPlayerHistory.Snapshot snapshot = get(ticksAgo);
      return snapshot == null ? fallback : snapshot.verticalCollision();
   }

   public static boolean verticalCollisionBelow(int ticksAgo, boolean fallback) {
      LocalPlayerHistory.Snapshot snapshot = get(ticksAgo);
      return snapshot == null ? fallback : snapshot.verticalCollisionBelow();
   }

   public static void reset() {
      for (int i = 0; i < 16; i++) {
         BUFFER[i] = null;
      }

      head = 0;
      size = 0;
   }

   @Environment(EnvType.CLIENT)
   public static record Snapshot(Vec3d pos, Vec3d motion, boolean onGround, boolean verticalCollision, boolean verticalCollisionBelow, float fallDistance) {
   }
}
