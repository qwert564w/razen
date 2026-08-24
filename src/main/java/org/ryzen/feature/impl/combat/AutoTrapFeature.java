package org.ryzen.feature.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.ryzen.event.events.input.MouseInputEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.InputBindSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.FriendManager;

@Environment(EnvType.CLIENT)
public final class AutoTrapFeature extends Feature {
   private static final String TARGET_SINGLE = "Single";
   private static final String TARGET_MULTI = "Multi";
   private static final String BLOCK_COBWEB = "Cobweb";
   private static final String BLOCK_OBSIDIAN = "Obsidian";
   private static final String BLOCK_BOTH = "Both";
   private static final int HOTBAR_SIZE = 9;
   private static final int INVENTORY_SIZE = 36;
   public final ModeSetting targetMode = this.register(new ModeSetting("Targets", "Single", "Single", "Multi"));
   public final ModeSetting blockMode = this.register(new ModeSetting("Blocks", "Cobweb", "Cobweb", "Obsidian", "Both"));
   public final NumberSetting range = this.register(new NumberSetting("Range", 4.0, 1.0, 8.0, 0.5, " blocks"));
   public final BooleanSetting jumpForUpper = this.register(new BooleanSetting("Jump For Upper", true).visibleWhen(() -> !this.blockMode.is("Cobweb")));
   public final BooleanSetting useInventory = this.register(new BooleanSetting("Use Inventory", false));
   public final InputBindSetting placeKey = this.register(new InputBindSetting("Place Key", -1));
   private final List<BlockPos> placementQueue = new ArrayList<>();
   private final List<BlockPos> placedPositions = new ArrayList<>();
   private int placementIndex;
   private boolean keyHeld;
   private boolean pendingPlacement;
   private boolean pendingCobweb;
   private boolean jumpBeforePlacement;
   private BlockPos pendingPos;
   private Direction pendingFace;
   private int borrowFrom = -1;
   private int borrowTo = -1;

   public AutoTrapFeature() {
      super("AutoTrap", "Seals a nearby player in cobwebs or obsidian while the key is held", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.keyHeld = false;
      this.reset();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.keyHeld = false;
      this.reset();
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (this.placeKey.matches(event.getKey())) {
         this.setKeyHeld(event.getAction() != 0);
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (this.placeKey.matchesMouse(event.getButton())) {
         this.setKeyHeld(event.getAction() != 0);
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      ClientWorld level = client.world;
      if (player != null && level != null && client.interactionManager != null && client.currentScreen == null) {
         if (!this.keyHeld || !this.hasRequiredBlocks(player)) {
            this.reset();
         } else if (this.pendingPlacement && this.pendingPos != null && this.pendingFace != null) {
            this.runPendingPlacement(client, player);
         } else {
            List<PlayerEntity> targets = this.findTargets(player, level);
            if (targets.isEmpty()) {
               this.reset();
            } else {
               if (this.placementQueue.isEmpty() || this.placementIndex >= this.placementQueue.size()) {
                  this.rebuildQueue(level, targets);
               }

               this.scheduleNext(level, player, targets);
            }
         }
      }
   }

   private void rebuildQueue(ClientWorld level, List<PlayerEntity> targets) {
      this.placementQueue.clear();
      this.placedPositions.clear();
      this.placementIndex = 0;

      for (PlayerEntity target : targets) {
         for (BlockPos pos : this.collectTrapPositions(level, target)) {
            if (!this.placementQueue.contains(pos)) {
               this.placementQueue.add(pos);
            }
         }
      }
   }

   private void scheduleNext(ClientWorld level, ClientPlayerEntity player, List<PlayerEntity> targets) {
      while (this.placementIndex < this.placementQueue.size()) {
         BlockPos pos = this.placementQueue.get(this.placementIndex);
         if (!level.getBlockState(pos).isReplaceable()) {
            this.placementIndex++;
         } else {
            boolean insideTarget = intersectsTarget(pos, targets);
            boolean cobweb = insideTarget && !this.blockMode.is("Obsidian") && this.hasCobweb(player);
            Direction face = findSupportFace(level, pos);
            if (face != null) {
               this.jumpBeforePlacement = this.jumpForUpper.getValue() && !cobweb && pos.getY() >= MathHelper.floor(player.getEyeY());
               this.schedulePlacement(player, pos, face, cobweb);
               if (this.jumpBeforePlacement && player.isOnGround()) {
                  player.jump();
               }

               return;
            }

            BlockPos below = pos.down();
            Direction belowFace = level.getBlockState(below).isReplaceable() && !this.placedPositions.contains(below) ? findSupportFace(level, below) : null;
            if (belowFace != null) {
               this.placementQueue.add(this.placementIndex, below);
               this.schedulePlacement(player, below, belowFace, false);
               return;
            }

            this.placementIndex++;
         }
      }

      this.placementQueue.clear();
      this.placementIndex = 0;
   }

   private void schedulePlacement(ClientPlayerEntity player, BlockPos pos, Direction face, boolean cobweb) {
      this.pendingPos = pos;
      this.pendingFace = face;
      this.pendingCobweb = cobweb;
      this.pendingPlacement = true;
      BlockPos support = pos.offset(face);
      Direction opposite = face.getOpposite();
      Vec3d aim = Vec3d.ofCenter(support).add((double)opposite.getOffsetX() * 0.5, (double)opposite.getOffsetY() * 0.5, (double)opposite.getOffsetZ() * 0.5);
      sendLookAt(player, aim);
   }

   private void runPendingPlacement(MinecraftClient client, ClientPlayerEntity player) {
      if (!this.jumpBeforePlacement) {
         this.placeBlock(client, player, this.pendingPos, this.pendingFace, this.pendingCobweb);
         this.finishPending();
      } else {
         if (!player.isOnGround() && player.getVelocity().y > 0.0) {
            this.placeBlock(client, player, this.pendingPos, this.pendingFace, this.pendingCobweb);
            this.finishPending();
         } else if (player.isOnGround()) {
            player.jump();
         }
      }
   }

   private void finishPending() {
      this.placedPositions.add(this.pendingPos);
      this.pendingPos = null;
      this.pendingFace = null;
      this.pendingPlacement = false;
      this.jumpBeforePlacement = false;
      this.placementIndex++;
   }

   private void placeBlock(MinecraftClient client, ClientPlayerEntity player, BlockPos pos, Direction face, boolean cobweb) {
      int originalSlot = player.getInventory().getSelectedSlot();
      int slot = this.selectBlockSlot(client, player, cobweb);
      if (slot != -1) {
         BlockPos support = pos.offset(face);
         Direction hitFace = face.getOpposite();
         Vec3d hitVec = Vec3d.ofCenter(support).add((double)hitFace.getOffsetX() * 0.5, (double)hitFace.getOffsetY() * 0.5, (double)hitFace.getOffsetZ() * 0.5);
         client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(hitVec, hitFace, support, false));
         player.swingHand(Hand.MAIN_HAND);
         select(player, originalSlot);
         this.returnBorrowed(client, player);
      }
   }

   private int selectBlockSlot(MinecraftClient client, ClientPlayerEntity player, boolean cobweb) {
      int slot = cobweb ? findInHotbar(player, AutoTrapFeature::isCobweb) : findInHotbar(player, AutoTrapFeature::isObsidian);
      if (slot == -1 && this.useInventory.getValue()) {
         int source = cobweb ? findInInventory(player, AutoTrapFeature::isCobweb) : findInInventory(player, AutoTrapFeature::isObsidian);
         if (source != -1) {
            int destination = (player.getInventory().getSelectedSlot() + 1) % 9;
            client.interactionManager.clickSlot(player.playerScreenHandler.syncId, source, destination, SlotActionType.SWAP, player);
            this.borrowFrom = source;
            this.borrowTo = destination;
            slot = destination;
         }
      }

      if (slot == -1) {
         return -1;
      } else {
         select(player, slot);
         return slot;
      }
   }

   private void returnBorrowed(MinecraftClient client, ClientPlayerEntity player) {
      if (this.borrowFrom != -1 && client.interactionManager != null) {
         client.interactionManager.clickSlot(player.playerScreenHandler.syncId, this.borrowFrom, this.borrowTo, SlotActionType.SWAP, player);
         this.borrowFrom = -1;
         this.borrowTo = -1;
      }
   }

   private List<BlockPos> collectTrapPositions(ClientWorld level, PlayerEntity target) {
      BlockPos feet = target.getBlockPos();
      BlockPos head = feet.up();
      BlockPos above = head.up();
      List<BlockPos> positions = new ArrayList<>();
      if (!this.blockMode.is("Obsidian")) {
         if (level.getBlockState(feet).isReplaceable()) {
            positions.add(feet);
         }

         if (level.getBlockState(head).isReplaceable()) {
            positions.add(head);
         }
      }

      if (!this.blockMode.is("Cobweb")) {
         for (BlockPos pos : List.of(
            feet.north(), feet.south(), feet.east(), feet.west(), head.north(), head.south(), head.east(), head.west(), above, above.up()
         )) {
            if (level.getBlockState(pos).isReplaceable() && !positions.contains(pos)) {
               positions.add(pos);
            }
         }
      }

      return positions;
   }

   private static Direction findSupportFace(ClientWorld level, BlockPos pos) {
      for (Direction direction : Direction.values()) {
         BlockState state = level.getBlockState(pos.offset(direction));
         if (!state.isAir() && !state.isReplaceable()) {
            return direction;
         }
      }

      return null;
   }

   private static boolean intersectsTarget(BlockPos pos, List<PlayerEntity> targets) {
      Box box = new Box(
         (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)pos.getX() + 1.0, (double)pos.getY() + 1.0, (double)pos.getZ() + 1.0
      );

      for (PlayerEntity target : targets) {
         if (target.getBoundingBox().intersects(box)) {
            return true;
         }
      }

      return false;
   }

   private List<PlayerEntity> findTargets(ClientPlayerEntity player, ClientWorld level) {
      double range = this.range.getValue();
      Box box = player.getBoundingBox().expand(range);
      List<PlayerEntity> targets = new ArrayList<>(
         level.getEntitiesByClass(
            PlayerEntity.class,
            box,
            candidate -> candidate.isAlive() && candidate != player && !FriendManager.INSTANCE.isFriend(candidate.getGameProfile().name())
         )
      );
      targets.sort(Comparator.comparingDouble(player::squaredDistanceTo));
      if (this.targetMode.is("Single")) {
         return targets.isEmpty() ? List.of() : List.of(targets.getFirst());
      } else {
         return targets;
      }
   }

   private boolean hasRequiredBlocks(ClientPlayerEntity player) {
      if (this.blockMode.is("Cobweb")) {
         return this.hasCobweb(player);
      } else {
         return this.blockMode.is("Obsidian") ? this.hasObsidian(player) : this.hasCobweb(player) || this.hasObsidian(player);
      }
   }

   private boolean hasCobweb(ClientPlayerEntity player) {
      return findInHotbar(player, AutoTrapFeature::isCobweb) != -1 || this.useInventory.getValue() && findInInventory(player, AutoTrapFeature::isCobweb) != -1;
   }

   private boolean hasObsidian(ClientPlayerEntity player) {
      return findInHotbar(player, AutoTrapFeature::isObsidian) != -1
         || this.useInventory.getValue() && findInInventory(player, AutoTrapFeature::isObsidian) != -1;
   }

   private static int findInHotbar(ClientPlayerEntity player, Predicate<Item> match) {
      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (!stack.isEmpty() && match.test(stack.getItem())) {
            return slot;
         }
      }

      return -1;
   }

   private static int findInInventory(ClientPlayerEntity player, Predicate<Item> match) {
      for (int slot = 9; slot < 36; slot++) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (!stack.isEmpty() && match.test(stack.getItem())) {
            return slot;
         }
      }

      return -1;
   }

   private static boolean isCobweb(Item item) {
      return item == Items.COBWEB;
   }

   private static boolean isObsidian(Item item) {
      return item == Items.OBSIDIAN || item == Items.CRYING_OBSIDIAN;
   }

   private static void select(ClientPlayerEntity player, int slot) {
      if (slot >= 0 && slot < 9 && player.getInventory().getSelectedSlot() != slot) {
         player.getInventory().setSelectedSlot(slot);
         if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
         }
      }
   }

   private static void sendLookAt(ClientPlayerEntity player, Vec3d aim) {
      if (player.networkHandler != null) {
         Vec3d delta = aim.subtract(player.getEyePos());
         float yaw = MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
         float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
         player.networkHandler.sendPacket(new LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
      }
   }

   private void setKeyHeld(boolean held) {
      this.keyHeld = held;
      if (!held) {
         this.reset();
      }
   }

   private void reset() {
      this.pendingPlacement = false;
      this.jumpBeforePlacement = false;
      this.pendingPos = null;
      this.pendingFace = null;
      this.placementQueue.clear();
      this.placedPositions.clear();
      this.placementIndex = 0;
   }
}
