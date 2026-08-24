package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class ElytraBoostFeature extends Feature implements MinecraftContext {
   private static final String MODE_STATIC = "Static";
   private static final String MODE_CUSTOM = "Custom";
   private static final float BAND_DEGREES = 5.0F;
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Static", "Static", "Custom"));
   public final NumberSetting value = this.register(new NumberSetting("Speed", 1.5, 0.1, 6.0, 0.05, "x").visibleWhen(() -> this.mode.is("Static")));
   private final NumberSetting[] bands = new NumberSetting[]{
      this.band("XZ 0-5°", 1.52),
      this.band("XZ 5-10°", 1.53),
      this.band("XZ 10-15°", 1.54),
      this.band("XZ 15-20°", 1.55),
      this.band("XZ 20-25°", 1.56),
      this.band("XZ 25-30°", 1.57),
      this.band("XZ 30-35°", 1.58),
      this.band("XZ 35-40°", 1.59),
      this.band("XZ 40-45°", 1.6)
   };

   public ElytraBoostFeature() {
      super("ElytraBoost", "Speeds up elytra gliding", FeatureCategory.MOVEMENT, -1);
   }

   private NumberSetting band(String name, double preset) {
      return this.register(new NumberSetting(name, preset, 1.5, 3.0, 0.01, "x").visibleWhen(() -> this.mode.is("Custom")));
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && player.isGliding()) {
         double factor = this.mode.is("Custom") ? this.bandFor(player.getPitch()).getValue() : this.value.getValue();
         Vec3d motion = player.getVelocity();
         player.setVelocity(motion.x * factor, motion.y, motion.z * factor);
      }
   }

   private NumberSetting bandFor(float pitch) {
      int index = (int)(Math.abs(pitch) / 5.0F);
      return this.bands[MathHelper.clamp(index, 0, this.bands.length - 1)];
   }
}
