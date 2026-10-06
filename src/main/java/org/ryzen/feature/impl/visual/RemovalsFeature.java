package org.ryzen.feature.impl.visual;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.MultiSelectSetting;

/**
 * Removals / NoRender — hides overlays, weather, particles, sounds, camera shake, lava fog.
 * Extended with options from external NoRender module.
 */
@Environment(EnvType.CLIENT)
public final class RemovalsFeature extends Feature {
   private static RemovalsFeature instance;

   private final MultiSelectSetting overlay = this.register(
      new MultiSelectSetting(
         "Overlay",
         Set.of("Fire", "Bad Effects", "Totem Overlay"),
         "Fire", "Bad Effects", "Shaking", "Totem Overlay", "Scoreboard", "Boss Bar", "Lava Fog"
      )
   );
   private final MultiSelectSetting world = this.register(
      new MultiSelectSetting("World", Set.of("Weather"), "Weather", "Totem Particles", "Effects")
   );
   private final MultiSelectSetting sounds = this.register(
      new MultiSelectSetting("Sounds", Set.of(), "Hit", "Hurt", "Totem", "Step", "Eat", "Firework", "Warden")
   );
   private final BooleanSetting cameraThroughBlocks = this.register(new BooleanSetting("Camera Through Blocks", false));
   private final BooleanSetting noCameraShake = this.register(new BooleanSetting("No Camera Shake", true));

   public RemovalsFeature() {
      super("Removals", "Hides distracting overlays, world effects, particles, and sounds (NoRender).", FeatureCategory.VISUAL, -1);
      instance = this;
   }

   private static boolean active() {
      return instance != null && instance.isEnabled();
   }

   public static boolean shouldRemoveFireOverlay() {
      return active() && instance.overlay.isSelected("Fire");
   }

   public static boolean shouldRemoveBadEffectsVisuals() {
      return active() && instance.overlay.isSelected("Bad Effects");
   }

   public static boolean shouldRemoveShaking() {
      return active() && (instance.overlay.isSelected("Shaking") || instance.noCameraShake.getValue());
   }

   public static boolean shouldRemoveTotemOverlay() {
      return active() && instance.overlay.isSelected("Totem Overlay");
   }

   public static boolean shouldRemoveScoreboard() {
      return active() && instance.overlay.isSelected("Scoreboard");
   }

   public static boolean shouldRemoveBossBar() {
      return active() && instance.overlay.isSelected("Boss Bar");
   }

   public static boolean shouldRemoveLavaFog() {
      return active() && instance.overlay.isSelected("Lava Fog");
   }

   public static boolean shouldRemoveWeather() {
      return active() && instance.world.isSelected("Weather");
   }

   public static boolean shouldCameraThroughBlocks() {
      return active() && instance.cameraThroughBlocks.getValue();
   }

   public static boolean shouldRemoveParticle(ParticleEffect options) {
      if (!active() || options == null) return false;
      if (instance.world.isSelected("Totem Particles") && options.getType() == ParticleTypes.TOTEM_OF_UNDYING) return true;
      return instance.world.isSelected("Effects");
   }

   public static boolean shouldRemoveSound(SoundEvent sound) {
      if (!active() || sound == null) return false;
      String path = sound.id().getPath();
      if (instance.sounds.isSelected("Hit") && path.contains("hit")) return true;
      if (instance.sounds.isSelected("Hurt") && path.contains("hurt")) return true;
      if (instance.sounds.isSelected("Totem") && path.contains("totem")) return true;
      if (instance.sounds.isSelected("Step") && path.contains("step")) return true;
      if (instance.sounds.isSelected("Eat") && (path.contains("eat") || path.contains("burp"))) return true;
      if (instance.sounds.isSelected("Firework") && path.contains("firework")) return true;
      if (instance.sounds.isSelected("Warden") && path.contains("warden")) return true;
      return false;
   }
}
