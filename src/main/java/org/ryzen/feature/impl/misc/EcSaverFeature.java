package org.ryzen.feature.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.ryzen.event.events.input.MouseInputEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.InputBindSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class EcSaverFeature extends Feature implements PlayerContext {
   public final InputBindSetting key = this.register(new InputBindSetting("Open Key", -1));
   private BlockPos pending;

   public EcSaverFeature() {
      super("EcSaver", "Opens the nearest ender chest on a key press", FeatureCategory.MISC, -1);
   }

   @Override
   protected void onDisable() {
      this.pending = null;
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 0 && this.key.matches(event.getKey())) {
         this.locate();
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 0 && this.key.matchesMouse(event.getButton())) {
         this.locate();
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      BlockPos target = this.pending;
      if (target != null) {
         this.pending = null;
         MinecraftClient client = event.getClient();
         ClientPlayerEntity player = client.player;
         if (player != null && client.interactionManager != null) {
            Vec3d center = Vec3d.ofCenter(target);
            Vec3d delta = center.subtract(player.getEyePos());
            float yaw = MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
            float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
            if (player.networkHandler != null) {
               player.networkHandler.sendPacket(new LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
            }

            client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(center, Direction.UP, target, false));
         }
      }
   }

   private void locate() {
      ClientPlayerEntity player = this.localPlayer();
      ClientWorld level = this.level();
      if (player != null && level != null) {
         BlockPos nearest = null;
         double nearestDistance = Double.MAX_VALUE;
         int viewDistance = MinecraftClient.getInstance().options.getClampedViewDistance();
         int chunkX = player.getChunkPos().x;
         int chunkZ = player.getChunkPos().z;

         for (int x = chunkX - viewDistance; x <= chunkX + viewDistance; x++) {
            for (int z = chunkZ - viewDistance; z <= chunkZ + viewDistance; z++) {
               WorldChunk chunk = level.getChunkManager().getWorldChunk(x, z, false);
               if (chunk != null) {
                  for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                     if (blockEntity instanceof EnderChestBlockEntity) {
                        BlockPos pos = blockEntity.getPos();
                        double distance = player.getBlockPos().getSquaredDistance(pos);
                        if (distance < nearestDistance) {
                           nearestDistance = distance;
                           nearest = pos;
                        }
                     }
                  }
               }
            }
         }

         if (nearest == null) {
            ChatUtil.error("EcSaver: эндер-сундук не найден");
         } else {
            this.pending = nearest;
            ChatUtil.success("EcSaver: сундук на " + nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ());
         }
      }
   }
}
