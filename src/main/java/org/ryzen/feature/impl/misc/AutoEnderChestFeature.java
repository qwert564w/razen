package org.ryzen.feature.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.input.KeyboardInputEvent;
import org.ryzen.event.events.input.MouseInputEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.InputBindSetting;

@Environment(EnvType.CLIENT)
public final class AutoEnderChestFeature extends Feature implements MinecraftContext {
   private static final int SEARCH_RADIUS = 6;
   private static final double MAX_DISTANCE_SQ = 36.0;
   public final InputBindSetting openKey = this.register(new InputBindSetting("Open Key", -1));
   private BlockPos target;

   public AutoEnderChestFeature() {
      super("AutoEnderChest", "Opens the nearest ender chest on a key press", FeatureCategory.MISC, -1);
   }

   @Override
   protected void onDisable() {
      this.target = null;
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 1 && this.openKey.matches(event.getKey())) {
         this.locate();
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 1 && this.openKey.matchesMouse(event.getButton())) {
         this.locate();
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      BlockPos chest = this.target;
      this.target = null;
      if (chest != null) {
         MinecraftClient client = event.getClient();
         ClientPlayerEntity player = client.player;
         if (player != null && client.world != null && client.interactionManager != null) {
            if (client.world.getBlockState(chest).isOf(Blocks.ENDER_CHEST)) {
               Vec3d center = Vec3d.ofCenter(chest);
               RotationContext.rotateToPosition(player, center);
               client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(center, Direction.UP, chest, false));
            }
         }
      }
   }

   private void locate() {
      MinecraftClient client = MinecraftClient.getInstance();
      ClientPlayerEntity player = client.player;
      if (client.world != null && player != null && client.currentScreen == null) {
         BlockPos origin = player.getBlockPos();
         BlockPos best = null;
         double bestDistance = 36.0;

         for (BlockPos pos : BlockPos.iterate(origin.add(-6, -6, -6), origin.add(6, 6, 6))) {
            if (client.world.getBlockState(pos).isOf(Blocks.ENDER_CHEST)) {
               double distance = player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(pos));
               if (distance < bestDistance) {
                  bestDistance = distance;
                  best = pos.toImmutable();
               }
            }
         }

         this.target = best;
      }
   }
}
