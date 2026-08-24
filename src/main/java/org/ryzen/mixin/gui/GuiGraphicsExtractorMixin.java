package org.ryzen.mixin.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.ryzen.utils.text.NameProtectUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin({DrawContext.class})
public abstract class GuiGraphicsExtractorMixin {
   @ModifyVariable(
      method = {"drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V", "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V", "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private String protectString(String text) {
      return NameProtectUtil.protect(text);
   }

   @ModifyVariable(
      method = {"drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V", "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V", "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V", "drawTextWithBackground(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIII)V", "drawTooltip(Lnet/minecraft/text/Text;II)V", "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;II)V", "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IILnet/minecraft/util/Identifier;)V"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private Text protectComponent(Text component) {
      return NameProtectUtil.protect(component);
   }

   @ModifyVariable(
      method = {"drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V", "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V", "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private OrderedText protectFormattedCharSequence(OrderedText sequence) {
      return NameProtectUtil.protect(sequence);
   }

   @ModifyVariable(
      method = {"drawWrappedTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/StringVisitable;IIII)V", "drawWrappedText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/StringVisitable;IIIIZ)V"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private StringVisitable protectFormattedText(StringVisitable text) {
      return NameProtectUtil.protect(text);
   }
}
