package org.ryzen.feature.impl.combat;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.AttackEvent;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.render.Render2DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.utils.combat.neuro.NeuroRotationData;
import org.ryzen.utils.combat.neuro.NeuroSample;
import org.ryzen.utils.render.Theme;
import org.ryzen.utils.render.gui.MsdfFont;
import org.ryzen.utils.render.gui.Render2DUtil;
import org.ryzen.utils.render.gui.UiFontStyle;
import org.ryzen.utils.render.gui.UiFonts;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class AiTrainingFeature extends Feature implements MinecraftContext {
   private static final UUID DUMMY_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
   private static final String DUMMY_NAME = "AiTrainingDummy";
   private OtherClientPlayerEntity dummy;
   private final NeuroRotationData buffer = new NeuroRotationData("recording");
   private int sampleCount;

   public AiTrainingFeature() {
      super("AiTraining", "Records combat samples on a training dummy", FeatureCategory.COMBAT, -1);
   }

   public NeuroRotationData recordingBuffer() {
      return this.buffer;
   }

   public int recordedSamples() {
      return this.sampleCount;
   }

   public void clearRecording() {
      this.buffer.clear();
      this.sampleCount = 0;
   }

   @Override
   protected void onEnable() {
      this.buffer.clear();
      this.sampleCount = 0;
      ChatUtil.info("AiTraining запущен  •  Двигайся и бей манекена — каждое движение записывается");
      this.spawnDummy();
   }

   @Override
   protected void onDisable() {
      this.removeDummy();
      ChatUtil.info("Записало " + this.sampleCount + " семплов  •  Чтобы сохранить и использовать в килке: .ai save <name>");
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.removeDummy();
   }

   @EventTarget
   public void onAttack(AttackEvent event) {
      if (this.dummy != null && mc.player != null && mc.world != null) {
         Entity target = mc.targetedEntity;
         if (target == this.dummy) {
            ClientPlayerEntity player = mc.player;
            if (mc.interactionManager != null) {
               mc.interactionManager.attackEntity(player, this.dummy);
            }

            player.swingHand(Hand.MAIN_HAND);
            mc.world
               .playSoundClient(
                  this.dummy.getX(),
                  this.dummy.getY(),
                  this.dummy.getZ(),
                  SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                  player.getSoundCategory(),
                  1.0F,
                  1.0F,
                  false
               );
            if (this.dummy.hurtTime <= 0) {
               this.dummy.hurtTime = 10;
               this.dummy.maxHurtTime = 10;
               this.dummy.knockedBack = true;
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      ClientWorld level = event.getClient().world;
      if (player != null && level != null) {
         if (this.dummy != null && this.dummy.isAlive() && !this.dummy.isRemoved()) {
            if (this.isMoving(player)) {
               NeuroSample sample = new NeuroSample(
                  player.getYaw(), player.getPitch(), player.sidewaysSpeed, player.forwardSpeed, player.isSprinting(), !player.isOnGround()
               );
               this.buffer.add(sample);
               this.sampleCount++;
            }
         } else {
            this.spawnDummy();
         }
      }
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      if (this.isEnabled() && mc.player != null) {
         DrawContext graphics = event.getGuiGraphicsExtractor();
         float unit = 1.0F / (float)mc.getWindow().getScaleFactor();
         MsdfFont font = UiFonts.sfProDisplay();
         float fontSize = 16.0F * unit;
         float letterSpacing = fontSize * UiFontStyle.MEDIUM.letterSpacingEm();
         String label = "Обученно: " + this.sampleCount;
         float textWidth = font.measureWidth(label, fontSize, letterSpacing);
         float padding = 10.0F * unit;
         float pillWidth = textWidth + padding * 2.0F;
         float pillHeight = 28.0F * unit;
         float screenWidth = (float)mc.getWindow().getScaledWidth();
         float screenHeight = (float)mc.getWindow().getScaledHeight();
         float x = (screenWidth - pillWidth) / 2.0F;
         float y = screenHeight / 2.0F + 14.0F * unit;
         Render2DUtil.rect(x, y, pillWidth, pillHeight)
            .color(Theme.Colors.BACKGROUND_PRIMARY_50)
            .radius(pillHeight / 2.0F)
            .border(Math.max(0.5F, 0.5F * unit), Theme.Colors.OUTLINES_SMALL)
            .blur(8.0F * unit)
            .draw();
         float centerY = y + pillHeight / 2.0F;
         Render2DUtil.text(x + padding, font.centeredTextY(centerY, fontSize), fontSize, label).style(UiFontStyle.MEDIUM).color(Theme.getAccent()).draw();
      }
   }

   private boolean isMoving(ClientPlayerEntity player) {
      return Math.abs(player.sidewaysSpeed) > 0.01F || Math.abs(player.forwardSpeed) > 0.01F || player.isSprinting() || !player.isOnGround();
   }

   private void spawnDummy() {
      ClientPlayerEntity player = mc.player;
      ClientWorld level = mc.world;
      if (player != null && level != null) {
         this.removeDummy();
         GameProfile profile = new GameProfile(DUMMY_UUID, "AiTrainingDummy");
         this.dummy = new OtherClientPlayerEntity(level, profile);
         this.dummy.setPosition(player.getX(), player.getY(), player.getZ());
         this.dummy.setYaw(player.getYaw() + 180.0F);
         this.dummy.headYaw = player.getYaw() + 180.0F;

         try {
            level.addEntity(this.dummy);
         } catch (Throwable var5) {
            this.dummy = null;
         }
      }
   }

   private void removeDummy() {
      if (this.dummy != null) {
         try {
            this.dummy.discard();
         } catch (Throwable var2) {
         }

         this.dummy = null;
      }
   }

   public Entity getDummy() {
      return this.dummy;
   }
}
