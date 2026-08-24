package org.ryzen.feature.impl.pve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveFeature;

@Environment(EnvType.CLIENT)
public final class AutoFarmFeature extends PveFeature {
   private static final String ROTATE_NONE = "None";
   private static final String ROTATE_CLIENT = "Client";
   private static final String ROTATE_PACKET = "Packet";
   private static final String CROP_PUMPKIN = "Pumpkins";
   private static final String CROP_MELON = "Melons";
   private static final String CROP_CARROT = "Carrots";
   private static final String CROP_POTATO = "Potatoes";
   private static final String CROP_BEETROOT = "Beetroots";
   private static final String CROP_WHEAT = "Wheat";
   private static final String CROP_SAPLINGS = "Saplings";
   private static final String CROP_BAMBOO = "Bamboo";
   private static final String CROP_COCOA = "Cocoa";
   private static final String CROP_SUGAR_CANE = "Sugar Cane";
   private static final String CROP_CACTUS = "Cactus";
   private static final String CROP_BERRIES = "Sweet Berries";
   private static final String CROP_NETHER_WART = "Nether Wart";
   private static final int STUCK_TICKS = 5;
   private static final double STUCK_EPSILON = 0.01;
   private static final double ARRIVE_DISTANCE = 2.0;
   public final NumberSetting range = this.register(new NumberSetting("Range", 4.5, 1.0, 6.0, 0.1, " blocks"));
   public final NumberSetting delay = this.register(new NumberSetting("Delay", 50.0, 0.0, 500.0, 10.0, " ms"));
   public final NumberSetting blocksPerTick = this.register(new NumberSetting("Blocks Per Tick", 10.0, 1.0, 32.0, 1.0, ""));
   public final BooleanSetting autoWalk = this.register(new BooleanSetting("Auto Walk", true));
   public final NumberSetting walkRange = this.register(new NumberSetting("Walk Range", 15.0, 5.0, 50.0, 1.0, " blocks").visibleWhen(this.autoWalk::getValue));
   public final BooleanSetting pauseWhileHarvesting = this.register(new BooleanSetting("Pause While Harvesting", true).visibleWhen(this.autoWalk::getValue));
   public final ModeSetting rotate = this.register(new ModeSetting("Rotate", "None", "None", "Client", "Packet"));
   public final MultiSelectSetting crops = this.register(
      new MultiSelectSetting(
         "Crops",
         List.of(
            "Pumpkins",
            "Melons",
            "Carrots",
            "Potatoes",
            "Beetroots",
            "Wheat",
            "Saplings",
            "Bamboo",
            "Cocoa",
            "Sugar Cane",
            "Cactus",
            "Sweet Berries",
            "Nether Wart"
         ),
         "Pumpkins",
         "Melons",
         "Carrots",
         "Potatoes",
         "Beetroots",
         "Wheat",
         "Saplings",
         "Bamboo",
         "Cocoa",
         "Sugar Cane",
         "Cactus",
         "Sweet Berries",
         "Nether Wart"
      )
   );
   private long nextHarvestAt;
   private Vec3d lastPosition;
   private int stuckTicks;

   public AutoFarmFeature() {
      super(
         "AutoFarm",
         "Harvests ripe crops around you and walks between patches",
         -1,
         AutomationPriority.FEATURE,
         AutomationResource.MOVEMENT,
         AutomationResource.ROTATION
      );
   }

   @Override
   protected void onPveEnable() {
      this.nextHarvestAt = 0L;
      this.lastPosition = null;
      this.stuckTicks = 0;
   }

   @Override
   protected void onPveDisable() {
      this.releaseMovement();
      this.lastPosition = null;
      this.stuckTicks = 0;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      ClientWorld level = client.world;
      if (player != null && level != null) {
         List<BlockPos> inReach = this.findCrops(player, level, this.range.getValue());
         List<BlockPos> walkTargets = this.autoWalk.getValue() ? this.findCrops(player, level, this.walkRange.getValue()) : List.of();
         boolean harvestReady = !inReach.isEmpty() && System.currentTimeMillis() >= this.nextHarvestAt;
         boolean holdStill = this.pauseWhileHarvesting.getValue() && harvestReady;
         if (this.autoWalk.getValue() && !walkTargets.isEmpty() && !holdStill) {
            this.walkToward(client, player, level, walkTargets.getFirst());
         } else if (this.autoWalk.getValue()) {
            this.releaseMovement();
         }

         if (harvestReady) {
            if (holdStill) {
               this.releaseMovement();
            }

            this.harvest(client, player, inReach);
         }
      }
   }

   private void harvest(MinecraftClient client, ClientPlayerEntity player, List<BlockPos> targets) {
      double rangeSqr = this.range.getValue() * this.range.getValue();
      int limit = this.blocksPerTick.getValue().intValue();
      int broken = 0;

      for (BlockPos pos : targets) {
         if (broken >= limit) {
            break;
         }

         if (!(player.squaredDistanceTo(Vec3d.ofCenter(pos)) > rangeSqr)) {
            this.aimAt(client, player, pos);
            if (player.networkHandler != null) {
               player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP));
               player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
            }

            broken++;
         }
      }

      if (broken > 0) {
         this.nextHarvestAt = System.currentTimeMillis() + this.delay.getValue().longValue();
      }
   }

   private void aimAt(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
      if (!this.rotate.is("None")) {
         Vec3d delta = Vec3d.ofCenter(pos).subtract(player.getEyePos());
         float yaw = MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
         float pitch = MathHelper.clamp((float)(-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))), -90.0F, 90.0F);
         if (this.rotate.is("Client")) {
            player.setYaw(yaw);
            player.setPitch(pitch);
            player.headYaw = yaw;
         } else {
            if (player.networkHandler != null) {
               player.networkHandler.sendPacket(new LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
            }
         }
      }
   }

   private void walkToward(MinecraftClient client, ClientPlayerEntity player, ClientWorld level, BlockPos target) {
      double deltaX = (double)target.getX() + 0.5 - player.getX();
      double deltaZ = (double)target.getZ() + 0.5 - player.getZ();
      double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      if (horizontal < 2.0) {
         this.releaseMovement();
         this.stuckTicks = 0;
         this.lastPosition = null;
      } else {
         float yaw = (float)(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
         if (this.lastPosition != null) {
            boolean stalled = player.getEntityPos().squaredDistanceTo(this.lastPosition) < 0.01 && player.isOnGround();
            this.stuckTicks = stalled ? this.stuckTicks + 1 : 0;
         }

         this.lastPosition = player.getEntityPos();
         if (this.stuckTicks > 5) {
            yaw += this.stuckTicks % 20 < 10 ? 45.0F : -45.0F;
         }

         player.setYaw(yaw);
         client.options.forwardKey.setPressed(true);
         BlockPos ahead = player.getBlockPos();
         Vec3d look = player.getRotationVec(1.0F);
         BlockPos step = ahead.add((int)Math.round(look.x), 0, (int)Math.round(look.z));
         BlockPos stepPrecise = BlockPos.ofFloored(player.getX() + look.x, player.getY(), player.getZ() + look.z);
         boolean blocked = !level.getBlockState(step).isAir() || !level.getBlockState(stepPrecise).isAir();
         boolean headroom = level.getBlockState(step.up()).isAir() && level.getBlockState(step.up(2)).isAir();
         boolean shouldJump = player.isOnGround() && (player.horizontalCollision || blocked && headroom);
         client.options.jumpKey.setPressed(shouldJump);
      }
   }

   private void releaseMovement() {
      MinecraftClient client = MinecraftClient.getInstance();
      client.options.forwardKey.setPressed(false);
      client.options.jumpKey.setPressed(false);
   }

   private List<BlockPos> findCrops(ClientPlayerEntity player, ClientWorld level, double radius) {
      List<BlockPos> found = new ArrayList<>();
      BlockPos origin = player.getBlockPos();
      int limit = (int)Math.ceil(radius);

      for (int x = -limit; x <= limit; x++) {
         for (int y = -limit; y <= limit; y++) {
            for (int z = -limit; z <= limit; z++) {
               BlockPos pos = origin.add(x, y, z);
               if (this.isHarvestable(level, pos)) {
                  found.add(pos);
               }
            }
         }
      }

      found.sort(Comparator.comparingDouble(posx -> player.squaredDistanceTo(Vec3d.ofCenter(posx))));
      return found;
   }

   private boolean isHarvestable(ClientWorld level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      Block block = state.getBlock();
      if (block == Blocks.PUMPKIN) {
         return this.crops.isSelected("Pumpkins");
      } else if (block == Blocks.MELON) {
         return this.crops.isSelected("Melons");
      } else if (block == Blocks.CARROTS) {
         return this.crops.isSelected("Carrots") && (Integer)state.get(Properties.AGE_7) >= 7;
      } else if (block == Blocks.POTATOES) {
         return this.crops.isSelected("Potatoes") && (Integer)state.get(Properties.AGE_7) >= 7;
      } else if (block == Blocks.BEETROOTS) {
         return this.crops.isSelected("Beetroots") && (Integer)state.get(Properties.AGE_3) >= 3;
      } else if (block == Blocks.WHEAT) {
         return this.crops.isSelected("Wheat") && (Integer)state.get(Properties.AGE_7) >= 7;
      } else if (isSapling(block)) {
         return this.crops.isSelected("Saplings");
      } else if (block == Blocks.BAMBOO || block == Blocks.BAMBOO_SAPLING) {
         return this.crops.isSelected("Bamboo");
      } else if (block == Blocks.COCOA) {
         return this.crops.isSelected("Cocoa") && (Integer)state.get(Properties.AGE_2) >= 2;
      } else if (block == Blocks.SUGAR_CANE) {
         return this.crops.isSelected("Sugar Cane") && level.getBlockState(pos.down()).isOf(Blocks.SUGAR_CANE);
      } else if (block == Blocks.CACTUS) {
         return this.crops.isSelected("Cactus") && level.getBlockState(pos.down()).isOf(Blocks.CACTUS);
      } else if (block == Blocks.SWEET_BERRY_BUSH) {
         return this.crops.isSelected("Sweet Berries") && (Integer)state.get(Properties.AGE_3) >= 2;
      } else {
         return block != Blocks.NETHER_WART ? false : this.crops.isSelected("Nether Wart") && (Integer)state.get(Properties.AGE_3) >= 3;
      }
   }

   private static boolean isSapling(Block block) {
      return block == Blocks.OAK_SAPLING
         || block == Blocks.BIRCH_SAPLING
         || block == Blocks.SPRUCE_SAPLING
         || block == Blocks.JUNGLE_SAPLING
         || block == Blocks.ACACIA_SAPLING
         || block == Blocks.DARK_OAK_SAPLING
         || block == Blocks.PALE_OAK_SAPLING
         || block == Blocks.CHERRY_SAPLING
         || block == Blocks.MANGROVE_PROPAGULE;
   }
}
