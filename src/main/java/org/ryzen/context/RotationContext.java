package org.ryzen.context;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class RotationContext implements MinecraftContext {
   private static final float MOUSE_TURN_SCALE = 0.15F;
   private static float freeYaw;
   private static float freePitch;
   private static boolean active;
   private static boolean hasLastAngle;
   private static float lastYaw;
   private static float lastPitch;
   private static float lastHeadYaw;
   private static float lastBodyYaw;

   private RotationContext() {
   }

   public static void apply(float yawDelta, float pitchDelta) {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         if (!active) {
            freeYaw = player.getYaw();
            freePitch = player.getPitch();
            active = true;
         }

         rememberRenderAngles(player);
         float nextYaw = player.headYaw + yawDelta;
         float nextPitch = MathHelper.clamp(player.getPitch() + pitchDelta, -90.0F, 90.0F);
         player.setYaw(nextYaw);
         player.setPitch(nextPitch);
         player.headYaw = nextYaw;
      }
   }

   public static void setRotation(float yaw, float pitch) {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         apply(MathHelper.wrapDegrees(yaw - player.headYaw), MathHelper.wrapDegrees(pitch - player.getPitch()));
      }
   }

   public static void rotateTo(ClientPlayerEntity player, Entity target) {
      Vec3d delta = target.getEyePos().subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      setRotation(yaw, pitch);
   }

   public static void rotateToPosition(ClientPlayerEntity player, Vec3d pos) {
      Vec3d delta = pos.subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      setRotation(yaw, pitch);
   }

   public static float getFovToEntity(ClientPlayerEntity player, Entity entity) {
      double diffX = entity.getX() - player.getX();
      double diffZ = entity.getZ() - player.getZ();
      float yaw = (float)(Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
      float deltaYaw = MathHelper.wrapDegrees(yaw - mc.gameRenderer.getCamera().getYaw());
      return Math.abs(deltaYaw);
   }

   public static void syncFreeLook(float yaw, float pitch) {
      if (!active) {
         freeYaw = yaw;
         freePitch = pitch;
      }
   }

   public static boolean onMouseTurn(double yawInput, double pitchInput) {
      if (!active) {
         return false;
      } else {
         freeYaw += (float)yawInput * 0.15F;
         freePitch = MathHelper.clamp(freePitch + (float)pitchInput * 0.15F, -90.0F, 90.0F);
         return true;
      }
   }

   public static void applyRenderInterpolation() {
      if (active && hasLastAngle) {
         ClientPlayerEntity player = mc.player;
         if (player == null) {
            hasLastAngle = false;
         } else {
            player.lastYaw = lastYaw;
            player.lastPitch = lastPitch;
            player.lastHeadYaw = lastHeadYaw;
            player.lastBodyYaw = lastBodyYaw;
         }
      }
   }

   public static void clear() {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         if (active) {
            player.setYaw(freeYaw);
            player.setPitch(freePitch);
            player.headYaw = freeYaw;
         }

         freeYaw = player.getYaw();
         freePitch = player.getPitch();
      }

      active = false;
      hasLastAngle = false;
   }

   private static void rememberRenderAngles(ClientPlayerEntity player) {
      lastYaw = player.getYaw();
      lastPitch = player.getPitch();
      lastHeadYaw = player.headYaw;
      lastBodyYaw = player.bodyYaw;
      hasLastAngle = true;
   }
   public static float getFreeYaw() {
      return freeYaw;
   }
   public static float getFreePitch() {
      return freePitch;
   }
   public static boolean isActive() {
      return active;
   }
}
