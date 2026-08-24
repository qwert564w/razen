package org.ryzen.mixin.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin({Window.class})
public abstract class WindowMixin {
   @ModifyArg(
      method = {"setTitle"},
      at = @At(
         value = "INVOKE",
         target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowTitle(JLjava/lang/CharSequence;)V"
      ),
      index = 1
   )
   private CharSequence ryzen$customTitle(CharSequence title) {
      return "Ryzen Client 1.21.11";
   }
}
