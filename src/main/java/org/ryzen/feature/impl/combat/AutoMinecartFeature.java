package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class AutoMinecartFeature extends Feature {
   private static final int SIMULATION_STEPS = 150;
   private static final double WATER_DRAG = 0.6;
   private static final double AIR_DRAG = 0.99;
   private static final double GRAVITY = 0.05;
   private static final double UNOWNED_ARROW_RANGE_SQR = 36.0;
   private static final double MIN_ARROW_SPEED_SQR = 0.01;
   private static final int HOTBAR_SIZE = 9;
   private static final int INVENTORY_SIZE = 36;
   public final NumberSetting distance = this.register(new NumberSetting("Distance", 4.5, 1.0, 6.0, 0.1, " blocks"));
   public final BooleanSetting useInventory = this.register(new BooleanSetting("Take From Inventory", true));
   private BlockPos pendingPos;
   private boolean placingRail;
   private boolean restoreSlot;
   private int originalSlot;
   private int railSlot = -1;
   private int cartSlot = -1;
   private int trackedArrowId = -1;
   private int railBorrowFrom = -1;
   private int railBorrowTo = -1;
   private int cartBorrowFrom = -1;
   private int cartBorrowTo = -1;

   public AutoMinecartFeature() {
      super("AutoMinecart", "Places a rail and a TNT minecart where your arrow will land", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onEnable() {
      this.clearPending();
   }

   @Override
   protected void onDisable() {
      MinecraftClient client = MinecraftClient.getInstance();
      ClientPlayerEntity player = client.player;
      if (player != null && client.interactionManager != null && (this.pendingPos != null || this.restoreSlot)) {
         select(player, this.originalSlot);
         this.returnBorrowedSlots(client, player);
      }

      this.clearPending();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.clearPending();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      ClientWorld level = client.world;
      if (player == null || level == null || client.interactionManager == null) {
         this.clearPending();
      } else if (this.restoreSlot) {
         this.restoreSlot = false;
         select(player, this.originalSlot);
         this.returnBorrowedSlots(client, player);
      } else if (this.pendingPos == null) {
         this.scanForArrow(client, player, level);
      } else {
         this.continuePlacement(client, player, level);
      }
   }

   private void scanForArrow(MinecraftClient client, ClientPlayerEntity player, ClientWorld level) {
      for (Entity entity : level.getEntities()) {
         if (entity instanceof PersistentProjectileEntity) {
            PersistentProjectileEntity arrow = (PersistentProjectileEntity)entity;
            if (arrow.getId() != this.trackedArrowId) {
               Entity owner = arrow.getOwner();
               if ((owner == null || owner == player)
                  && (owner != null || !(arrow.squaredDistanceTo(player) > 36.0))
                  && !(arrow.getVelocity().lengthSquared() < 0.01)
                  && this.beginPlacement(client, player, level, arrow)) {
                  this.trackedArrowId = arrow.getId();
                  return;
               }
            }
         }
      }
   }

   private boolean beginPlacement(MinecraftClient client, ClientPlayerEntity player, ClientWorld level, PersistentProjectileEntity arrow) {
      BlockHitResult impact = this.simulate(level, player, arrow);
      if (impact != null && impact.getType() == Type.BLOCK) {
         Direction face = impact.getSide();
         Vec3d nudged = impact.getPos().add((double)face.getOffsetX() * 0.02, (double)face.getOffsetY() * 0.02, (double)face.getOffsetZ() * 0.02);
         BlockPos target = this.findPlaceablePos(level, BlockPos.ofFloored(nudged));
         if (target != null && this.inReach(player, target)) {
            int rail = findInHotbar(player, Items.RAIL);
            int cart = findInHotbar(player, Items.TNT_MINECART);
            if (rail == -1 && this.useInventory.getValue()) {
               rail = this.borrow(client, player, Items.RAIL, cart, true);
            }

            if (cart == -1 && this.useInventory.getValue()) {
               cart = this.borrow(client, player, Items.TNT_MINECART, rail, false);
            }

            if (rail != -1 && cart != -1) {
               this.pendingPos = target;
               this.railSlot = rail;
               this.cartSlot = cart;
               this.placingRail = true;
               this.originalSlot = player.getInventory().getSelectedSlot();
               return true;
            } else {
               this.returnBorrowedSlots(client, player);
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private BlockHitResult simulate(ClientWorld level, ClientPlayerEntity player, PersistentProjectileEntity arrow) {
      Vec3d position = arrow.getEntityPos();
      Vec3d velocity = arrow.getVelocity();

      for (int step = 0; step <= 150; step++) {
         Vec3d from = position;
         position = position.add(velocity);
         boolean inWater = arrow.isTouchingWater() || level.getBlockState(BlockPos.ofFloored(from)).isOf(Blocks.WATER);
         velocity = velocity.multiply(inWater ? 0.6 : 0.99);
         if (!arrow.hasNoGravity()) {
            velocity = new Vec3d(velocity.x, velocity.y - 0.05, velocity.z);
         }

         BlockHitResult hit = level.raycast(new RaycastContext(from, position, ShapeType.COLLIDER, FluidHandling.NONE, player));
         if (hit.getType() == Type.BLOCK) {
            return hit;
         }

         if (position.y <= (double)level.getBottomY()) {
            break;
         }
      }

      return null;
   }

   private BlockPos findPlaceablePos(ClientWorld level, BlockPos impact) {
      int[][] offsets = new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

      for (int drop = 0; drop <= 6; drop++) {
         int y = impact.getY() - drop;

         for (int[] offset : offsets) {
            BlockPos candidate = new BlockPos(impact.getX() + offset[0], y, impact.getZ() + offset[1]);
            if (isPlaceable(level, candidate)) {
               return candidate;
            }
         }
      }

      return null;
   }

   private static boolean isPlaceable(ClientWorld level, BlockPos pos) {
      BlockPos below = pos.down();
      return level.getBlockState(pos).isReplaceable() && level.getBlockState(below).isSideSolidFullSquare(level, below, Direction.UP);
   }

   private void continuePlacement(MinecraftClient client, ClientPlayerEntity player, ClientWorld level) {
      BlockPos pos = this.pendingPos;
      if (!this.inReach(player, pos)) {
         this.abortPlacement();
      } else if (this.placingRail) {
         BlockPos below = pos.down();
         if (level.getBlockState(pos).isReplaceable() && level.getBlockState(below).isSideSolidFullSquare(level, below, Direction.UP)) {
            this.faceAndSelect(player, pos, this.railSlot);
            Vec3d hitVec = new Vec3d((double)below.getX() + 0.5, (double)below.getY() + 1.0, (double)below.getZ() + 0.5);
            client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(hitVec, Direction.UP, below, false));
            player.swingHand(Hand.MAIN_HAND);
            this.placingRail = false;
         } else {
            this.abortPlacement();
         }
      } else {
         this.faceAndSelect(player, pos, this.cartSlot);
         Vec3d hitVec = Vec3d.ofCenter(pos).add(0.0, 0.5, 0.0);
         client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(hitVec, Direction.UP, pos, false));
         player.swingHand(Hand.MAIN_HAND);
         this.pendingPos = null;
         this.restoreSlot = true;
      }
   }

   private void abortPlacement() {
      this.pendingPos = null;
      this.placingRail = false;
      this.restoreSlot = true;
   }

   private void faceAndSelect(ClientPlayerEntity player, BlockPos pos, int slot) {
      Vec3d center = Vec3d.ofCenter(pos);
      Vec3d delta = center.subtract(player.getEyePos());
      float yaw = MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
      float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
      if (player.networkHandler != null) {
         player.networkHandler.sendPacket(new LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
      }

      select(player, slot);
   }

   private boolean inReach(ClientPlayerEntity player, BlockPos pos) {
      double reach = this.distance.getValue();
      return player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
   }

   private int borrow(MinecraftClient client, ClientPlayerEntity player, Item item, int avoidSlot, boolean rail) {
      int source = -1;

      for (int slot = 9; slot < 36; slot++) {
         if (player.getInventory().getStack(slot).isOf(item)) {
            source = slot;
            break;
         }
      }

      if (source == -1) {
         return -1;
      } else {
         int destination = -1;

         for (int slotx = 0; slotx < 9; slotx++) {
            if (slotx != avoidSlot && player.getInventory().getStack(slotx).isEmpty()) {
               destination = slotx;
               break;
            }
         }

         if (destination == -1) {
            destination = avoidSlot == 8 ? 7 : 8;
         }

         client.interactionManager.clickSlot(player.playerScreenHandler.syncId, source, destination, SlotActionType.SWAP, player);
         if (rail) {
            this.railBorrowFrom = source;
            this.railBorrowTo = destination;
         } else {
            this.cartBorrowFrom = source;
            this.cartBorrowTo = destination;
         }

         return destination;
      }
   }

   private void returnBorrowedSlots(MinecraftClient client, ClientPlayerEntity player) {
      if (client.interactionManager != null) {
         int containerId = player.playerScreenHandler.syncId;
         if (this.railBorrowFrom != -1) {
            client.interactionManager.clickSlot(containerId, this.railBorrowFrom, this.railBorrowTo, SlotActionType.SWAP, player);
            this.railBorrowFrom = -1;
         }

         if (this.cartBorrowFrom != -1) {
            client.interactionManager.clickSlot(containerId, this.cartBorrowFrom, this.cartBorrowTo, SlotActionType.SWAP, player);
            this.cartBorrowFrom = -1;
         }
      }
   }

   private static void select(ClientPlayerEntity player, int slot) {
      if (slot >= 0 && slot < 9) {
         player.getInventory().setSelectedSlot(slot);
         if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
         }
      }
   }

   private static int findInHotbar(ClientPlayerEntity player, Item item) {
      for (int slot = 0; slot < 9; slot++) {
         if (player.getInventory().getStack(slot).isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   private void clearPending() {
      this.pendingPos = null;
      this.placingRail = false;
      this.restoreSlot = false;
      this.railSlot = -1;
      this.cartSlot = -1;
      this.trackedArrowId = -1;
      this.railBorrowFrom = -1;
      this.railBorrowTo = -1;
      this.cartBorrowFrom = -1;
      this.cartBorrowTo = -1;
   }
}
