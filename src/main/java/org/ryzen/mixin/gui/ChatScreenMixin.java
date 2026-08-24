package org.ryzen.mixin.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.ryzen.command.CommandManager;
import org.ryzen.utils.irc.IrcChatRouter;
import org.ryzen.utils.text.SensitiveChatMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({ChatScreen.class})
public abstract class ChatScreenMixin extends Screen {
   @Shadow
   protected TextFieldWidget chatField;
   @Shadow
   private ChatInputSuggestor chatInputSuggestor;
   @Unique
   private ButtonWidget privacyButton;

   protected ChatScreenMixin(Text title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void addPrivacyButton(CallbackInfo ci) {
      this.privacyButton = ButtonWidget.builder(privacyLabel(), ignored -> {
         SensitiveChatMask.toggle();
         this.privacyButton.setMessage(privacyLabel());
         this.chatInputSuggestor.refresh();
         this.focusInputAfterClick();
      }).dimensions(this.width - 150, this.height - 40, 146, 20).build();
      this.addDrawableChild(this.privacyButton);
   }

   @Inject(
      method = {"format"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void maskSensitiveText(String text, int offset, CallbackInfoReturnable<OrderedText> cir) {
      OrderedText masked = SensitiveChatMask.format(this.chatField.getText(), text, offset);
      if (masked != null) {
         cir.setReturnValue(masked);
      }
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("RETURN")}
   )
   private void keepKeyboardFocusInInput(KeyInput event, CallbackInfoReturnable<Boolean> cir) {
      this.setFocused(this.chatField);
      this.chatField.setFocused(true);
   }

   @Inject(
      method = {"sendMessage"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void handleClientCommand(String message, boolean addToRecentChat, CallbackInfo ci) {
      ChatScreen screen = (ChatScreen)(Object)this;
      String normalized = screen.normalize(message);
      if (CommandManager.INSTANCE.handleChat(normalized)) {
         if (addToRecentChat) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(normalized);
         }

         ci.cancel();
      } else {
         if (IrcChatRouter.routeOutgoing(normalized)) {
            if (addToRecentChat) {
               MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(normalized);
            }

            ci.cancel();
         }
      }
   }

   @Unique
   private static Text privacyLabel() {
      return Text.literal("Скрыть данные: " + (SensitiveChatMask.isEnabled() ? "True" : "False"));
   }

   @Unique
   private void focusInputAfterClick() {
      MinecraftClient client = MinecraftClient.getInstance();
      client.execute(() -> {
         if (client.currentScreen == (ChatScreen)(Object)this) {
            this.setFocused(this.chatField);
            this.chatField.setFocused(true);
         }
      });
   }
}
