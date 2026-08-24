package org.ryzen.feature.impl.player;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.util.hit.BlockHitResult;
import org.ryzen.context.MinecraftContext;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.MultiSelectSetting;

@Environment(EnvType.CLIENT)
public final class NoInteractFeature extends Feature implements MinecraftContext {
   private static final String DOORS = "Doors";
   private static final String BUTTONS = "Buttons";
   private static final String CHESTS = "Chests";
   private static final String HOPPERS = "Hoppers";
   private static final String DISPENSERS = "Dispensers";
   private static final String NOTE_BLOCKS = "Note Blocks";
   private static final String CRAFTING_TABLES = "Crafting Tables";
   private static final String TRAPDOORS = "Trapdoors";
   private static final String FURNACES = "Furnaces";
   private static final String FENCE_GATES = "Fence Gates";
   private static final String ANVILS = "Anvils";
   private static final String LEVERS = "Levers";
   public final BooleanSetting allBlocks = this.register(new BooleanSetting("All Blocks", false));
   public final MultiSelectSetting blocks = this.register(
      new MultiSelectSetting(
            "Blocks",
            List.of(
               "Doors",
               "Buttons",
               "Chests",
               "Hoppers",
               "Dispensers",
               "Note Blocks",
               "Crafting Tables",
               "Trapdoors",
               "Furnaces",
               "Fence Gates",
               "Anvils",
               "Levers"
            ),
            "Doors",
            "Buttons",
            "Chests",
            "Hoppers",
            "Dispensers",
            "Note Blocks",
            "Crafting Tables",
            "Trapdoors",
            "Furnaces",
            "Fence Gates",
            "Anvils",
            "Levers"
         )
         .visibleWhen(() -> !this.allBlocks.getValue())
   );

   public NoInteractFeature() {
      super("NoInteract", "Blocks right-click use on chosen blocks", FeatureCategory.PLAYER, -1);
   }

   public static boolean shouldCancel(BlockHitResult hit) {
      NoInteractFeature feature = FeatureManager.INSTANCE.getEnabled(NoInteractFeature.class);
      if (feature == null || hit == null || mc.world == null || mc.player == null) {
         return false;
      } else if (mc.player.isSneaking()) {
         return false;
      } else {
         BlockState state = mc.world.getBlockState(hit.getBlockPos());
         return feature.allBlocks.getValue() || feature.isSelected(state);
      }
   }

   private boolean isSelected(BlockState state) {
      return this.matches(state, DoorBlock.class, "Doors")
         || this.matches(state, ButtonBlock.class, "Buttons")
         || this.matches(state, ChestBlock.class, "Chests")
         || this.matches(state, HopperBlock.class, "Hoppers")
         || this.matches(state, DispenserBlock.class, "Dispensers")
         || this.matches(state, NoteBlock.class, "Note Blocks")
         || this.matches(state, CraftingTableBlock.class, "Crafting Tables")
         || this.matches(state, TrapdoorBlock.class, "Trapdoors")
         || this.matches(state, FurnaceBlock.class, "Furnaces")
         || this.matches(state, FenceGateBlock.class, "Fence Gates")
         || this.matches(state, AnvilBlock.class, "Anvils")
         || this.matches(state, LeverBlock.class, "Levers");
   }

   private boolean matches(BlockState state, Class<?> type, String option) {
      return type.isInstance(state.getBlock()) && this.blocks.isSelected(option);
   }
}
