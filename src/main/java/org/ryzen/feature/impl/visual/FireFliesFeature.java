package org.ryzen.feature.impl.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class FireFliesFeature extends Feature implements MinecraftContext {
   public final NumberSetting count = this.register(new NumberSetting("Count", 40.0, 5.0, 120.0, 1.0, ""));
   public final NumberSetting radius = this.register(new NumberSetting("Radius", 12.0, 3.0, 30.0, 0.5, " blocks"));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 0.6, 0.1, 2.0, 0.05, ""));
   public final NumberSetting size = this.register(new NumberSetting("Size", 0.12, 0.04, 0.4, 0.02, ""));
   public final ColorSetting color = this.register(new ColorSetting("Color", 0xFFFFCC66));

   public FireFliesFeature() {
      super("FireFlies", "Ambient firefly particles around the player", FeatureCategory.VISUAL, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent e) {}

   @EventTarget
   public void onRender3D(Render3DEvent e) {}
}
