package org.ryzen.feature.impl.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class BladeStormFeature extends Feature implements MinecraftContext {
   public final NumberSetting blades = this.register(new NumberSetting("Blades", 6.0, 2.0, 16.0, 1.0, ""));
   public final NumberSetting radius = this.register(new NumberSetting("Radius", 1.6, 0.5, 4.0, 0.1, " blocks"));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 1.2, 0.2, 4.0, 0.1, ""));
   public final NumberSetting height = this.register(new NumberSetting("Height", 1.0, 0.2, 2.5, 0.1, ""));
   public final ColorSetting color = this.register(new ColorSetting("Color", 0xFFFF4466));

   public BladeStormFeature() {
      super("BladeStorm", "Orbiting blade / slash visual around the player", FeatureCategory.VISUAL, -1);
   }

   @EventTarget
   public void onRender3D(Render3DEvent e) {}
}
