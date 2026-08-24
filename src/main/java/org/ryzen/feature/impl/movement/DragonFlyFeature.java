package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class DragonFlyFeature extends Feature implements MinecraftContext {
   private static final String MODE_DEFAULT = "Default";
   private static final String MODE_CUSTOM = "Custom";
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Default", "Default", "Custom"));
   public final NumberSetting speedX = this.register(new NumberSetting("Horizontal", 1.012, 0.1, 5.0, 0.001, "x"));
   public final NumberSetting speedY = this.register(new NumberSetting("Vertical", 1.0, 0.1, 5.0, 0.001, "x"));
   public final NumberSetting diagonalSpeed = this.register(
      new NumberSetting("Diagonal", 1.0109, 0.1, 5.0, 0.001, "x").visibleWhen(() -> this.mode.is("Custom"))
   );

   public DragonFlyFeature() {
      super("DragonFly", "Speeds up creative flight", FeatureCategory.MOVEMENT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && player.getAbilities().flying) {
         double horizontal = this.mode.is("Custom") && isDiagonal(client) ? this.diagonalSpeed.getValue() : this.speedX.getValue();
         Vec3d motion = player.getVelocity();
         player.setVelocity(motion.x * horizontal, motion.y * this.speedY.getValue(), motion.z * horizontal);
      }
   }

   private static boolean isDiagonal(MinecraftClient client) {
      if (client.options == null) {
         return false;
      } else {
         int pressed = 0;
         if (client.options.forwardKey.isPressed()) {
            pressed++;
         }

         if (client.options.backKey.isPressed()) {
            pressed++;
         }

         if (client.options.leftKey.isPressed()) {
            pressed++;
         }

         if (client.options.rightKey.isPressed()) {
            pressed++;
         }

         return pressed >= 2;
      }
   }
}
