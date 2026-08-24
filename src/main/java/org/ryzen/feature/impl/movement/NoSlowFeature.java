package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;

@Environment(EnvType.CLIENT)
public final class NoSlowFeature extends Feature implements MinecraftContext {
   private static final String MODE_NONE = "None";
   private static final String MODE_GRIM_TICKS = "Grim Ticks";
   private static final String MODE_LONY_GRIEF = "LonyGrief";
   private static final String MODE_VANILLA = "Vanilla";
   private static final int GRIM_PERIOD = 2;
   public final ModeSetting mainHand = this.register(new ModeSetting("Main Hand", "Vanilla", "None", "Grim Ticks", "LonyGrief", "Vanilla"));
   public final ModeSetting offHand = this.register(new ModeSetting("Off Hand", "Grim Ticks", "None", "Grim Ticks", "Vanilla"));
   public final BooleanSetting mainHandSprint = this.register(new BooleanSetting("Main Hand Sprint", true));
   public final BooleanSetting offHandSprint = this.register(new BooleanSetting("Off Hand Sprint", true));
   private int mainHandTicks;
   private int offHandTicks;

   public NoSlowFeature() {
      super("NoSlow", "Keeps your speed while using an item", FeatureCategory.MOVEMENT, -1);
   }

   public static NoSlowFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(NoSlowFeature.class);
   }

   @Override
   protected void onDisable() {
      this.mainHandTicks = 0;
      this.offHandTicks = 0;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && player.isUsingItem()) {
         if (player.getActiveHand() == Hand.MAIN_HAND) {
            this.mainHandTicks++;
            if (this.mainHandSprint.getValue()) {
               player.setSprinting(true);
            }
         } else {
            this.offHandTicks++;
            if (this.offHandSprint.getValue()) {
               player.setSprinting(true);
            }
         }
      } else {
         this.mainHandTicks = 0;
         this.offHandTicks = 0;
      }
   }

   public boolean shouldKeepSpeed(ClientPlayerEntity player) {
      if (player != null && player.isUsingItem()) {
         return player.getActiveHand() == Hand.MAIN_HAND ? this.keepForMainHand(player) : this.keepForOffHand();
      } else {
         return false;
      }
   }

   private boolean keepForMainHand(ClientPlayerEntity player) {
      String mode = this.mainHand.getValue();
      if ("Vanilla".equals(mode)) {
         return true;
      } else if ("LonyGrief".equals(mode)) {
         return player.getItemUseTimeLeft() > 0;
      } else if ("Grim Ticks".equals(mode) && this.mainHandTicks >= 2) {
         this.mainHandTicks = 0;
         return true;
      } else {
         return false;
      }
   }

   private boolean keepForOffHand() {
      String mode = this.offHand.getValue();
      if ("Vanilla".equals(mode)) {
         return true;
      } else if ("Grim Ticks".equals(mode) && this.offHandTicks >= 2) {
         this.offHandTicks = 0;
         return true;
      } else {
         return false;
      }
   }
}
