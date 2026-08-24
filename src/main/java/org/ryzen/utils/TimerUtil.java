package org.ryzen.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.ryzen.injection.TimerAccess;

@Environment(EnvType.CLIENT)
public final class TimerUtil {
   private TimerUtil() {
   }

   public static void setTimer(float multiplier) {
      if (MinecraftClient.getInstance().getRenderTickCounter() instanceof TimerAccess timer) {
         timer.ryzen$setSpeedMultiplier(multiplier);
      }
   }

   public static void resetTimer() {
      setTimer(1.0F);
   }

   public static float getTimer() {
      return MinecraftClient.getInstance().getRenderTickCounter() instanceof TimerAccess timer ? timer.ryzen$getSpeedMultiplier() : 1.0F;
   }
}
