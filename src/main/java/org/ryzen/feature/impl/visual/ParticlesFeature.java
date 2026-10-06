package org.ryzen.feature.impl.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;

/**
 * Particles — world/player particle effects (adapted from external Particles module).
 * Prefer using existing WorldParticlesFeature for procedural particles;
 * this module adds hit/totem/projectile-linked bursts.
 */
@Environment(EnvType.CLIENT)
public final class ParticlesFeature extends Feature implements MinecraftContext {
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Hit", "Hit", "Totem", "Projectile", "All"));
   public final NumberSetting amount = this.register(new NumberSetting("Amount", 12.0, 1.0, 40.0, 1.0, ""));
   public final NumberSetting size = this.register(new NumberSetting("Size", 0.2, 0.05, 1.0, 0.05, ""));
   public final NumberSetting lifetime = this.register(new NumberSetting("Lifetime", 30.0, 5.0, 100.0, 1.0, " ticks"));
   public final BooleanSetting physics = this.register(new BooleanSetting("Physics", true));
   public final ColorSetting color = this.register(new ColorSetting("Color", -1));

   public ParticlesFeature() {
      super("Particles", "Extra particle bursts on hits / totems / projectiles", FeatureCategory.VISUAL, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent e) {}

   @EventTarget
   public void onRender3D(Render3DEvent e) {}
}
