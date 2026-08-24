package org.ryzen.utils.text;

import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;

@Environment(EnvType.CLIENT)
public final class SensitiveChatMask {
   private static final Set<String> COMMANDS = Set.of("/login", "/l", "/pass", "/reg", "/register", "/changepassword", "/cp");
   private static boolean enabled;

   private SensitiveChatMask() {
   }

   public static boolean isEnabled() {
      return enabled;
   }

   public static void toggle() {
      enabled = !enabled;
   }

   public static boolean hasSecret(String input) {
      return enabled && secretStart(input) >= 0;
   }

   public static OrderedText format(String fullInput, String visibleText, int visibleOffset) {
      int secretStart = secretStart(fullInput);
      if (enabled && secretStart >= 0 && visibleOffset + visibleText.length() > secretStart) {
         Style hidden = Style.EMPTY.withObfuscated(true);
         return sink -> {
            int index = 0;

            while (index < visibleText.length()) {
               int codePoint = visibleText.codePointAt(index);
               Style style = visibleOffset + index >= secretStart ? hidden : Style.EMPTY;
               if (!sink.accept(index, style, codePoint)) {
                  return false;
               }

               index += Character.charCount(codePoint);
            }

            return true;
         };
      } else {
         return null;
      }
   }

   private static int secretStart(String input) {
      if (input != null && !input.isEmpty() && input.charAt(0) == '/') {
         int commandEnd = 0;

         while (commandEnd < input.length() && !Character.isWhitespace(input.charAt(commandEnd))) {
            commandEnd++;
         }

         String command = input.substring(0, commandEnd).toLowerCase(Locale.ROOT);
         if (!COMMANDS.contains(command)) {
            return -1;
         } else {
            int argumentStart = commandEnd;

            while (argumentStart < input.length() && Character.isWhitespace(input.charAt(argumentStart))) {
               argumentStart++;
            }

            return argumentStart < input.length() ? argumentStart : -1;
         }
      } else {
         return -1;
      }
   }
}
