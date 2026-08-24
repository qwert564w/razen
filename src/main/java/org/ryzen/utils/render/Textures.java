package org.ryzen.utils.render;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class Textures {
   private static final List<Identifier> ALL = new ArrayList<>();
   public static final Identifier TARGET = texture("target.png");
   public static final Identifier TARGET_SKULL = texture("target_skull.png");

   private Textures() {
   }

   public static List<Identifier> all() {
      touch(
         Textures.Logos.BOOT,
         Textures.Hud.ARROW_OUTLINE,
         Textures.Shader.BLOOM,
         Textures.Header.SEARCH,
         Textures.Icons.BOXES,
         Textures.ClickGui.AVATAR,
         Textures.Title.LOGO,
         Textures.AltManager.LOGO
      );
      synchronized (ALL) {
         return List.copyOf(ALL);
      }
   }

   private static void touch(Identifier... constants) {
   }

   private static Identifier svg(String menuPath) {
      return texture("menu/" + menuPath + ".svg");
   }

   private static Identifier texture(String path) {
      return register(Identifier.of("ryzen:textures/" + path));
   }

   private static Identifier register(Identifier id) {
      synchronized (ALL) {
         ALL.add(id);
         return id;
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class AltManager {
      public static final Identifier ACCENT_SHAPE = Textures.texture("gui/alt_manager/icons/accent_shape.svg");
      public static final Identifier ADD = Textures.texture("gui/alt_manager/icons/add.svg");
      public static final Identifier BACK = Textures.texture("gui/alt_manager/icons/back.svg");
      public static final Identifier CARD_STAR_ORANGE = Textures.texture("gui/alt_manager/icons/card_star_orange.svg");
      public static final Identifier CARD_STAR_OUTLINE = Textures.texture("gui/alt_manager/icons/card_star_outline.svg");
      public static final Identifier CARD_STAR_WHITE = Textures.texture("gui/alt_manager/icons/card_star_white.svg");
      public static final Identifier DATE = Textures.texture("gui/alt_manager/icons/date.svg");
      public static final Identifier DELETE = Textures.texture("gui/alt_manager/icons/delete.svg");
      public static final Identifier FAVORITE_LARGE = Textures.texture("gui/alt_manager/icons/favorite_large.svg");
      public static final Identifier LOGO = Textures.texture("gui/alt_manager/icons/logo.svg");
      public static final Identifier PROFILE = Textures.texture("gui/alt_manager/icons/profile.svg");
      public static final Identifier RANDOM = Textures.texture("gui/alt_manager/icons/random.svg");
      public static final Identifier SEARCH = Textures.texture("gui/alt_manager/icons/search.svg");
      public static final Identifier SELECT = Textures.texture("gui/alt_manager/icons/select.svg");
      public static final Identifier SELECTED_CHECK = Textures.texture("gui/alt_manager/icons/selected_check.svg");
      public static final Identifier USER_INPUT = Textures.texture("gui/alt_manager/icons/user_input.svg");

      private AltManager() {
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class ClickGui {
      public static final Identifier AVATAR = Textures.texture("menu/clickgui/avatar.png");
      public static final Identifier APP_ICON = Textures.texture("gui/icon_32.png");
      public static final Identifier ENDER_PEARL = Textures.texture("menu/clickgui/ender_pearl.png");

      private ClickGui() {
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Header {
      public static final Identifier SEARCH = Textures.svg("header/search");
      public static final Identifier CHEVRON_LEFT = Textures.svg("header/chevron_left");
      public static final Identifier CHEVRON_RIGHT = Textures.svg("header/chevron_right");
      public static final Identifier SETTINGS = Textures.svg("header/settings");
      public static final Identifier FRIENDS = Textures.svg("header/friends");
      public static final Identifier PROFILE_ADD = Textures.svg("header/profile_add");
      public static final Identifier DOCUMENT = Textures.svg("header/document");
      public static final Identifier HUD = Textures.svg("header/hud");
      public static final Identifier DEV_AVATAR = Textures.texture("menu/header/dev.png");

      private Header() {
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Hud {
      public static final Identifier ARROW_OUTLINE = Textures.texture("hud/arrow.png");
      public static final Identifier ARROW_FILLED = Textures.texture("hud/filled_arrow.png");

      private Hud() {
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Icons {
      public static final Identifier AUTOBUY = Textures.svg("icons/autobuy");
      public static final Identifier BOXES = Textures.svg("icons/boxes");
      public static final Identifier BRAIN = Textures.svg("icons/brain");
      public static final Identifier CALLOUT_POINTER = Textures.svg("icons/callout_pointer");
      public static final Identifier CHECK = Textures.svg("icons/check");
      public static final Identifier CHEVRON_DOWN = Textures.svg("icons/chevron_down");
      public static final Identifier CHEVRON_UP = Textures.svg("icons/chevron_up");
      public static final Identifier CHEVRONS_LEFT_RIGHT = Textures.svg("icons/chevrons_left_right");
      public static final Identifier CHEVRONS_LEFT_RIGHT_ELLIPSIS = Textures.svg("icons/chevrons_left_right_ellipsis");
      public static final Identifier CIRCLE_PLUS = Textures.svg("icons/circle_plus");
      public static final Identifier COMMAND = Textures.svg("icons/command");
      public static final Identifier DELETE = Textures.svg("icons/delete");
      public static final Identifier DELETE_LEFT = Textures.svg("icons/delete_left");
      public static final Identifier DICES = Textures.svg("icons/dices");
      public static final Identifier EYE = Textures.svg("icons/eye");
      public static final Identifier FILTER_LINES = Textures.svg("icons/filter_lines");
      public static final Identifier FOLDER = Textures.svg("icons/folder");
      public static final Identifier UPLOAD = Textures.svg("icons/upload");
      public static final Identifier GAMEPAD = Textures.svg("icons/gamepad_2");
      public static final Identifier GIFT = Textures.svg("icons/gift");
      public static final Identifier HARD_DRIVE = Textures.svg("icons/hard_drive");
      public static final Identifier KEYBOARD = Textures.svg("icons/keyboard");
      public static final Identifier MOVE_3D = Textures.svg("icons/move_3d");
      public static final Identifier OPTION = Textures.svg("icons/option");
      public static final Identifier PERSON_STANDING = Textures.svg("icons/person_standing");
      public static final Identifier PALETTE = Textures.svg("icons/palette");
      public static final Identifier GLOBE = Textures.svg("icons/globe");
      public static final Identifier PIN = Textures.svg("icons/pin");
      public static final Identifier POINTER_CLICK = Textures.svg("icons/pointer_click");
      public static final Identifier PLUS = Textures.svg("icons/plus");
      public static final Identifier REFRESH_CCW = Textures.svg("icons/refresh_ccw");
      public static final Identifier SCAN_HEART = Textures.svg("icons/scan_heart");
      public static final Identifier SHIRT = Textures.svg("icons/shirt");
      public static final Identifier SPARKLES = Textures.svg("icons/sparkles");
      public static final Identifier SWORDS = Textures.svg("icons/swords");
      public static final Identifier TRIANGLE_ALERT = Textures.svg("icons/triangle_alert");
      public static final Identifier USER_ROUND = Textures.svg("icons/user_round");
      public static final Identifier USER_ROUND_CHECK = Textures.svg("icons/user_round_check");
      public static final Identifier USER_ROUND_PLUS = Textures.svg("icons/user_round_plus");
      public static final Identifier X = Textures.svg("icons/x");

      private Icons() {
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Logos {
      public static final Identifier BOOT = Textures.texture("menu/logo_boot.svg");
      public static final Identifier BOLT = Textures.texture("hud/logo_bolt.svg");
      public static final Identifier AUTOBUY = Textures.svg("autobuy/mark");

      private Logos() {
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Shader {
      public static final Identifier BLOOM = Textures.texture("shader/bloom.png");
      public static final Identifier JUMP_FREQUENCY = Textures.texture("shader/jump_frequency.png");

      private Shader() {
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Title {
      public static final Identifier LOGO = Textures.svg("title/logo");
      public static final Identifier AVATAR = Textures.texture("menu/title/avatar.png");
      public static final Identifier SINGLEPLAYER_BG = Textures.texture("menu/title/singleplayer_background.png");
      public static final Identifier SINGLEPLAYER = Textures.svg("title/singleplayer");
      public static final Identifier MULTIPLAYER = Textures.svg("title/multiplayer");
      public static final Identifier ACCOUNTS = Textures.svg("title/accounts");
      public static final Identifier OPTIONS = Textures.svg("title/options");
      public static final Identifier QUIT = Textures.svg("title/quit");
      public static final Identifier PROFILE = Textures.svg("title/profile");
      public static final Identifier CHEVRON = Textures.svg("title/chevron");
      public static final Identifier DISCORD = Textures.svg("title/discord");
      public static final Identifier TELEGRAM = Textures.svg("title/telegram");

      private Title() {
      }
   }
}
