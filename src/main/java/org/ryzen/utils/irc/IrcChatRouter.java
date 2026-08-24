package org.ryzen.utils.irc;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class IrcChatRouter {
   private static final int IRC_TAG_COLOR = -11141121;
   private static final int NAME_COLOR = -1;
   private static final int TEXT_COLOR = -2236963;
   private static final int INFO_COLOR = -6643546;
   private static final int WARN_COLOR = -43691;

   private IrcChatRouter() {
   }

   public static boolean routeOutgoing(String message) {
      IrcService irc = IrcService.INSTANCE;
      if (irc.isChatMode() && message != null && !message.isBlank()) {
         switch (irc.send(message)) {
            case SENT:
               List<IrcService.Message> history = irc.history();
               if (!history.isEmpty()) {
                  display(history.get(history.size() - 1));
               }

               return true;
            case MUTED:
               long minutes = (irc.muteRemainingMs() + 59999L) / 60000L;
               systemLine("You are muted for spam. Wait " + minutes + " min.", true);
               return true;
            case TOO_FAST:
               systemLine("Slow down - one message per second.", true);
               return true;
            case DISCONNECTED:
               systemLine("IRC is disconnected. Use .irc connect", true);
               return true;
            default:
               return false;
         }
      } else {
         return false;
      }
   }

   public static void display(IrcService.Message message) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.inGameHud != null && message != null) {
         mc.inGameHud.getChatHud().addMessage(build(message));
      }
   }

   public static MutableText build(IrcService.Message message) {
      MutableText line = Text.empty();
      line.append(colored("IRC ", -11141121));
      line.append(colored("[" + message.role().displayName() + "] ", message.role().color()));
      line.append(colored(message.name() + ": ", -1));
      line.append(colored(message.text(), -2236963));
      return line;
   }

   public static void systemLine(String text, boolean warning) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.inGameHud != null && text != null && !text.isBlank()) {
         mc.inGameHud.getChatHud().addMessage(colored("IRC ", -11141121).append(colored(text, warning ? -43691 : -6643546)));
      }
   }

   private static MutableText colored(String text, int argb) {
      return Text.literal(text).fillStyle(Style.EMPTY.withColor(argb & 16777215));
   }
}
