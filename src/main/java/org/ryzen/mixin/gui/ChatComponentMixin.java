package org.ryzen.mixin.gui;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.ryzen.feature.impl.misc.ChatHelperFeature;
import org.ryzen.utils.text.NameProtectUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ChatHud.class})
public abstract class ChatComponentMixin {
   @Shadow
   @Final
   private List<ChatHudLine> messages;
   @Unique
   private String ryzen$lastCollapsedText;
   @Unique
   private int ryzen$repeatCount;

   @Shadow
   protected abstract void refresh();

   @ModifyVariable(
      method = {"addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private Text ryzen$rewriteMessage(Text message) {
      Text protectedMessage = NameProtectUtil.protect(message);
      int window = ChatHelperFeature.antiSpamWindow();
      if (window <= 0) {
         this.ryzen$lastCollapsedText = null;
         this.ryzen$repeatCount = 0;
         return protectedMessage;
      } else {
         String text = protectedMessage.getString();
         int limit = Math.min(window, this.messages.size());

         for (int index = 0; index < limit; index++) {
            if (this.ryzen$matchesCollapsed(this.messages.get(index).content().getString(), text)) {
               this.messages.remove(index);
               this.ryzen$repeatCount = text.equals(this.ryzen$lastCollapsedText) ? this.ryzen$repeatCount + 1 : 2;
               this.ryzen$lastCollapsedText = text;
               this.refresh();
               return ChatHelperFeature.withRepeatCounter(protectedMessage, this.ryzen$repeatCount);
            }
         }

         this.ryzen$lastCollapsedText = null;
         this.ryzen$repeatCount = 0;
         return protectedMessage;
      }
   }

   @Unique
   private boolean ryzen$matchesCollapsed(String existing, String incoming) {
      if (existing.equals(incoming)) {
         return true;
      } else {
         int marker = existing.lastIndexOf(" (x");
         return marker > 0 && existing.endsWith(")") && existing.substring(0, marker).equals(incoming);
      }
   }

   @Inject(
      method = {"clear"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ryzen$keepHistory(boolean clearSentHistory, CallbackInfo ci) {
      if (!clearSentHistory && ChatHelperFeature.shouldKeepHistory()) {
         ci.cancel();
      }
   }
}
