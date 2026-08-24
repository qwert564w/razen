package org.ryzen.feature.impl.player;

import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class AimingItemsFeature extends Feature implements PlayerContext {
   private static final String TARGET_HEADS = "Heads";
   private static final String TARGET_ELYTRA = "Elytra";
   private static final double ITEM_Y_OFFSET = 0.15;
   public final MultiSelectSetting targets = this.register(new MultiSelectSetting("Aim At", List.of("Heads", "Elytra"), "Heads", "Elytra"));
   public final NumberSetting range = this.register(new NumberSetting("Range", 128.0, 16.0, 512.0, 8.0, " blocks"));
   private ItemEntity target;
   private boolean announced;

   public AimingItemsFeature() {
      super("AimingItems", "Aims at dropped heads and elytras so you fly straight to them", FeatureCategory.PLAYER, -1);
   }

   @Override
   protected void onDisable() {
      this.target = null;
      this.announced = false;
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.target = null;
      this.announced = false;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null) {
         double rangeSqr = this.range.getValue() * this.range.getValue();
         ItemEntity best = null;
         double bestDistance = Double.MAX_VALUE;

         for (Entity entity : client.world.getEntities()) {
            if (entity instanceof ItemEntity) {
               ItemEntity item = (ItemEntity)entity;
               if (this.isTarget(item.getStack())) {
                  double distance = player.squaredDistanceTo(item);
                  if (distance < bestDistance && distance <= rangeSqr) {
                     bestDistance = distance;
                     best = item;
                  }
               }
            }
         }

         if (best == null) {
            this.target = null;
            this.announced = false;
         } else {
            this.target = best;
            this.aimAt(player, best);
            if (!this.announced) {
               this.announced = true;
               ChatUtil.info("AimingItems: лечу на " + best.getStack().getName().getString());
            }
         }
      } else {
         this.target = null;
      }
   }

   private void aimAt(ClientPlayerEntity player, ItemEntity item) {
      Vec3d delta = item.getEntityPos().add(0.0, 0.15, 0.0).subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float yaw = MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
      float pitch = MathHelper.clamp((float)(-Math.toDegrees(Math.atan2(delta.y, horizontal))), -90.0F, 90.0F);
      player.setYaw(yaw);
      player.setPitch(pitch);
      player.headYaw = yaw;
   }

   private boolean isTarget(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         String name = stack.getName().getString().toLowerCase(Locale.ROOT);
         return !this.targets.isSelected("Heads") || !stack.isOf(Items.PLAYER_HEAD) && !name.contains("head") && !name.contains("голов")
            ? this.targets.isSelected("Elytra") && (stack.isOf(Items.ELYTRA) || name.contains("elytra") || name.contains("элитр"))
            : true;
      }
   }
}
