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
public final class SoulSwarmFeature extends Feature implements MinecraftContext {
   public final NumberSetting count = this.register(new NumberSetting("Count", 24.0, 4.0, 80.0, 1.0, ""));
   public final NumberSetting orbitRadius = this.register(new NumberSetting("Orbit Radius", 1.4, 0.4, 4.0, 0.1, " blocks"));
   public final NumberSetting speed = this.register(new NumberSetting("Speed", 1.0, 0.2, 3.0, 0.1, ""));
   public final ColorSetting color = this.register(new ColorSetting("Color", 0xFF88FFEE));

   public SoulSwarmFeature() {
      super("SoulSwarm", "Orbiting soul particles around player / target", FeatureCategory.VISUAL, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent e) {}

   @EventTarget
   public void onRender3D(Render3DEvent e) {}
}
