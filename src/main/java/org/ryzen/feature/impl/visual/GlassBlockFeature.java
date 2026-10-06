package org.ryzen.feature.impl.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class GlassBlockFeature extends Feature {
   public final NumberSetting opacity = this.register(new NumberSetting("Opacity", 0.45, 0.05, 1.0, 0.05, ""));
   public final BooleanSetting walls = this.register(new BooleanSetting("Walls", true));
   public final BooleanSetting floors = this.register(new BooleanSetting("Floors", false));
   public final BooleanSetting xrayStyle = this.register(new BooleanSetting("Xray Style", false));
   public final ColorSetting tint = this.register(new ColorSetting("Tint", 0x88AADDFF));

   public GlassBlockFeature() {
      super("GlassBlock", "Renders nearby blocks as translucent glass-like surfaces", FeatureCategory.VISUAL, -1);
   }
}
