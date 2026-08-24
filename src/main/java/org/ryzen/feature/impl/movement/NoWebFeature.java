package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class NoWebFeature extends Feature implements MinecraftContext {
   private static final String MODE_CANCEL = "Cancel";
   private static final String MODE_SPEED = "Speed";
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Cancel", "Cancel", "Speed"));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 1.0, 0.1, 2.0, 0.1, "x").visibleWhen(() -> this.mode.is("Speed")));

   public NoWebFeature() {
      super("NoWeb", "Removes the cobweb slowdown", FeatureCategory.MOVEMENT, -1);
   }

   public static NoWebFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(NoWebFeature.class);
   }

   public static boolean shouldCancelWeb(PlayerEntity player, BlockState state) {
      NoWebFeature feature = getEnabled();
      return feature != null && player == MinecraftContext.mc.player && state.isOf(Blocks.COBWEB) && feature.mode.is("Cancel");
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && this.mode.is("Speed") && inWeb(player)) {
         double factor = this.speed.getValue();
         Vec3d motion = player.getVelocity();
         player.setVelocity(motion.x * factor, motion.y, motion.z * factor);
      }
   }

   private static boolean inWeb(ClientPlayerEntity player) {
      return player.getEntityWorld().getBlockState(player.getBlockPos()).isOf(Blocks.COBWEB)
         || player.getEntityWorld().getBlockState(player.getBlockPos().up()).isOf(Blocks.COBWEB);
   }
}
