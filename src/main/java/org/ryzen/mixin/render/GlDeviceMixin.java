package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.ShaderSourceGetter;
import org.ryzen.utils.render.ShaderFallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(
   targets = {"net/minecraft/client/gl/GlBackend"}
)
public class GlDeviceMixin {
   @ModifyVariable(
      method = {"compileShader"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private ShaderSourceGetter wrapShaderSource(ShaderSourceGetter source) {
      return (shaderId, shaderType) -> {
         String result = null;

         try {
            result = source.get(shaderId, shaderType);
         } catch (Throwable var5) {
         }

         if (result == null) {
            result = ShaderFallback.load(shaderId, shaderType);
         }

         return result;
      };
   }
}
