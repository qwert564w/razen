package org.ryzen.utils.cosmetics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.ryzen.context.MinecraftContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class FiguraBridge {
   private static final Logger LOGGER = LoggerFactory.getLogger(FiguraBridge.class);
   private static final String MOD_ID = "figura";
   private static final String AVATAR_MANAGER = "org.figuramc.figura.avatar.AvatarManager";
   private static final String CONFIGS = "org.figuramc.figura.config.Configs";
   private static Boolean available;
   private static Method loadLocalAvatar;
   private static Method clearAvatars;
   private static String appliedId = "";

   private FiguraBridge() {
   }

   public static boolean isAvailable() {
      if (available == null) {
         available = resolve();
      }

      return available;
   }

   private static boolean resolve() {
      if (!FabricLoader.getInstance().isModLoaded("figura")) {
         return false;
      } else {
         try {
            Class<?> manager = Class.forName("org.figuramc.figura.avatar.AvatarManager");
            loadLocalAvatar = manager.getMethod("loadLocalAvatar", Path.class);
            clearAvatars = manager.getMethod("clearAvatars", UUID.class);
            return true;
         } catch (Exception var1) {
            loadLocalAvatar = null;
            clearAvatars = null;
            return false;
         }
      }
   }

   public static boolean disablePopupMenu() {
      if (!FabricLoader.getInstance().isModLoaded("figura")) {
         return false;
      } else {
         try {
            Object popupButton = Class.forName("org.figuramc.figura.config.Configs").getField("POPUP_BUTTON").get(null);
            if (popupButton.getClass().getField("keyBind").get(popupButton) instanceof KeyBinding mapping && !mapping.isUnbound()) {
               mapping.setBoundKey(InputUtil.UNKNOWN_KEY);
               KeyBinding.updateKeysByCode();
               return true;
            }

            return false;
         } catch (Exception var3) {
            LOGGER.debug("Could not unbind the Figura popup menu", var3);
            return false;
         }
      }
   }

   public static boolean disableFirstPersonMatrices() {
      if (!FabricLoader.getInstance().isModLoaded("figura")) {
         return false;
      } else {
         try {
            Object config = Class.forName("org.figuramc.figura.config.Configs").getField("FIRST_PERSON_MATRICES").get(null);
            Field value = config.getClass().getField("value");
            if (Boolean.FALSE.equals(value.get(config))) {
               return false;
            } else {
               value.set(config, Boolean.FALSE);
               return true;
            }
         } catch (Exception var2) {
            LOGGER.debug("Could not turn off Figura's first-person matrices", var2);
            return false;
         }
      }
   }

   public static String appliedId() {
      return appliedId;
   }

   public static boolean isApplied(CosmeticEntry entry) {
      return entry != null && entry.id().equals(appliedId);
   }

   public static boolean apply(CosmeticEntry entry) {
      if (entry != null && isAvailable()) {
         try {
            CosmeticFirstPerson.repair(entry.folder());
            if (entry.kind() != CosmeticEntry.Kind.WEAPON) {
               CosmeticFirstPerson.installHide(entry.folder());
            }

            loadLocalAvatar.invoke(null, entry.folder());
            appliedId = entry.id();
            return true;
         } catch (Exception var2) {
            LOGGER.error("Failed to apply cosmetic {}", entry.id(), var2);
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean clear() {
      appliedId = "";
      if (isAvailable() && MinecraftContext.mc.player != null) {
         try {
            clearAvatars.invoke(null, MinecraftContext.mc.player.getUuid());
            return true;
         } catch (Exception var1) {
            LOGGER.error("Failed to clear the applied cosmetic", var1);
            return false;
         }
      } else {
         return false;
      }
   }
}
