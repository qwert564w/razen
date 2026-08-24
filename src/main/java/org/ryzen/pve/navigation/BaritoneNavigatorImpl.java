package org.ryzen.pve.navigation;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.Settings.Setting;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.selection.ISelection;
import baritone.api.utils.BetterBlockPos;
import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.ryzen.pve.mining.MiningInventory;

@Environment(EnvType.CLIENT)
final class BaritoneNavigatorImpl implements Navigator {
   private static final boolean IMPLEMENTATION_PRESENT = implementationPresent();
   private static final List<Block> CLIMBABLE_BLOCKS = List.of(Blocks.LADDER, Blocks.VINE, Blocks.SCAFFOLDING);
   private static final List<Block> COMMON_TUNNEL_BLOCKS = MiningInventory.commonTunnelBlocks();
   private BaritoneNavigatorImpl.SettingsSnapshot snapshot;
   private BlockPos currentGoal;
   private boolean active;
   private boolean strictBreakWhitelist;
   private ISelection mineBoundsSelection;

   @Override
   public boolean isAvailable() {
      if (!IMPLEMENTATION_PRESENT) {
         return false;
      } else {
         try {
            return BaritoneAPI.getProvider().getPrimaryBaritone() != null;
         } catch (RuntimeException | LinkageError var2) {
            return false;
         }
      }
   }

   private static boolean implementationPresent() {
      try {
         Class.forName("baritone.BaritoneProvider", false, BaritoneNavigatorImpl.class.getClassLoader());
         return true;
      } catch (LinkageError | ClassNotFoundException var1) {
         return false;
      }
   }

   @Override
   public synchronized void begin(NavigationOptions options) {
      Objects.requireNonNull(options, "options");
      Settings settings = BaritoneAPI.getSettings();
      if (!this.active) {
         this.snapshot = BaritoneNavigatorImpl.SettingsSnapshot.capture(settings);
         this.active = true;
      }

      this.strictBreakWhitelist = options.strictBreakWhitelist();
      settings.allowBreak.value = this.strictBreakWhitelist ? false : options.allowBreak();
      settings.allowBreakAnyway.value = this.strictBreakWhitelist ? new ArrayList<>(COMMON_TUNNEL_BLOCKS) : new ArrayList<>(this.snapshot.allowBreakAnyway);
      settings.allowPlace.value = options.allowPlace();
      settings.allowSprint.value = options.allowSprint();
      settings.freeLook.value = !options.rotateView();
      writeOptionalSetting(settings, "blockFreeLook", !options.rotateView());
      settings.mineScanDroppedItems.value = options.scanDroppedItems();
      writeOptionalSetting(settings, "mineSearchRadius", options.mineSearchRadius());
      writeOptionalSetting(settings, "mineAoeLevel", this.snapshot.mineAoeLevel);
      restoreMineBounds(settings, this.snapshot);
      writeOptionalSetting(settings, "allowRightClick", options.allowInteract());
      settings.chunkCaching.value = false;
      settings.renderCachedChunks.value = false;
      ArrayList<Block> protectedBlocks = new ArrayList<>(this.snapshot.blocksToDisallowBreaking);
      if (options.protectClimbables()) {
         for (Block block : CLIMBABLE_BLOCKS) {
            if (!protectedBlocks.contains(block)) {
               protectedBlocks.add(block);
            }
         }
      }

      settings.blocksToDisallowBreaking.value = protectedBlocks;
      if (options.fastMining()) {
         settings.mineGoalUpdateInterval.value = 5;
         writeOptionalSetting(settings, "mineMaxOreLocationsCount", 6);
         settings.mineDropLoiterDurationMSThanksLouca.value = 0L;
         settings.exploreForBlocks.value = false;
      } else {
         settings.mineGoalUpdateInterval.value = this.snapshot.mineGoalUpdateInterval;
         writeOptionalSetting(settings, "mineMaxOreLocationsCount", this.snapshot.mineMaxOreLocationsCount);
         settings.mineDropLoiterDurationMSThanksLouca.value = this.snapshot.mineDropLoiterMillis;
         settings.exploreForBlocks.value = this.snapshot.exploreForBlocks;
      }

      settings.chatControl.value = false;
      settings.chatControlAnyway.value = false;
      settings.renderPath.value = this.snapshot.renderPath;
      settings.renderGoal.value = this.snapshot.renderGoal;
      settings.renderGoalXZBeacon.value = this.snapshot.renderGoalXZBeacon;
      settings.renderSelectionBoxes.value = this.snapshot.renderSelectionBoxes;
   }

   @Override
   public synchronized void pathTo(BlockPos position, int radius) {
      this.requireActive();
      this.currentGoal = Objects.requireNonNull(position, "position").toImmutable();
      baritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(this.currentGoal, Math.max(0, radius)));
   }

   @Override
   public synchronized void mine(int quantity, Block... blocks) {
      this.requireActive();
      if (blocks != null && blocks.length != 0) {
         if (this.strictBreakWhitelist) {
            LinkedHashSet<Block> allowed = new LinkedHashSet<>(COMMON_TUNNEL_BLOCKS);
            allowed.addAll(List.of(blocks));
            allowed.removeAll(CLIMBABLE_BLOCKS);
            Settings settings = BaritoneAPI.getSettings();
            settings.allowBreak.value = false;
            settings.allowBreakAnyway.value = new ArrayList<>(allowed);
         }

         this.currentGoal = null;
         baritone().getMineProcess().mine(Math.max(0, quantity), blocks);
      } else {
         throw new IllegalArgumentException("At least one target block is required");
      }
   }

   @Override
   public synchronized void setMineBounds(BlockPos min, BlockPos max) {
      Settings settings = BaritoneAPI.getSettings();
      this.removeMineBoundsSelection();
      if (min != null && max != null) {
         writeMineBounds(
            settings,
            Math.min(min.getX(), max.getX()),
            Math.min(min.getY(), max.getY()),
            Math.min(min.getZ(), max.getZ()),
            Math.max(min.getX(), max.getX()),
            Math.max(min.getY(), max.getY()),
            Math.max(min.getZ(), max.getZ())
         );
         settings.renderSelection.value = true;
         this.mineBoundsSelection = baritone().getSelectionManager().addSelection(BetterBlockPos.from(min), BetterBlockPos.from(max));
      } else {
         writeMineBounds(settings, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
         if (this.snapshot != null) {
            settings.renderSelection.value = this.snapshot.renderSelection;
            settings.colorSelection.value = this.snapshot.colorSelection;
            settings.colorSelectionPos1.value = this.snapshot.colorSelectionPos1;
            settings.colorSelectionPos2.value = this.snapshot.colorSelectionPos2;
         }
      }
   }

   @Override
   public synchronized void setMineRenderColor(int argb) {
      if (this.active) {
         Color color = new Color(argb, true);
         Settings settings = BaritoneAPI.getSettings();
         settings.colorSelection.value = color;
         settings.colorSelectionPos1.value = color;
         settings.colorSelectionPos2.value = color;
      }
   }

   @Override
   public synchronized void setMineAoeLevel(int level) {
      if (this.active) {
         writeOptionalSetting(BaritoneAPI.getSettings(), "mineAoeLevel", Math.clamp((long)level, 0, 2));
      }
   }

   @Override
   public boolean isPathing() {
      return this.isAvailable() && baritone().getPathingBehavior().isPathing();
   }

   @Override
   public boolean isMining() {
      return this.isAvailable() && baritone().getMineProcess().isActive();
   }

   @Override
   public List<BlockPos> miningTargets() {
      if (!this.isAvailable()) {
         return List.of();
      } else {
         try {
            return baritone().getMineProcess().getClass().getMethod("knownTargets").invoke(baritone().getMineProcess()) instanceof Collection<?> values
               ? values.stream().filter(BlockPos.class::isInstance).map(BlockPos.class::cast).<BlockPos>map(BlockPos::toImmutable).toList()
               : List.of();
         } catch (LinkageError | RuntimeException | ReflectiveOperationException var3) {
            return List.of();
         }
      }
   }

   @Override
   public Optional<Double> estimatedTicksToGoal() {
      return !this.isAvailable() ? Optional.empty() : baritone().getPathingBehavior().estimatedTicksToGoal();
   }

   @Override
   public synchronized Optional<BlockPos> currentGoal() {
      return Optional.ofNullable(this.currentGoal);
   }

   @Override
   public synchronized String diagnostics() {
      if (!this.isAvailable()) {
         return "available=false";
      } else {
         Settings settings = BaritoneAPI.getSettings();
         IBaritone baritone = baritone();
         List<BlockPos> miningTargets = this.miningTargets();
         return String.format(
            Locale.ROOT,
            "available=true session=%s mineActive=%s pathing=%s hasPath=%s calculating=%s goal=%s customGoal=%s allowBreak=%s allowPlace=%s allowRightClick=%s sprint=%s freeLook=%s blockFreeLook=%s radius=%d aoe=%d scanDrops=%s goalUpdate=%d maxTargets=%d dropLoiterMs=%d explore=%s chunkCaching=%s renderCachedChunks=%s renderPath=%s renderGoal=%s renderSelections=%s allowedBreakAnyway=%s protectedBlocks=%s mineBounds=[%d,%d,%d -> %d,%d,%d] knownMineTargets=%d targetSample=%s",
            this.active,
            baritone.getMineProcess().isActive(),
            baritone.getPathingBehavior().isPathing(),
            baritone.getPathingBehavior().hasPath(),
            baritone.getPathingBehavior().getInProgress().isPresent(),
            baritone.getPathingBehavior().getGoal(),
            this.currentGoal,
            settings.allowBreak.value,
            settings.allowPlace.value,
            readOptionalSetting(settings, "allowRightClick", Boolean.class, true),
            settings.allowSprint.value,
            settings.freeLook.value,
            readOptionalSetting(settings, "blockFreeLook", Boolean.class, false),
            readOptionalSetting(settings, "mineSearchRadius", Integer.class, 0),
            readOptionalSetting(settings, "mineAoeLevel", Integer.class, 0),
            settings.mineScanDroppedItems.value,
            settings.mineGoalUpdateInterval.value,
            readOptionalSetting(settings, "mineMaxOreLocationsCount", Integer.class, 6),
            settings.mineDropLoiterDurationMSThanksLouca.value,
            settings.exploreForBlocks.value,
            settings.chunkCaching.value,
            settings.renderCachedChunks.value,
            settings.renderPath.value,
            settings.renderGoal.value,
            settings.renderSelectionBoxes.value,
            settings.allowBreakAnyway.value,
            settings.blocksToDisallowBreaking.value,
            readOptionalSetting(settings, "mineRegionMinX", Integer.class, Integer.MIN_VALUE),
            readOptionalSetting(settings, "mineRegionMinY", Integer.class, Integer.MIN_VALUE),
            readOptionalSetting(settings, "mineRegionMinZ", Integer.class, Integer.MIN_VALUE),
            readOptionalSetting(settings, "mineRegionMaxX", Integer.class, Integer.MAX_VALUE),
            readOptionalSetting(settings, "mineRegionMaxY", Integer.class, Integer.MAX_VALUE),
            readOptionalSetting(settings, "mineRegionMaxZ", Integer.class, Integer.MAX_VALUE),
            miningTargets.size(),
            miningTargets.stream().limit(8L).toList()
         );
      }
   }

   @Override
   public synchronized void cancel() {
      if (this.isAvailable()) {
         baritone().getPathingBehavior().cancelEverything();
      }

      this.currentGoal = null;
   }

   @Override
   public synchronized void end() {
      this.removeMineBoundsSelection();
      this.cancel();
      if (this.snapshot != null) {
         this.snapshot.restore(BaritoneAPI.getSettings());
      }

      this.snapshot = null;
      this.active = false;
      this.strictBreakWhitelist = false;
   }

   private void requireActive() {
      if (!this.active) {
         throw new IllegalStateException("Navigation session has not started");
      } else if (!this.isAvailable()) {
         throw new IllegalStateException("Baritone is unavailable");
      }
   }

   private static IBaritone baritone() {
      return BaritoneAPI.getProvider().getPrimaryBaritone();
   }

   private void removeMineBoundsSelection() {
      if (this.mineBoundsSelection != null) {
         if (this.isAvailable()) {
            baritone().getSelectionManager().removeSelection(this.mineBoundsSelection);
         }

         this.mineBoundsSelection = null;
      }
   }

   private static void restoreMineBounds(Settings settings, BaritoneNavigatorImpl.SettingsSnapshot snapshot) {
      writeMineBounds(
         settings,
         snapshot.mineRegionMinX,
         snapshot.mineRegionMinY,
         snapshot.mineRegionMinZ,
         snapshot.mineRegionMaxX,
         snapshot.mineRegionMaxY,
         snapshot.mineRegionMaxZ
      );
   }

   private static void writeMineBounds(Settings settings, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      writeOptionalSetting(settings, "mineRegionMinX", minX);
      writeOptionalSetting(settings, "mineRegionMinY", minY);
      writeOptionalSetting(settings, "mineRegionMinZ", minZ);
      writeOptionalSetting(settings, "mineRegionMaxX", maxX);
      writeOptionalSetting(settings, "mineRegionMaxY", maxY);
      writeOptionalSetting(settings, "mineRegionMaxZ", maxZ);
   }

   private static <T> T readOptionalSetting(Settings settings, String name, Class<T> type, T fallback) {
      try {
         Field field = Settings.class.getField(name);
         Object value = ((Setting)field.get(settings)).value;
         return type.isInstance(value) ? type.cast(value) : fallback;
      } catch (LinkageError | RuntimeException | ReflectiveOperationException var6) {
         return fallback;
      }
   }

   private static void writeOptionalSetting(Settings settings, String name, Object value) {
      try {
         Field field = Settings.class.getField(name);
         Setting setting = (Setting)field.get(settings);
         setting.value = value;
      } catch (LinkageError | RuntimeException | ReflectiveOperationException var5) {
      }
   }

   @Environment(EnvType.CLIENT)
   private static record SettingsSnapshot(
      boolean allowBreak,
      boolean allowPlace,
      boolean allowSprint,
      boolean freeLook,
      boolean blockFreeLook,
      boolean scanDroppedItems,
      int mineSearchRadius,
      int mineAoeLevel,
      int mineRegionMinX,
      int mineRegionMinY,
      int mineRegionMinZ,
      int mineRegionMaxX,
      int mineRegionMaxY,
      int mineRegionMaxZ,
      boolean allowRightClick,
      int mineGoalUpdateInterval,
      int mineMaxOreLocationsCount,
      long mineDropLoiterMillis,
      boolean exploreForBlocks,
      List<Block> allowBreakAnyway,
      List<Block> blocksToDisallowBreaking,
      boolean chatControl,
      boolean chatControlAnyway,
      boolean renderPath,
      boolean renderGoal,
      boolean renderGoalXZBeacon,
      boolean renderSelectionBoxes,
      boolean renderSelection,
      Color colorSelection,
      Color colorSelectionPos1,
      Color colorSelectionPos2,
      boolean chunkCaching,
      boolean renderCachedChunks
   ) {
      private static BaritoneNavigatorImpl.SettingsSnapshot capture(Settings settings) {
         return new BaritoneNavigatorImpl.SettingsSnapshot(
            (Boolean)settings.allowBreak.value,
            (Boolean)settings.allowPlace.value,
            (Boolean)settings.allowSprint.value,
            (Boolean)settings.freeLook.value,
            BaritoneNavigatorImpl.readOptionalSetting(settings, "blockFreeLook", Boolean.class, false),
            (Boolean)settings.mineScanDroppedItems.value,
            BaritoneNavigatorImpl.readOptionalSetting(settings, "mineSearchRadius", Integer.class, 0),
            BaritoneNavigatorImpl.readOptionalSetting(settings, "mineAoeLevel", Integer.class, 0),
            BaritoneNavigatorImpl.readOptionalSetting(settings, "mineRegionMinX", Integer.class, Integer.MIN_VALUE),
            BaritoneNavigatorImpl.readOptionalSetting(settings, "mineRegionMinY", Integer.class, Integer.MIN_VALUE),
            BaritoneNavigatorImpl.readOptionalSetting(settings, "mineRegionMinZ", Integer.class, Integer.MIN_VALUE),
            BaritoneNavigatorImpl.readOptionalSetting(settings, "mineRegionMaxX", Integer.class, Integer.MAX_VALUE),
            BaritoneNavigatorImpl.readOptionalSetting(settings, "mineRegionMaxY", Integer.class, Integer.MAX_VALUE),
            BaritoneNavigatorImpl.readOptionalSetting(settings, "mineRegionMaxZ", Integer.class, Integer.MAX_VALUE),
            BaritoneNavigatorImpl.readOptionalSetting(settings, "allowRightClick", Boolean.class, true),
            (Integer)settings.mineGoalUpdateInterval.value,
            BaritoneNavigatorImpl.readOptionalSetting(settings, "mineMaxOreLocationsCount", Integer.class, 6),
            (Long)settings.mineDropLoiterDurationMSThanksLouca.value,
            (Boolean)settings.exploreForBlocks.value,
            List.copyOf((Collection<? extends Block>)settings.allowBreakAnyway.value),
            List.copyOf((Collection<? extends Block>)settings.blocksToDisallowBreaking.value),
            (Boolean)settings.chatControl.value,
            (Boolean)settings.chatControlAnyway.value,
            (Boolean)settings.renderPath.value,
            (Boolean)settings.renderGoal.value,
            (Boolean)settings.renderGoalXZBeacon.value,
            (Boolean)settings.renderSelectionBoxes.value,
            (Boolean)settings.renderSelection.value,
            (Color)settings.colorSelection.value,
            (Color)settings.colorSelectionPos1.value,
            (Color)settings.colorSelectionPos2.value,
            (Boolean)settings.chunkCaching.value,
            (Boolean)settings.renderCachedChunks.value
         );
      }

      private void restore(Settings settings) {
         settings.allowBreak.value = this.allowBreak;
         settings.allowPlace.value = this.allowPlace;
         settings.allowSprint.value = this.allowSprint;
         settings.freeLook.value = this.freeLook;
         BaritoneNavigatorImpl.writeOptionalSetting(settings, "blockFreeLook", this.blockFreeLook);
         settings.mineScanDroppedItems.value = this.scanDroppedItems;
         BaritoneNavigatorImpl.writeOptionalSetting(settings, "mineSearchRadius", this.mineSearchRadius);
         BaritoneNavigatorImpl.writeOptionalSetting(settings, "mineAoeLevel", this.mineAoeLevel);
         BaritoneNavigatorImpl.restoreMineBounds(settings, this);
         BaritoneNavigatorImpl.writeOptionalSetting(settings, "allowRightClick", this.allowRightClick);
         settings.mineGoalUpdateInterval.value = this.mineGoalUpdateInterval;
         BaritoneNavigatorImpl.writeOptionalSetting(settings, "mineMaxOreLocationsCount", this.mineMaxOreLocationsCount);
         settings.mineDropLoiterDurationMSThanksLouca.value = this.mineDropLoiterMillis;
         settings.exploreForBlocks.value = this.exploreForBlocks;
         settings.allowBreakAnyway.value = new ArrayList<>(this.allowBreakAnyway);
         settings.blocksToDisallowBreaking.value = new ArrayList<>(this.blocksToDisallowBreaking);
         settings.chatControl.value = this.chatControl;
         settings.chatControlAnyway.value = this.chatControlAnyway;
         settings.renderPath.value = this.renderPath;
         settings.renderGoal.value = this.renderGoal;
         settings.renderGoalXZBeacon.value = this.renderGoalXZBeacon;
         settings.renderSelectionBoxes.value = this.renderSelectionBoxes;
         settings.renderSelection.value = this.renderSelection;
         settings.colorSelection.value = this.colorSelection;
         settings.colorSelectionPos1.value = this.colorSelectionPos1;
         settings.colorSelectionPos2.value = this.colorSelectionPos2;
         settings.chunkCaching.value = this.chunkCaching;
         settings.renderCachedChunks.value = this.renderCachedChunks;
      }
   }
}
