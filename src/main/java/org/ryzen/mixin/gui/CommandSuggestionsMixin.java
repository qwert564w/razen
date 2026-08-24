package org.ryzen.mixin.gui;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import org.ryzen.command.CommandManager;
import org.ryzen.utils.text.SensitiveChatMask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ChatInputSuggestor.class})
public abstract class CommandSuggestionsMixin {
   @Shadow
   @Final
   private Screen owner;
   @Shadow
   @Final
   private TextFieldWidget textField;
   @Shadow
   @Final
   private List<OrderedText> messages;
   @Shadow
   private CompletableFuture<Suggestions> pendingSuggestions;
   @Shadow
   private boolean completingSuggestions;
   @Shadow
   private boolean windowActive;

   @Shadow
   public abstract void show(boolean var1);

   @Shadow
   public abstract void clearWindow();

   @Inject(
      method = {"refresh"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void suggestClientCommands(CallbackInfo ci) {
      if (this.owner instanceof ChatScreen) {
         String text = this.textField.getText();
         if (SensitiveChatMask.hasSecret(text)) {
            ci.cancel();
            this.textField.setSuggestion(null);
            this.clearWindow();
            this.messages.clear();
            this.pendingSuggestions = null;
         } else {
            String prefix = CommandManager.INSTANCE.getPrefix();
            if (!prefix.equals("/") && text.startsWith(prefix)) {
               ci.cancel();
               if (!this.completingSuggestions) {
                  this.textField.setSuggestion(null);
                  this.clearWindow();
                  this.messages.clear();
                  StringReader reader = new StringReader(text);
                  reader.setCursor(prefix.length());
                  CommandDispatcher<Object> dispatcher = CommandManager.INSTANCE.getDispatcher();
                  ParseResults<Object> parse = dispatcher.parse(reader, new Object());
                  int cursor = Math.max(this.textField.getCursor(), prefix.length());
                  CompletableFuture<Suggestions> future = dispatcher.getCompletionSuggestions(parse, cursor);
                  this.pendingSuggestions = future;
                  future.thenRun(() -> {
                     if (this.pendingSuggestions == future && future.isDone() && !future.join().isEmpty() && this.windowActive) {
                        this.show(false);
                     }
                  });
               }
            }
         }
      }
   }
}
