package org.ryzen.utils.text;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.misc.NameProtectFeature;

@Environment(EnvType.CLIENT)
public final class NameProtectUtil {
   private static final String FEATURE_NAME = "NameProtect";

   private NameProtectUtil() {
   }

   public static String protect(String text) {
      if (text != null && !text.isEmpty()) {
         String realName = realName();
         String fakeName = fakeName();
         return !realName.isEmpty() && !fakeName.isEmpty() && !realName.equals(fakeName) && text.contains(realName) ? text.replace(realName, fakeName) : text;
      } else {
         return text;
      }
   }

   public static Text protect(Text component) {
      if (component == null) {
         return null;
      } else {
         String protectedText = protect(component.getString());
         return (Text)(protectedText.equals(component.getString()) ? component : Text.literal(protectedText).fillStyle(component.getStyle()));
      }
   }

   public static StringVisitable protect(StringVisitable text) {
      if (text == null) {
         return null;
      } else if (text instanceof Text component) {
         return protect(component);
      } else {
         String protectedText = protect(text.getString());
         return protectedText.equals(text.getString()) ? text : StringVisitable.plain(protectedText);
      }
   }

   public static OrderedText protect(OrderedText sequence) {
      if (sequence == null) {
         return null;
      } else {
         StringBuilder text = new StringBuilder();
         sequence.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
         });
         String original = text.toString();
         String protectedText = protect(original);
         return protectedText.equals(original) ? sequence : OrderedText.styledForwardsVisitedString(protectedText, Style.EMPTY);
      }
   }

   private static String realName() {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      if (minecraft == null) {
         return "";
      } else {
         return minecraft.getSession() != null && minecraft.getSession().getUsername() != null ? minecraft.getSession().getUsername() : "";
      }
   }

   private static String fakeName() {
      if (FeatureManager.INSTANCE.getFeature("NameProtect") instanceof NameProtectFeature nameProtect && nameProtect.isEnabled()) {
         String value = nameProtect.name.getValue();
         if (value == null) {
            return "";
         }

         return value.trim();
      }

      return "";
   }
}
