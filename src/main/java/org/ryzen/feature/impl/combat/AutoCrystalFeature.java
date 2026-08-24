package org.ryzen.feature.impl.combat;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.Gizmo;
import net.minecraft.world.debug.gizmo.GizmoDrawer;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.GizmoDrawing.CollectorScope;
import org.ryzen.context.MinecraftContext;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class AutoCrystalFeature extends Feature implements MinecraftContext {
   private static final long SWAP_REVERT_MS = 100L;
   private static final double PITCH_CAP = 89.0;
   private static final float LINE_WIDTH = 1.5F;
   public final NumberSetting range = this.register(new NumberSetting("Range", 5.0, 1.0, 6.0, 0.1, ""));
   public final NumberSetting placeDelay = this.register(new NumberSetting("Place Delay", 0.0, 0.0, 20.0, 1.0, "t"));
   public final NumberSetting breakDelay = this.register(new NumberSetting("Break Delay", 0.0, 0.0, 20.0, 1.0, "t"));
   public final BooleanSetting autoPlace = this.register(new BooleanSetting("Auto Place", true));
   public final BooleanSetting render = this.register(new BooleanSetting("Render", true));
   public final ModeSetting swapMode = this.register(new ModeSetting("Swap Mode", "Hand", "Hand", "Packet"));
   private BlockPos placePos;
   private PlayerEntity target;
   private int placeTicks;
   private int breakTicks;
   private int previousSlot = -1;
   private boolean pendingSwapRevert;
   private long swapRevertAt;
   private float renderYaw;
   private float renderPitch;

   public AutoCrystalFeature() {
      super("AutoCrystal", "Places and breaks end crystals on nearby players", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onEnable() {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         this.renderYaw = player.getYaw();
         this.renderPitch = player.getPitch();
      }

      this.resetSession();
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = mc.player;
      if (player != null && this.previousSlot != -1 && this.previousSlot < 9) {
         player.getInventory().setSelectedSlot(this.previousSlot);
      }

      this.resetSession();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.resetSession();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = mc.player;
      ClientWorld level = mc.world;
      if (player != null && level != null) {
         this.placeTicks++;
         this.breakTicks++;
         this.handleSwapRevert(player);
         this.target = this.findTarget(player, level);
         if (this.target == null) {
            this.placePos = null;
            this.renderYaw = player.getYaw();
            this.renderPitch = player.getPitch();
         } else {
            if (this.autoPlace.getValue() && (double)this.placeTicks >= this.placeDelay.getValue()) {
               this.placePos = this.findPlacePos(player, level);
               if (this.placePos != null) {
                  this.placeCrystal(player, this.placePos);
                  this.placeTicks = 0;
               }
            }

            if ((double)this.breakTicks >= this.breakDelay.getValue()) {
               EndCrystalEntity crystal = this.findCrystalToBreak(player, level);
               if (crystal != null) {
                  this.rotateToCrystal(player, crystal);
                  this.attackCrystal(player, crystal);
                  this.breakTicks = 0;
               }
            }
         }
      }
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && mc.player != null && mc.world != null && event.getClient().worldRenderer != null) {
         BlockPos pos = this.placePos;
         PlayerEntity currentTarget = this.target;
         if (pos != null && currentTarget != null) {
            Vec3d crystalCenter = new Vec3d((double)pos.getX() + 0.5, (double)pos.getY() + 1.0, (double)pos.getZ() + 0.5);
            double targetDist = currentTarget.getEntityPos().squaredDistanceTo(crystalCenter);
            double selfDist = mc.player.getEntityPos().squaredDistanceTo(crystalCenter);
            int color;
            if (targetDist <= 3.5 && selfDist > targetDist) {
               color = -1275003086;
            } else if (targetDist <= 6.0 && selfDist >= targetDist * 0.7) {
               color = -1593835776;
            } else {
               color = 2029998080;
            }

            Box box = new Box(pos);
            CollectorScope ignored = event.getClient().worldRenderer.startDrawingGizmos();

            try {
               GizmoDrawing.collect(new AutoCrystalFeature.CrystalGizmo(box, color)).ignoreOcclusion();
            } catch (Throwable var15) {
               if (ignored != null) {
                  try {
                     ignored.close();
                  } catch (Throwable var14) {
                     var15.addSuppressed(var14);
                  }
               }

               throw var15;
            }

            if (ignored != null) {
               ignored.close();
            }
         }
      }
   }

   private void handleSwapRevert(ClientPlayerEntity player) {
      if (this.pendingSwapRevert && this.swapMode.is("Hand")) {
         if (System.currentTimeMillis() - this.swapRevertAt >= 100L) {
            if (this.previousSlot != -1 && this.previousSlot < 9) {
               player.getInventory().setSelectedSlot(this.previousSlot);
               this.previousSlot = -1;
            }

            this.pendingSwapRevert = false;
         }
      }
   }

   private void resetSession() {
      this.placePos = null;
      this.target = null;
      this.placeTicks = 0;
      this.breakTicks = 0;
      this.previousSlot = -1;
      this.pendingSwapRevert = false;
      this.swapRevertAt = 0L;
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         this.renderYaw = player.getYaw();
         this.renderPitch = player.getPitch();
      }
   }

   private int findCrystalHotbarSlot(ClientPlayerEntity player) {
      for (int slot = 0; slot < 9; slot++) {
         if (player.getInventory().getStack(slot).isOf(Items.END_CRYSTAL)) {
            return slot;
         }
      }

      return -1;
   }

   private PlayerEntity findTarget(ClientPlayerEntity self, ClientWorld level) {
      PlayerEntity best = null;
      double bestDist = Double.MAX_VALUE;
      double maxDistSq = Math.pow(this.range.getValue() * 2.0, 2.0);

      for (PlayerEntity player : level.getPlayers()) {
         if (player != self && !player.isDead() && !(player.getHealth() <= 0.0F)) {
            double dist = self.squaredDistanceTo(player);
            if (!(dist > maxDistSq) && !(dist >= bestDist)) {
               bestDist = dist;
               best = player;
            }
         }
      }

      return best;
   }

   private BlockPos findPlacePos(ClientPlayerEntity player, ClientWorld level) {
      if (this.target == null) {
         return null;
      } else {
         BlockPos origin = player.getBlockPos();
         int radius = (int)Math.ceil(this.range.getValue());
         double rangeSq = this.range.getValue() * this.range.getValue();
         BlockPos best = null;
         double bestTargetDist = Double.MAX_VALUE;

         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               for (int dy = -radius; dy <= radius; dy++) {
                  BlockPos pos = origin.add(dx, dy, dz);
                  double px = (double)pos.getX() + 0.5;
                  double py = (double)pos.getY() + 0.5;
                  double pz = (double)pos.getZ() + 0.5;
                  if (!(player.squaredDistanceTo(px, py, pz) > rangeSq) && this.canPlaceCrystal(player, level, pos)) {
                     double targetDist = this.target.squaredDistanceTo((double)pos.getX() + 0.5, (double)pos.getY() + 1.0, (double)pos.getZ() + 0.5);
                     if (targetDist < bestTargetDist) {
                        bestTargetDist = targetDist;
                        best = pos.toImmutable();
                     }
                  }
               }
            }
         }

         return best;
      }
   }

   private boolean canPlaceCrystal(ClientPlayerEntity player, ClientWorld level, BlockPos base) {
      BlockState state = level.getBlockState(base);
      if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) {
         return false;
      } else {
         BlockPos feet = base.up();
         if (level.getBlockState(feet).isAir() && level.getBlockState(feet.up()).isAir()) {
            Box entityBox = new Box(
               (double)feet.getX(), (double)feet.getY(), (double)feet.getZ(), (double)feet.getX() + 1.0, (double)feet.getY() + 2.0, (double)feet.getZ() + 1.0
            );

            for (Entity entity : level.getEntities()) {
               if (entity != player && entity.getBoundingBox().intersects(entityBox)) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private EndCrystalEntity findCrystalToBreak(ClientPlayerEntity player, ClientWorld level) {
      if (this.target == null) {
         return null;
      } else {
         EndCrystalEntity best = null;
         double bestTargetDist = Double.MAX_VALUE;
         double rangeSq = this.range.getValue() * this.range.getValue();

         for (Entity entity : level.getEntities()) {
            if (entity instanceof EndCrystalEntity) {
               EndCrystalEntity crystal = (EndCrystalEntity)entity;
               if (!(player.squaredDistanceTo(crystal) > rangeSq)) {
                  double targetDist = this.target.squaredDistanceTo(crystal);
                  if (targetDist < bestTargetDist) {
                     bestTargetDist = targetDist;
                     best = crystal;
                  }
               }
            }
         }

         return best;
      }
   }

   private void placeCrystal(ClientPlayerEntity player, BlockPos pos) {
      int slot = this.findCrystalHotbarSlot(player);
      if (slot != -1) {
         MinecraftClient client = mc;
         ClientPlayerInteractionManager gameMode = client.interactionManager;
         if (gameMode != null && player.networkHandler != null) {
            this.rotateToPlace(player, pos);
            int previous = player.getInventory().getSelectedSlot();
            if (this.swapMode.is("Hand")) {
               player.getInventory().setSelectedSlot(slot);
            } else {
               player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            Vec3d eyes = player.getEyePos();
            Box blockBox = new Box(pos);
            Vec3d hit = closestPoint(eyes, blockBox);
            Vec3d look = hit.subtract(eyes).normalize();
            Direction side = Direction.getFacing(look.x, look.y, look.z);
            BlockHitResult hitResult = new BlockHitResult(hit, side, pos, false);
            gameMode.interactBlock(player, Hand.MAIN_HAND, hitResult);
            player.swingHand(Hand.MAIN_HAND);
            if (this.swapMode.is("Hand")) {
               this.previousSlot = previous;
               this.pendingSwapRevert = true;
               this.swapRevertAt = System.currentTimeMillis();
            } else {
               player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previous));
            }
         }
      }
   }

   private void attackCrystal(ClientPlayerEntity player, EndCrystalEntity crystal) {
      if (crystal != null && mc.interactionManager != null) {
         mc.interactionManager.attackEntity(player, crystal);
         player.swingHand(Hand.MAIN_HAND);
      }
   }

   private void rotateToPlace(ClientPlayerEntity player, BlockPos pos) {
      Vec3d eyes = player.getEyePos();
      Vec3d delta = closestPoint(eyes, new Box(pos)).subtract(eyes);
      this.applyRotation(player, delta);
   }

   private void rotateToCrystal(ClientPlayerEntity player, EndCrystalEntity crystal) {
      Vec3d eyes = player.getEyePos();
      Vec3d delta = closestPoint(eyes, crystal.getBoundingBox()).subtract(eyes);
      this.applyRotation(player, delta);
   }

   private void applyRotation(ClientPlayerEntity player, Vec3d delta) {
      float yaw = (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
      this.smoothRotation(player, yaw, pitch);
   }

   private void smoothRotation(ClientPlayerEntity player, float targetYaw, float targetPitch) {
      float gcd = gcd();
      float yaw = this.renderYaw + MathHelper.wrapDegrees(targetYaw - this.renderYaw);
      float pitch = this.renderPitch + (targetPitch - this.renderPitch);
      yaw -= (yaw - this.renderYaw) % gcd;
      pitch -= (pitch - this.renderPitch) % gcd;
      pitch = (float)MathHelper.clamp((double)pitch, -89.0, 89.0);
      if (yaw == this.renderYaw && pitch == this.renderPitch) {
         int jitter = ThreadLocalRandom.current().nextInt(1, 4);
         float sign = ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F;
         if (ThreadLocalRandom.current().nextBoolean()) {
            yaw += gcd * (float)jitter * sign;
         } else {
            pitch += gcd * (float)jitter * sign;
         }

         pitch = (float)MathHelper.clamp((double)pitch, -89.0, 89.0);
      }

      RotationContext.setRotation(yaw, pitch);
      this.renderYaw = yaw;
      this.renderPitch = pitch;
   }

   private static float gcd() {
      double sensitivity = (Double)MinecraftClient.getInstance().options.getMouseSensitivity().getValue() * 0.6 + 0.2;
      double factor = sensitivity * sensitivity * sensitivity * 1.2;
      return (float)(factor * 0.15);
   }

   private static Vec3d closestPoint(Vec3d point, Box box) {
      return new Vec3d(
         MathHelper.clamp(point.x, box.minX, box.maxX), MathHelper.clamp(point.y, box.minY, box.maxY), MathHelper.clamp(point.z, box.minZ, box.maxZ)
      );
   }

   @Environment(EnvType.CLIENT)
   private static record CrystalGizmo(Box box, int color) implements Gizmo {
      public void draw(GizmoDrawer consumer, float opacity) {
         Vec3d a = new Vec3d(this.box.minX, this.box.minY, this.box.minZ);
         Vec3d b = new Vec3d(this.box.maxX, this.box.minY, this.box.minZ);
         Vec3d c = new Vec3d(this.box.maxX, this.box.minY, this.box.maxZ);
         Vec3d d = new Vec3d(this.box.minX, this.box.minY, this.box.maxZ);
         Vec3d e = new Vec3d(this.box.minX, this.box.maxY, this.box.minZ);
         Vec3d f = new Vec3d(this.box.maxX, this.box.maxY, this.box.minZ);
         Vec3d g = new Vec3d(this.box.maxX, this.box.maxY, this.box.maxZ);
         Vec3d h = new Vec3d(this.box.minX, this.box.maxY, this.box.maxZ);
         int fill = this.color & 16777215 | Math.round((float)(this.color >>> 24) * 0.3F) << 24;
         consumer.addQuad(a, b, c, d, fill);
         consumer.addQuad(e, f, g, h, fill);
         consumer.addQuad(a, b, f, e, fill);
         consumer.addQuad(d, c, g, h, fill);
         consumer.addQuad(a, d, h, e, fill);
         consumer.addQuad(b, c, g, f, fill);
         consumer.addLine(a, b, this.color, 1.5F);
         consumer.addLine(b, c, this.color, 1.5F);
         consumer.addLine(c, d, this.color, 1.5F);
         consumer.addLine(d, a, this.color, 1.5F);
         consumer.addLine(e, f, this.color, 1.5F);
         consumer.addLine(f, g, this.color, 1.5F);
         consumer.addLine(g, h, this.color, 1.5F);
         consumer.addLine(h, e, this.color, 1.5F);
         consumer.addLine(a, e, this.color, 1.5F);
         consumer.addLine(b, f, this.color, 1.5F);
         consumer.addLine(c, g, this.color, 1.5F);
         consumer.addLine(d, h, this.color, 1.5F);
      }
   }
}
