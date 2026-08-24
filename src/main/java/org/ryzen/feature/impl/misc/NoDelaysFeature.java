package org.ryzen.feature.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.mixin.accessor.LivingEntityAccessor;
import org.ryzen.mixin.accessor.MinecraftAccessor;
import org.ryzen.mixin.accessor.MultiPlayerGameModeAccessor;

@Environment(EnvType.CLIENT)
public final class NoDelaysFeature extends Feature {
   private static final int DEFAULT_JUMP_DELAY = 10;
   private static final int DEFAULT_RIGHT_CLICK_DELAY = 4;
   private static final int DEFAULT_BLOCK_BREAK_DELAY = 5;
   public final BooleanSetting jump = this.register(new BooleanSetting("Jump", true));
   public final BooleanSetting rightClick = this.register(new BooleanSetting("Right Click", false));
   public final BooleanSetting experienceBottlesOnly = this.register(
      new BooleanSetting("Experience Bottles Only", false).visibleWhen(this.rightClick::getValue)
   );
   public final BooleanSetting blockBreak = this.register(new BooleanSetting("Block Break", false));

   public NoDelaysFeature() {
      super("NoDelays", "Removes selected player action delays", FeatureCategory.MISC, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      if (client.player != null) {
         if (this.jump.getValue()) {
            ((LivingEntityAccessor)client.player).setNoJumpDelay(0);
         }

         if (this.rightClick.getValue()
            && (
               !this.experienceBottlesOnly.getValue()
                  || client.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)
                  || client.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)
            )) {
            ((MinecraftAccessor)client).setRightClickDelay(0);
         }

         if (this.blockBreak.getValue() && client.interactionManager != null) {
            ((MultiPlayerGameModeAccessor)client.interactionManager).setDestroyDelay(0);
         }
      }
   }

   @Override
   protected void onDisable() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null) {
         ((LivingEntityAccessor)client.player).setNoJumpDelay(10);
      }

      ((MinecraftAccessor)client).setRightClickDelay(4);
      if (client.interactionManager != null) {
         ((MultiPlayerGameModeAccessor)client.interactionManager).setDestroyDelay(5);
      }
   }
}
