package org.ryzen.feature.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.screen.ScreenOpenEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class DeathCoordsFeature extends Feature {
   public final BooleanSetting copyToClipboard = this.register(new BooleanSetting("Copy To Clipboard", false));
   private boolean reported;
   private Vec3d lastDeathPosition;
   private String lastDeathDimension;

   public DeathCoordsFeature() {
      super("DeathCoords", "Shows your coordinates on death", FeatureCategory.MISC, -1);
   }

   public static DeathCoordsFeature get() {
      return FeatureManager.INSTANCE.getFeature(DeathCoordsFeature.class);
   }

   public Vec3d getLastDeathPosition() {
      return this.lastDeathPosition;
   }

   public String getLastDeathDimension() {
      return this.lastDeathDimension;
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.reported = false;
   }

   @EventTarget
   public void onScreenOpen(ScreenOpenEvent event) {
      if (event.getScreen() instanceof DeathScreen) {
         this.report(event.getClient(), event.getClient().player);
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player == null) {
         this.reported = false;
      } else if (player.isAlive() && player.getHealth() > 0.0F) {
         this.reported = false;
      } else {
         this.report(client, player);
      }
   }

   private void report(MinecraftClient client, ClientPlayerEntity player) {
      if (!this.reported && player != null) {
         this.reported = true;
         Vec3d position = player.getEntityPos();
         this.lastDeathPosition = position;
         this.lastDeathDimension = client.world == null ? null : client.world.getRegistryKey().getValue().getPath();
         BlockPos pos = BlockPos.ofFloored(position);
         String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
         String dimension = this.lastDeathDimension == null ? "" : " (" + this.lastDeathDimension + ")";
         ChatUtil.error("Смерть на " + coords + dimension);
         if (this.copyToClipboard.getValue()) {
            client.keyboard.setClipboard(coords);
         }
      }
   }
}
