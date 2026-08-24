package org.ryzen.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.bar.Bar;
import net.minecraft.client.gui.hud.bar.ExperienceBar;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.ryzen.context.RenderContext;
import org.ryzen.event.EventManager;
import org.ryzen.event.Events;
import org.ryzen.event.events.render.Render2DEvent;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.visual.BoardSpooferFeature;
import org.ryzen.feature.impl.visual.CrosshairFeature;
import org.ryzen.feature.impl.visual.HudFeature;
import org.ryzen.feature.impl.visual.RemovalsFeature;
import org.ryzen.utils.render.gui.Render2DUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({InGameHud.class})
public abstract class HudMixin {
   @Shadow
   @Final
   private MinecraftClient client;
   @Unique
   private boolean blade$hotbarDecorationsShifted;

   @Inject(
      method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDisplayScoreboardSidebar(DrawContext guiGraphicsExtractor, ScoreboardObjective objective, CallbackInfo ci) {
      if (RemovalsFeature.shouldRemoveScoreboard()) {
         ci.cancel();
      }
   }

   @ModifyArg(
      method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"
      ),
      index = 1
   )
   private Text ryzen$spoofScoreboardText(Text original) {
      return BoardSpooferFeature.transform(original);
   }

   @Inject(
      method = {"renderMainHud"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void blade$shiftHotbarDecorations(DrawContext guiGraphicsExtractor, RenderTickCounter deltaTracker, CallbackInfo ci) {
      float offsetX = HudFeature.hotbarDecorationOffsetX(this.client);
      float offsetY = HudFeature.hotbarDecorationOffsetY(this.client);
      this.blade$hotbarDecorationsShifted = offsetX != 0.0F || offsetY != 0.0F;
      if (this.blade$hotbarDecorationsShifted) {
         guiGraphicsExtractor.getMatrices().pushMatrix();
         guiGraphicsExtractor.getMatrices().translate(offsetX, offsetY);
      }
   }

   @Inject(
      method = {"renderMainHud"},
      at = {@At("RETURN")}
   )
   private void blade$unshiftHotbarDecorations(DrawContext guiGraphicsExtractor, RenderTickCounter deltaTracker, CallbackInfo ci) {
      if (this.blade$hotbarDecorationsShifted) {
         guiGraphicsExtractor.getMatrices().popMatrix();
         this.blade$hotbarDecorationsShifted = false;
      }
   }

   @WrapOperation(
      method = {"renderMainHud"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/InGameHud;renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V"
      )}
   )
   private void blade$scalePlayerStatus(InGameHud instance, DrawContext guiGraphicsExtractor, Operation<Void> original) {
      this.blade$withScaledStatus(guiGraphicsExtractor, () -> original.call(new Object[]{instance, guiGraphicsExtractor}));
   }

   @WrapOperation(
      method = {"renderMainHud"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/InGameHud;renderMountHealth(Lnet/minecraft/client/gui/DrawContext;)V"
      )}
   )
   private void blade$scaleVehicleStatus(InGameHud instance, DrawContext guiGraphicsExtractor, Operation<Void> original) {
      this.blade$withScaledStatus(guiGraphicsExtractor, () -> original.call(new Object[]{instance, guiGraphicsExtractor}));
   }

   @Unique
   private void blade$withScaledStatus(DrawContext guiGraphicsExtractor, Runnable draw) {
      float scale = HudFeature.hotbarDecorationScale(this.client);
      if (Math.abs(scale - 1.0F) < 0.001F) {
         draw.run();
      } else {
         float pivotX = (float)this.client.getWindow().getScaledWidth() / 2.0F;
         float pivotY = (float)this.client.getWindow().getScaledHeight() - 22.0F;
         guiGraphicsExtractor.getMatrices().pushMatrix();
         guiGraphicsExtractor.getMatrices().translate(pivotX, pivotY);
         guiGraphicsExtractor.getMatrices().scale(scale);
         guiGraphicsExtractor.getMatrices().translate(-pivotX, -pivotY);

         try {
            draw.run();
         } finally {
            guiGraphicsExtractor.getMatrices().popMatrix();
         }
      }
   }

   @Inject(
      method = {"renderHotbar"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void blade$hideVanillaHotbar(DrawContext guiGraphicsExtractor, RenderTickCounter deltaTracker, CallbackInfo ci) {
      if (HudFeature.customHotbarActive()) {
         ci.cancel();
      }
   }

   @Redirect(
      method = {"renderMainHud"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"
      )
   )
   private void blade$skipXpBarBackground(Bar bar, DrawContext guiGraphicsExtractor, RenderTickCounter deltaTracker) {
      if (!(bar instanceof ExperienceBar) || !HudFeature.customHotbarActive()) {
         bar.renderBar(guiGraphicsExtractor, deltaTracker);
      }
   }

   @Redirect(
      method = {"renderMainHud"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderAddons(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"
      )
   )
   private void blade$skipXpBarFill(Bar bar, DrawContext guiGraphicsExtractor, RenderTickCounter deltaTracker) {
      if (!(bar instanceof ExperienceBar) || !HudFeature.customHotbarActive()) {
         bar.renderAddons(guiGraphicsExtractor, deltaTracker);
      }
   }

   @Redirect(
      method = {"renderMainHud"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/bar/Bar;drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V"
      )
   )
   private void blade$skipXpLevel(DrawContext guiGraphics, TextRenderer font, int experienceLevel) {
      if (!HudFeature.customHotbarActive()) {
         Bar.drawExperienceLevel(guiGraphics, font, experienceLevel);
      }
   }

   @Inject(
      method = {"renderHeldItemTooltip"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void blade$hideVanillaSelectedItemName(DrawContext guiGraphicsExtractor, CallbackInfo ci) {
      if (HudFeature.customHotbarActive()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderCrosshair"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void hideVanillaCrosshair(DrawContext guiGraphicsExtractor, RenderTickCounter deltaTracker, CallbackInfo ci) {
      if (FeatureManager.INSTANCE.getEnabled(CrosshairFeature.class) != null) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void onExtractRenderState(DrawContext guiGraphicsExtractor, RenderTickCounter deltaTracker, CallbackInfo ci) {
      InGameHud gui = (InGameHud)(Object)this;
      RenderContext.enter2D(gui, guiGraphicsExtractor, deltaTracker);

      try {
         Render2DUtil.beginFrame();
         if (EventManager.hasListeners(Render2DEvent.class)) {
            EventManager.call(Events.RENDER_2D.set(this.client, gui, guiGraphicsExtractor, deltaTracker));
         }

         Render2DUtil.flush();
      } finally {
         RenderContext.exit2D();
      }
   }
}
