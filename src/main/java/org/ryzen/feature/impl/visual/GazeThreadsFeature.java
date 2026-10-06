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
public final class GazeThreadsFeature extends Feature implements MinecraftContext {
   public final NumberSetting maxDistance = this.register(new NumberSetting("Max Distance", 24.0, 6.0, 64.0, 1.0, " blocks"));
   public final NumberSetting width = this.register(new NumberSetting("Width", 0.8, 0.2, 3.0, 0.1, ""));
   public final BooleanSetting onlyPlayers = this.register(new BooleanSetting("Only Players", true));
   public final BooleanSetting throughWalls = this.register(new BooleanSetting("Through Walls", false));
   public final ColorSetting color = this.register(new ColorSetting("Color", 0xFFFF66AA));

   public GazeThreadsFeature() {
      super("GazeThreads", "Draws gaze / look threads from entities toward you", FeatureCategory.VISUAL, -1);
   }

   @EventTarget
   public void onRender3D(Render3DEvent e) {}
}
