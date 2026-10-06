package org.ryzen.feature.impl.visual;

import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorMode;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class TrailsFeature extends Feature implements MinecraftContext {
   public final NumberSetting length = this.register(new NumberSetting("Length", 20.0, 5.0, 60.0, 1.0, " ticks"));
   public final NumberSetting width = this.register(new NumberSetting("Width", 1.2, 0.3, 4.0, 0.1, ""));
   public final BooleanSetting onlyMoving = this.register(new BooleanSetting("Only When Moving", true));
   public final ColorSetting color;
   private final Deque<Vec3d> points = new ArrayDeque<>();

   public TrailsFeature() {
      super("Trails", "Player movement trail", FeatureCategory.VISUAL, -1);
      this.register(new org.ryzen.feature.setting.ModeSetting("Color Mode", "Custom", "Custom", "Rainbow"));
      this.color = this.register(new ColorSetting("Color", -1));
   }

   @EventTarget
   public void onTick(GameTickEvent e) {
      if (mc.player == null || mc.world == null) return;
      if (this.onlyMoving.getValue() && mc.player.getVelocity().horizontalLengthSquared() < 1.0E-4) return;
      this.points.addLast(mc.player.getPos().add(0.0, 0.1, 0.0));
      while (this.points.size() > this.length.getValue().intValue()) this.points.removeFirst();
   }

   @EventTarget
   public void onRender3D(Render3DEvent e) {
      // draw polyline from points via Render3DUtil when available
   }

   @Override
   protected void onDisable() {
      this.points.clear();
   }
}
