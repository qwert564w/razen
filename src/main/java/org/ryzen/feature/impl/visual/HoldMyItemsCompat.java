package org.ryzen.feature.impl.visual;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class HoldMyItemsCompat {
   private static final long ATTACK_STYLE_WINDOW_NANOS = 250000000L;
   private static final String ACCESSOR_CLASS = "com.holdmylua.source.access.LivingEntityAccessor";
   private static final String RESET_MAIN_HAND_SWING = "hMI5_0$resetMainHandSwing";
   private static final Method RESET_METHOD = findResetMethod();
   private static int auraAttackIndex;
   private static long forceGroundStyleUntil;

   private HoldMyItemsCompat() {
   }

   public static void beginMainHandAttack(ClientPlayerEntity player) {
      if (player != null && HoldMyItemsFeature.isActive() && RESET_METHOD != null && RESET_METHOD.getDeclaringClass().isInstance(player)) {
         try {
            RESET_METHOD.invoke(player, false);
            boolean groundStyle = auraAttackIndex++ % 3 != 2;
            forceGroundStyleUntil = groundStyle ? System.nanoTime() + 250000000L : 0L;
         } catch (InvocationTargetException | IllegalAccessException var2) {
         }
      }
   }

   public static boolean shouldUseGroundAttackStyle(AbstractClientPlayerEntity player) {
      return player != null && player == MinecraftClient.getInstance().player && System.nanoTime() < forceGroundStyleUntil;
   }

   private static Method findResetMethod() {
      try {
         Class<?> accessor = Class.forName("com.holdmylua.source.access.LivingEntityAccessor", false, HoldMyItemsCompat.class.getClassLoader());
         return accessor.getMethod("hMI5_0$resetMainHandSwing", boolean.class);
      } catch (NoSuchMethodException | ClassNotFoundException var1) {
         return null;
      }
   }
}
