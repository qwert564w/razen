package org.ryzen.feature.impl.pve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
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
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.feature.setting.TextSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.pve.mining.MiningInventory;
import org.ryzen.pve.mining.MiningParsers;
import org.ryzen.pve.mining.MiningSessionSnapshot;
import org.ryzen.pve.mining.MiningTargetSelector;

@Environment(EnvType.CLIENT)
public final class NukerFeature extends PveFeature {
   private static final double MAX_REACH_SQUARED = 25.0;
   private static final int INSTANT_LIMIT = 8;
   public final NumberSetting radiusXz = this.register(new NumberSetting("Radius XZ", 3.0, 1.0, 6.0, 1.0, " blocks"));
   public final NumberSetting radiusY = this.register(new NumberSetting("Radius Y", 3.0, 1.0, 6.0, 1.0, " blocks"));
   public final ModeSetting workMode = this.register(new ModeSetting("Work Mode", "Everywhere", "Everywhere", "Only Mine"));
   public final TextSetting mineRegion = this.register(new TextSetting("Mine Region", "", 96));
   public final ModeSetting diggingMode = this.register(new ModeSetting("Digging Mode", "Everyone", "Everyone", "Ore Priority", "Only Ore"));
   public final NumberSetting yawSpeed = this.register(
      new NumberSetting("Yaw Speed", 180.0, 1.0, 180.0, 1.0, " deg/tick").visibleWhen(PveManagerFeature.INSTANCE.rotate::getValue)
   );
   public final NumberSetting pitchSpeed = this.register(
      new NumberSetting("Pitch Speed", 180.0, 1.0, 180.0, 1.0, " deg/tick").visibleWhen(PveManagerFeature.INSTANCE.rotate::getValue)
   );
   public final BooleanSetting throughWalls = this.register(new BooleanSetting("Through Walls", false));
   public final BooleanSetting mineNeighbor = this.register(new BooleanSetting("Mine Neighbor", false));
   public final BooleanSetting mineDown = this.register(new BooleanSetting("Mine Down", false));
   public final BooleanSetting instant = this.register(new BooleanSetting("Instant", false));
   private final MiningSessionSnapshot snapshot = new MiningSessionSnapshot();
   private BlockPos currentTarget;

   public NukerFeature() {
      super("Nuker", "Breaks nearby blocks using safe target filtering", -1, AutomationPriority.FEATURE, AutomationResource.ROTATION);
   }

   @Override
   protected void onPveEnable() {
      this.currentTarget = null;
      this.snapshot.capture(MinecraftClient.getInstance().player);
   }

   @Override
   protected void onPveDisable() {
      this.cleanup();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cleanup();
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPre()) {
         MinecraftClient client = MinecraftClient.getInstance();
         ClientPlayerEntity player = event.getPlayer();
         ClientWorld level = client.world;
         if (player != null && level != null && client.interactionManager != null && player.isAlive() && !player.isSpectator() && client.currentScreen == null) {
            this.snapshot.capture(player);
            List<MiningTargetSelector.Candidate<BlockPos>> candidates = this.collectCandidates(level, player);
            Optional<MiningTargetSelector.Candidate<BlockPos>> selected = MiningTargetSelector.select(
               candidates, this.resolvedDiggingMode(), this.throughWalls.getValue()
            );
            if (selected.isEmpty()) {
               this.cancelBreaking(client);
            } else {
               MiningTargetSelector.Candidate<BlockPos> target = selected.get();
               if (this.mineNeighbor.getValue()) {
                  List<MiningTargetSelector.Candidate<BlockPos>> neighbors = this.collectNeighbors(level, player, target.value());
                  target = MiningTargetSelector.easierNeighbor(target, neighbors, this.throughWalls.getValue()).orElse(target);
               }

               if (this.instant.getValue() && this.instantBreak(client, level, player, candidates)) {
                  this.currentTarget = target.value();
               } else {
                  this.breakTarget(client, level, player, target.value());
               }
            }
         } else {
            this.cancelBreaking(client);
         }
      }
   }

   public BlockPos getCurrentTarget() {
      return this.currentTarget;
   }

   private List<MiningTargetSelector.Candidate<BlockPos>> collectCandidates(ClientWorld level, ClientPlayerEntity player) {
      int horizontal = this.radiusXz.getValue().intValue();
      int vertical = this.radiusY.getValue().intValue();
      BlockPos origin = player.getBlockPos();
      int minimumY = this.mineDown.getValue() ? origin.getY() - vertical : origin.getY();
      List<MiningTargetSelector.Candidate<BlockPos>> candidates = new ArrayList<>();

      for (int x = origin.getX() - horizontal; x <= origin.getX() + horizontal; x++) {
         for (int y = minimumY; y <= origin.getY() + vertical; y++) {
            for (int z = origin.getZ() - horizontal; z <= origin.getZ() + horizontal; z++) {
               BlockPos position = new BlockPos(x, y, z);
               this.candidate(level, player, origin, position).ifPresent(candidates::add);
            }
         }
      }

      return candidates;
   }

   private List<MiningTargetSelector.Candidate<BlockPos>> collectNeighbors(ClientWorld level, ClientPlayerEntity player, BlockPos position) {
      List<MiningTargetSelector.Candidate<BlockPos>> neighbors = new ArrayList<>();
      BlockPos origin = player.getBlockPos();

      for (Direction direction : Direction.values()) {
         this.candidate(level, player, origin, position.offset(direction))
            .filter(candidate -> !this.diggingMode.is("Only Ore") || candidate.ore())
            .ifPresent(neighbors::add);
      }

      return neighbors;
   }

   private Optional<MiningTargetSelector.Candidate<BlockPos>> candidate(ClientWorld level, ClientPlayerEntity player, BlockPos origin, BlockPos position) {
      if (!this.mineDown.getValue() && position.getY() < origin.getY()) {
         return Optional.empty();
      } else {
         BlockState state = level.getBlockState(position);
         boolean inWorkArea = this.isInWorkArea(position);
         boolean safe = this.isSafe(level, position, state) && distanceToBlockSquared(player.getEyePos(), position) <= 25.0;
         if (!safe) {
            return Optional.empty();
         } else {
            boolean visible = this.isVisible(level, player, position);
            return Optional.of(
               new MiningTargetSelector.Candidate<>(
                  position.toImmutable(),
                  true,
                  MiningInventory.isOre(state.getBlock()),
                  visible,
                  inWorkArea,
                  position.getY() - origin.getY(),
                  player.squaredDistanceTo(Vec3d.ofCenter(position)),
                  state.getHardness(level, position)
               )
            );
         }
      }
   }

   private boolean isSafe(ClientWorld level, BlockPos position, BlockState state) {
      return !state.isAir()
         && state.getFluidState().isEmpty()
         && !state.hasBlockEntity()
         && MiningInventory.isSafeBreakTarget(state.getBlock())
         && state.getHardness(level, position) >= 0.0F
         && !state.getCollisionShape(level, position).isEmpty();
   }

   private boolean isInWorkArea(BlockPos position) {
      return !this.workMode.is("Only Mine") ? true : MiningParsers.region(this.mineRegion.getValue()).map(region -> region.contains(position)).orElse(false);
   }

   private boolean isVisible(ClientWorld level, ClientPlayerEntity player, BlockPos position) {
      BlockHitResult hit = level.raycast(new RaycastContext(player.getEyePos(), Vec3d.ofCenter(position), ShapeType.OUTLINE, FluidHandling.NONE, player));
      return hit.getType() == Type.MISS || hit.getBlockPos().equals(position);
   }

   private void breakTarget(MinecraftClient client, ClientWorld level, ClientPlayerEntity player, BlockPos target) {
      if (PveManagerFeature.INSTANCE.rotate.getValue()) {
         this.rotateToward(player, target);
      }

      if (!target.equals(this.currentTarget)) {
         this.cancelBreaking(client);
         this.currentTarget = target.toImmutable();
         client.interactionManager.attackBlock(target, this.hitDirection(level, player, target));
      }

      client.interactionManager.updateBlockBreakingProgress(target, this.hitDirection(level, player, target));
      player.swingHand(Hand.MAIN_HAND);
   }

   private boolean instantBreak(MinecraftClient client, ClientWorld level, ClientPlayerEntity player, List<MiningTargetSelector.Candidate<BlockPos>> candidates) {
      List<MiningTargetSelector.Candidate<BlockPos>> instantTargets = candidates.stream()
         .filter(MiningTargetSelector.Candidate::safe)
         .filter(MiningTargetSelector.Candidate::inWorkArea)
         .filter(candidatex -> this.throughWalls.getValue() || candidatex.visible())
         .filter(candidatex -> !this.diggingMode.is("Only Ore") || candidatex.ore())
         .filter(
            candidatex -> player.isCreative()
                  || level.getBlockState((BlockPos)candidatex.value()).calcBlockBreakingDelta(player, level, (BlockPos)candidatex.value()) >= 1.0F
         )
         .sorted(
            Comparator.<MiningTargetSelector.Candidate<BlockPos>>comparingInt(candidatex -> this.diggingMode.is("Ore Priority") && candidatex.ore() ? 0 : 1)
               .thenComparingDouble(MiningTargetSelector.Candidate::distanceSquared)
         )
         .limit(8L)
         .toList();

      for (MiningTargetSelector.Candidate<BlockPos> candidate : instantTargets) {
         client.interactionManager.attackBlock(candidate.value(), Direction.UP);
         player.swingHand(Hand.MAIN_HAND);
      }

      return !instantTargets.isEmpty();
   }

   private Direction hitDirection(ClientWorld level, ClientPlayerEntity player, BlockPos position) {
      BlockHitResult hit = level.raycast(new RaycastContext(player.getEyePos(), Vec3d.ofCenter(position), ShapeType.OUTLINE, FluidHandling.NONE, player));
      return hit.getType() == Type.BLOCK && hit.getBlockPos().equals(position) ? hit.getSide() : Direction.UP;
   }

   private void rotateToward(ClientPlayerEntity player, BlockPos position) {
      Vec3d difference = Vec3d.ofCenter(position).subtract(player.getEyePos());
      double horizontal = Math.sqrt(difference.x * difference.x + difference.z * difference.z);
      float targetYaw = (float)Math.toDegrees(Math.atan2(difference.z, difference.x)) - 90.0F;
      float targetPitch = (float)(-Math.toDegrees(Math.atan2(difference.y, horizontal)));
      float yawDelta = MathHelper.wrapDegrees(targetYaw - player.getYaw());
      float pitchDelta = MathHelper.wrapDegrees(targetPitch - player.getPitch());
      player.setYaw(player.getYaw() + MathHelper.clamp(yawDelta, -this.yawSpeed.getValue().floatValue(), this.yawSpeed.getValue().floatValue()));
      player.setPitch(player.getPitch() + MathHelper.clamp(pitchDelta, -this.pitchSpeed.getValue().floatValue(), this.pitchSpeed.getValue().floatValue()));
   }

   private MiningTargetSelector.DiggingMode resolvedDiggingMode() {
      if (this.diggingMode.is("Only Ore")) {
         return MiningTargetSelector.DiggingMode.ONLY_ORE;
      } else {
         return this.diggingMode.is("Ore Priority") ? MiningTargetSelector.DiggingMode.ORE_PRIORITY : MiningTargetSelector.DiggingMode.EVERYONE;
      }
   }

   private static double distanceToBlockSquared(Vec3d point, BlockPos position) {
      double x = point.x - MathHelper.clamp(point.x, (double)position.getX(), (double)position.getX() + 1.0);
      double y = point.y - MathHelper.clamp(point.y, (double)position.getY(), (double)position.getY() + 1.0);
      double z = point.z - MathHelper.clamp(point.z, (double)position.getZ(), (double)position.getZ() + 1.0);
      return x * x + y * y + z * z;
   }

   private void cancelBreaking(MinecraftClient client) {
      if (client != null && client.interactionManager != null) {
         client.interactionManager.cancelBlockBreaking();
      }

      this.currentTarget = null;
   }

   private void cleanup() {
      MinecraftClient client = MinecraftClient.getInstance();
      this.cancelBreaking(client);
      this.snapshot.restore(client);
   }
}
