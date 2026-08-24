package org.ryzen.mixin.gui;

import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.ryzen.context.RenderContext;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Textures;
import org.ryzen.utils.render.Theme;
import org.ryzen.utils.render.gui.Render2DUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({SplashOverlay.class})
public abstract class LoadingOverlayMixin {
   @Shadow
   @Final
   private MinecraftClient client;
   @Shadow
   @Final
   private ResourceReload reload;
   @Shadow
   @Final
   private Consumer<Optional<Throwable>> exceptionHandler;
   @Shadow
   @Final
   private boolean reloading;
   @Shadow
   private float progress;
   @Shadow
   private long reloadCompleteTime;
   @Shadow
   private long reloadStartTime;
   @Unique
   private static final Identifier LOGO_BOOT = Textures.Logos.BOOT;
   @Unique
   private long lastTime = -1L;
   @Unique
   private float animTime = 0.0F;

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRender(DrawContext guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      ci.cancel();
      if (Thread.currentThread().getPriority() != 10) {
         try {
            Thread.currentThread().setPriority(10);
         } catch (Throwable var45) {
         }
      }

      int width = this.client.getWindow().getScaledWidth();
      int height = this.client.getWindow().getScaledHeight();
      long currentTime = Util.getMeasuringTimeMs();
      if (this.reloading && this.reloadStartTime == -1L) {
         this.reloadStartTime = currentTime;
      }

      if (RenderContext.overlayStartTime == -1L) {
         RenderContext.overlayStartTime = currentTime;
      }

      long elapsed = currentTime - RenderContext.overlayStartTime;
      boolean isStartup = this.client.currentScreen == null;
      long animStartDelay = isStartup ? 1200L : 0L;
      if (this.lastTime == -1L) {
         this.lastTime = currentTime;
      }

      float deltaTime = (float)(currentTime - this.lastTime) / 1000.0F;
      this.lastTime = currentTime;
      float progressDelta = Math.min(deltaTime, 0.03F);
      if (elapsed >= animStartDelay) {
         this.animTime += progressDelta;
      }

      float targetProgress;
      if (elapsed < animStartDelay) {
         targetProgress = 0.0F;
      } else if (!this.reload.isComplete()) {
         float progressTime = Math.max(0.0F, (float)(elapsed - animStartDelay) / 1000.0F);
         float simulated = MathHelper.clamp(progressTime * 0.51F, 0.0F, 0.92F);
         targetProgress = Math.max(simulated, this.reload.getProgress() * 0.92F);
      } else {
         targetProgress = 1.0F;
      }

      float catchUpSpeed = !this.reload.isComplete() ? 0.2F : 0.5F;
      if (this.progress < targetProgress) {
         this.progress = Math.min(this.progress + catchUpSpeed * progressDelta, targetProgress);
      } else {
         this.progress = MathHelper.clamp(this.progress * 0.98F + targetProgress * 0.02F, 0.0F, 1.0F);
      }

      float fadeOutProgress = this.reloadCompleteTime > -1L ? (float)(currentTime - this.reloadCompleteTime) / 1500.0F : 0.0F;
      if (fadeOutProgress >= 1.0F) {
         this.client.setOverlay(null);
         RenderContext.overlayStartTime = -1L;
      } else {
         if (this.reload.isComplete() && this.client.currentScreen != null) {
            this.client.currentScreen.renderWithTooltip(guiGraphics, mouseX, mouseY, partialTick);
         }

         float alpha = 1.0F;
         if (this.reloadCompleteTime > -1L) {
            alpha = MathHelper.clamp(1.0F - fadeOutProgress, 0.0F, 1.0F);
         } else if (this.reloading && this.reloadStartTime > -1L) {
            float fadeInProgress = (float)(currentTime - this.reloadStartTime) / 1000.0F;
            alpha = MathHelper.clamp(fadeInProgress, 0.0F, 1.0F);
         }

         int bgAlpha = Math.round(255.0F * alpha);
         int bgCol = ColorUtil.rgba(21, 21, 22, bgAlpha);
         RenderContext.enter2D(null, guiGraphics, null);

         try {
            Render2DUtil.beginFrame();
            Render2DUtil.rect(0.0F, 0.0F, (float)width, (float)height).color(bgCol).draw();
            float baseLogoY = ((float)height - 96.0F) / 2.0F - 25.0F;
            float logoSize = 96.0F;
            if (this.reloadCompleteTime > -1L) {
               baseLogoY -= 15.0F * fadeOutProgress;
            }

            float logoX = ((float)width - logoSize) / 2.0F;
            int logoColor = ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha));
            Render2DUtil.texture(logoX, baseLogoY, logoSize, logoSize, LOGO_BOOT).color(logoColor).draw();
            float barWidth = 200.0F;
            float barHeight = 4.0F;
            float barX = ((float)width - barWidth) / 2.0F;
            float barY = ((float)height - 96.0F) / 2.0F - 25.0F + 96.0F + 55.0F;
            if (this.reloadCompleteTime > -1L) {
               barY += 15.0F * fadeOutProgress;
            }

            int trackColor = ColorUtil.rgba(38, 38, 43, Math.round(255.0F * alpha));
            int borderColor = ColorUtil.rgba(255, 255, 255, Math.round(10.2F * alpha));
            Render2DUtil.rect(barX, barY, barWidth, barHeight).color(trackColor).radius(2.0F).border(0.5F, borderColor).draw();
            int accentColor = Theme.getAccent();
            int accentFadeColor = ColorUtil.withAlpha(accentColor, Math.round(255.0F * alpha));
            float fillWidth = barWidth * this.progress;
            if (fillWidth > 0.0F) {
               Render2DUtil.rect(barX, barY, fillWidth, barHeight).color(accentFadeColor).radius(2.0F).draw();
               float glareWidth = 40.0F;
               float glareProgress = (float)(currentTime % 1500L) / 1500.0F;
               float glareX = barX + (fillWidth + glareWidth) * glareProgress - glareWidth;
               float drawGlareX = Math.max(barX, glareX);
               float drawGlareWidth = Math.min(barX + fillWidth, glareX + glareWidth) - drawGlareX;
               if (drawGlareWidth > 0.0F) {
                  Render2DUtil.rect(drawGlareX, barY, drawGlareWidth, barHeight)
                     .color(ColorUtil.rgba(255, 255, 255, Math.round(75.0F * alpha)))
                     .radius(2.0F)
                     .draw();
               }
            }

            Render2DUtil.flush();
         } finally {
            RenderContext.exit2D();
         }
      }
   }
}
