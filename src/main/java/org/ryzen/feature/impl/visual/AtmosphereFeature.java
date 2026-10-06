package org.ryzen.feature.impl.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class AtmosphereFeature extends Feature {
   public final NumberSetting fogStart = this.register(new NumberSetting("Fog Start", 20.0, 0.0, 100.0, 1.0, " blocks"));
   public final NumberSetting fogEnd = this.register(new NumberSetting("Fog End", 80.0, 10.0, 200.0, 1.0, " blocks"));
   public final NumberSetting density = this.register(new NumberSetting("Density", 0.5, 0.0, 1.0, 0.05, ""));
   public final BooleanSetting customSky = this.register(new BooleanSetting("Custom Sky Tint", false));
   public final ColorSetting fogColor = this.register(new ColorSetting("Fog Color", 0xFF8899AA));
   public final ColorSetting skyColor = this.register(new ColorSetting("Sky Color", 0xFF4466AA));

   public AtmosphereFeature() {
      super("Atmosphere", "Fog density, sky tint and atmosphere control", FeatureCategory.VISUAL, -1);
   }
}
