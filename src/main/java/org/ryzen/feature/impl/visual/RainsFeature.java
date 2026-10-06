package org.ryzen.feature.impl.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class RainsFeature extends Feature implements MinecraftContext {
   public final NumberSetting density = this.register(new NumberSetting("Density", 80.0, 10.0, 200.0, 5.0, ""));
   public final NumberSetting length = this.register(new NumberSetting("Length", 1.2, 0.3, 3.0, 0.1, ""));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 1.0, 0.2, 3.0, 0.1, ""));
   public final BooleanSetting splash = this.register(new BooleanSetting("Splash", true));
   public final ColorSetting color = this.register(new ColorSetting("Color", 0xAA88AADD));

   public RainsFeature() {
      super("Rains", "Custom rain / particle precipitation overlay", FeatureCategory.VISUAL, -1);
   }

   @EventTarget
   public void onRender3D(Render3DEvent e) {}
}
