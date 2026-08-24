package org.ryzen.feature.impl.movement;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class ScaffoldFeature extends Feature {
   private static final Direction[] SUPPORT_DIRECTIONS = new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private static final Set<Block> BLOCK_BLACKLIST = Set.of(
      Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.ANVIL, Blocks.TNT, Blocks.SAND, Blocks.GRAVEL
   );
   public final BooleanSetting avoidFall = this.register(new BooleanSetting("Avoid Falling", false));
   public final BooleanSetting keepY = this.register(new BooleanSetting("Keep Y", false));
   public final BooleanSetting swing = this.register(new BooleanSetting("Swing", true));
   public final NumberSetting placeDelay = this.register(new NumberSetting("Place Delay", 1.0, 0.0, 5.0, 1.0, " ticks"));
   private int baseY = Integer.MIN_VALUE;
   private int delay;

   public ScaffoldFeature() {
      super("Scaffold", "Automatically places safe blocks beneath you", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onEnable() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      this.baseY = player == null ? Integer.MIN_VALUE : player.getBlockPos().getY() - 1;
      this.delay = 0;
   }

   @Override
   protected void onDisable() {
      this.baseY = Integer.MIN_VALUE;
      this.delay = 0;
   }

   @EventTarget
   public void onInput(PlayerInputEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (this.avoidFall.getValue() && player != null && wouldStepIntoVoid(player)) {
         event.setShift(true);
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && client.interactionManager != null && player.networkHandler != null) {
         if (this.delay > 0) {
            this.delay--;
         } else {
            int blockSlot = findBlockSlot(player);
            if (blockSlot >= 0) {
               int y = this.keepY.getValue() && this.baseY != Integer.MIN_VALUE ? this.baseY : (int)Math.floor(player.getY() - 0.25) - 1;
               Vec3d velocity = player.getVelocity();
               double lead = player.isOnGround() ? 0.35 : 0.18;
               BlockPos target = BlockPos.ofFloored(player.getX() + velocity.x * lead, (double)y, player.getZ() + velocity.z * lead);
               if (client.world.getBlockState(target).isReplaceable()) {
                  ScaffoldFeature.Placement placement = findSupport(client, target);
                  if (placement != null) {
                     int previous = player.getInventory().getSelectedSlot();
                     if (blockSlot != previous) {
                        player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(blockSlot));
                        player.getInventory().setSelectedSlot(blockSlot);
                     }

                     client.interactionManager
                        .interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(placement.hit(), placement.face(), placement.support(), false));
                     if (this.swing.getValue()) {
                        player.swingHand(Hand.MAIN_HAND);
                     }

                     if (blockSlot != previous) {
                        player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previous));
                        player.getInventory().setSelectedSlot(previous);
                     }

                     this.delay = this.placeDelay.getValue().intValue();
                  }
               }
            }
         }
      }
   }

   private static ScaffoldFeature.Placement findSupport(MinecraftClient client, BlockPos target) {
      for (Direction direction : SUPPORT_DIRECTIONS) {
         BlockPos support = target.offset(direction);
         BlockState state = client.world.getBlockState(support);
         if (!state.isAir() && !state.isReplaceable() && !state.getCollisionShape(client.world, support).isEmpty()) {
            Direction face = direction.getOpposite();
            Vec3d hit = Vec3d.ofCenter(support).add((double)face.getOffsetX() * 0.5, (double)face.getOffsetY() * 0.5, (double)face.getOffsetZ() * 0.5);
            return new ScaffoldFeature.Placement(support, face, hit);
         }
      }

      return null;
   }

   private static int findBlockSlot(ClientPlayerEntity player) {
      int best = -1;
      int count = -1;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = player.getInventory().getStack(slot);
         Item var6 = stack.getItem();
         if (var6 instanceof BlockItem) {
            BlockItem blockItem = (BlockItem)var6;
            if (!BLOCK_BLACKLIST.contains(blockItem.getBlock()) && stack.getCount() > count) {
               best = slot;
               count = stack.getCount();
            }
         }
      }

      return best;
   }

   private static boolean wouldStepIntoVoid(ClientPlayerEntity player) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world == null) {
         return false;
      } else {
         Vec3d velocity = player.getVelocity();
         BlockPos next = BlockPos.ofFloored(player.getX() + velocity.x * 1.6, player.getY() - 1.0, player.getZ() + velocity.z * 1.6);
         return client.world.getBlockState(next).isReplaceable() && client.world.getBlockState(next.down()).isReplaceable();
      }
   }

   @Environment(EnvType.CLIENT)
   private static record Placement(BlockPos support, Direction face, Vec3d hit) {
   }
}
