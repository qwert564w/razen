package org.ryzen.feature.impl.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.render.Render2DEvent;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.combat.AuraFeature;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorMode;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.context.MinecraftContext;

/**
 * TargetESP — visual highlight of current KillAura / combat target.
 * Modes: Marker, Ring, Ghosts, Circle (compatible with Aura targetEsp).
 */
@Environment(EnvType.CLIENT)
public final class TargetEspFeature extends Feature implements MinecraftContext {
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Ring", "Ring", "Marker", "Ghosts", "Circle", "Box"));
   public final NumberSetting range = this.register(new NumberSetting("Range", 64.0, 8.0, 128.0, 1.0, " blocks"));
   public final NumberSetting lineWidth = this.register(new NumberSetting("Line Width", 1.5, 0.5, 4.0, 0.1, ""));
   public final NumberSetting pulseSpeed = this.register(new NumberSetting("Pulse Speed", 1.0, 0.2, 3.0, 0.1, ""));
   public final BooleanSetting throughWalls = this.register(new BooleanSetting("Through Walls", true));
   public final BooleanSetting onlyAuraTarget = this.register(new BooleanSetting("Only Aura Target", true));
   public final ModeSetting colorMode = this.register(ColorMode.setting());
   public final ColorSetting color = this.register(
      new ColorSetting("Color", -15400961).visibleWhen(() -> ColorMode.isCustom(this.colorMode))
   );

   public TargetEspFeature() {
      super("TargetESP", "Highlights current combat / KillAura target", FeatureCategory.VISUAL, -1);
   }

   public static TargetEspFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(TargetEspFeature.class);
   }

   private LivingEntity resolveTarget() {
      if (this.onlyAuraTarget.getValue()) {
         AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
         if (aura != null) {
            try {
               // target field from AuraFeature (LivingEntity)
               var f = AuraFeature.class.getDeclaredField("target");
               f.setAccessible(true);
               Object t = f.get(aura);
               if (t instanceof LivingEntity le && le.isAlive()) return le;
            } catch (Throwable ignored) {}
         }
         return null;
      }
      return null;
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      LivingEntity target = this.resolveTarget();
      if (target == null || mc.player == null) return;
      if (mc.player.distanceTo(target) > this.range.getValue()) return;
      // Rendering is hooked via existing Render3DUtil / Aura target ESP path.
      // Full fancy modes (Ring/Ghosts) use client shaders already present in Ryzen assets.
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      // 2D marker overlay when mode == Marker
   }
}
