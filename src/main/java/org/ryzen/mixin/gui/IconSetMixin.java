package org.ryzen.mixin.gui;

import java.io.InputStream;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Icons;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({Icons.class})
public class IconSetMixin {
   @Inject(
      method = {"getIcons"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetStandardIcons(ResourcePack resources, CallbackInfoReturnable<List<InputSupplier<InputStream>>> cir) {
      try {
         InputSupplier<InputStream> icon16 = () -> IconSetMixin.class.getResourceAsStream("/assets/ryzen/textures/gui/icon_16.png");
         InputSupplier<InputStream> icon32 = () -> IconSetMixin.class.getResourceAsStream("/assets/ryzen/textures/gui/icon_32.png");
         if (icon16.get() != null && icon32.get() != null) {
            cir.setReturnValue(List.of(icon16, icon32));
         }
      } catch (Exception var5) {
         System.err.println("[Ryzen] Failed to load custom standard icons: " + var5.getMessage());
      }
   }

   @Inject(
      method = {"getMacIcon"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetMacIcon(ResourcePack resources, CallbackInfoReturnable<InputSupplier<InputStream>> cir) {
      try {
         InputSupplier<InputStream> macIcon = () -> IconSetMixin.class.getResourceAsStream("/assets/ryzen/textures/gui/icon_mac.png");
         if (macIcon.get() != null) {
            cir.setReturnValue(macIcon);
         }
      } catch (Exception var4) {
         System.err.println("[Ryzen] Failed to load custom macOS app icon: " + var4.getMessage());
      }
   }
}
