package org.ryzen.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.ryzen.context.RenderContext;
import org.ryzen.pve.economy.ScoreboardBalance;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Theme;
import org.ryzen.utils.render.gui.MsdfFont;
import org.ryzen.utils.render.gui.Render2DUtil;
import org.ryzen.utils.render.gui.TextAlign;
import org.ryzen.utils.render.gui.UiFontStyle;
import org.ryzen.utils.render.gui.UiFonts;

@Environment(EnvType.CLIENT)
public final class AnarchyElement extends HudElement {
   private static final float DESIGN_WIDTH = 201.0F;
   private static final float DESIGN_HEIGHT = 171.0F;
   private static final float ROW_HEIGHT = 31.0F;
   private static final float INNER_PADDING_Y = 2.0F;
   private static final float TEXT_SIZE = 12.0F;
   private static final Identifier TITLE_ICON = figma("anarchy_title_0_837.svg");
   private static final Identifier CLOSE_ICON = figma("anarchy_close_0_835.svg");
   private static final Pattern FORMATTING_CODE = Pattern.compile("(?i)§[0-9A-FK-ORX]");
   private static final Pattern RANK_LABEL = Pattern.compile("(?:rank|ранг|статус|привилеги[яи])", 66);
   private static final Pattern PLAYED_LABEL = Pattern.compile("(?:played|playtime|hours?|наиграно|время в игре)", 66);
   private static final Pattern NUMBER = Pattern.compile("[0-9][0-9\\s.,_]*");
   private final List<AnarchyElement.Row> rows = new ArrayList<>(4);

   public AnarchyElement() {
      super("anarchy", "Anarchy");
   }

   @Override
   protected float defaultX(float unit) {
      return 1645.0F * unit;
   }

   @Override
   protected float defaultY(float unit) {
      return 435.0F * unit;
   }

   @Override
   protected boolean hasHeaderCloseButton() {
      return true;
   }

   @Override
   protected void layout(MinecraftClient mc, float unit) {
      AnarchyElement.Snapshot snapshot = readSnapshot(mc);
      if (!snapshot.hasLiveValue() && showcase(mc)) {
         snapshot = new AnarchyElement.Snapshot("Игрок", "12931", snapshot.currency(), "123", "128ч", false);
      }

      this.rows.clear();
      this.rows.add(new AnarchyElement.Row("Ранк", valueOrDash(snapshot.rank())));
      this.rows.add(new AnarchyElement.Row("Монет", valueOrDash(snapshot.coins())));
      this.rows.add(new AnarchyElement.Row(snapshot.currency().label, valueOrDash(snapshot.currencyValue())));
      this.rows.add(new AnarchyElement.Row("Наиграно", valueOrDash(snapshot.played())));
      this.width = 201.0F * unit;
      this.height = 171.0F * unit;
   }

   @Override
   protected void draw(MinecraftClient mc, float unit) {
      float alpha = this.appearAlpha();
      float[] inner = this.drawCard(unit, alpha, 38.0F, null, null, 5.0F, 6.0F);
      this.drawHeader(unit, alpha);
      float innerX = inner[0];
      float innerY = inner[1] + 2.0F * unit;
      float innerW = inner[2];
      float rowH = 31.0F * unit;
      float labelX = innerX + 12.0F * unit;
      float valueRight = innerX + innerW - 12.0F * unit;
      float textSize = 12.0F * unit;
      MsdfFont font = UiFonts.sfProDisplay();

      for (int index = 0; index < this.rows.size(); index++) {
         AnarchyElement.Row row = this.rows.get(index);
         float centerY = innerY + ((float)index + 0.5F) * rowH;
         float textY = font.centeredTextY(centerY, textSize);
         Render2DUtil.text(labelX, textY, textSize, row.label()).style(UiFontStyle.MEDIUM).color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, alpha)).draw();
         Render2DUtil.text(valueRight, textY, textSize, row.value())
            .style(UiFontStyle.SEMIBOLD)
            .color(ColorUtil.multiplyAlpha(HudPalette.accentAlt(), alpha))
            .align(TextAlign.RIGHT)
            .draw();
         if (index < this.rows.size() - 1) {
            Render2DUtil.rect(innerX + 6.0F * unit, innerY + (float)(index + 1) * rowH, innerW - 12.0F * unit, Math.max(0.5F, unit))
               .color(ColorUtil.multiplyAlpha(DIVIDER_COLOR, alpha))
               .draw();
         }
      }
   }

   private void drawHeader(float unit, float alpha) {
      float centerY = this.y + 38.0F * unit / 2.0F;
      float iconSize = 10.0F * unit;
      float iconX = this.x + 18.0F * unit;
      Render2DUtil.texture(iconX, centerY - iconSize / 2.0F, iconSize, iconSize, TITLE_ICON).color(ColorUtil.multiplyAlpha(HudPalette.accent(), alpha)).draw();
      float titleSize = 12.0F * unit;
      MsdfFont font = UiFonts.sfProDisplay();
      Render2DUtil.text(iconX + iconSize + 8.0F * unit, font.centeredTextY(centerY, titleSize), titleSize, "Anarchy")
         .style(UiFontStyle.MEDIUM)
         .color(ColorUtil.multiplyAlpha(-1, alpha))
         .draw();
      DrawContext graphics = RenderContext.currentGuiGraphicsExtractor();
      if (graphics != null) {
         Matrix3x2fStack pose = graphics.getMatrices();
         Render2DUtil.pushScissor(this.x, this.y, this.width, 38.0F * unit);
         pose.pushMatrix();
         pose.translate(this.x + 137.0F * unit, this.y - 8.0F * unit);
         pose.rotate((float)Math.toRadians(-34.8));
         Render2DUtil.text(0.0F, 0.0F, 70.0F * unit, "14").style(UiFontStyle.SEMIBOLD).color(ColorUtil.multiplyAlpha(HudPalette.accent(), alpha)).draw();
         pose.popMatrix();
         Render2DUtil.popScissor();
      }

      float closeSize = 10.0F * unit;
      Render2DUtil.texture(this.x + this.width - 29.0F * unit, centerY - closeSize / 2.0F, closeSize, closeSize, CLOSE_ICON)
         .color(ColorUtil.multiplyAlpha(-1, alpha))
         .draw();
   }

   private static AnarchyElement.Snapshot readSnapshot(MinecraftClient mc) {
      AnarchyElement.Currency currency = AnarchyElement.Currency.forServer(mc);
      if (mc.player == null) {
         return new AnarchyElement.Snapshot(null, null, currency, null, null, false);
      } else {
         String rank = null;
         String currencyValue = null;
         String played = null;
         AnarchyElement.Currency[] order = currency.order();
         OptionalLong balance = ScoreboardBalance.read(mc.player);
         String coins = balance.isPresent() ? Long.toString(balance.getAsLong()) : null;
         Scoreboard scoreboard = mc.player.getEntityWorld().getScoreboard();
         ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
         if (objective != null) {
            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
               if (!entry.hidden()) {
                  Text name = (Text)(entry.display() == null ? Text.literal(entry.owner()) : entry.display());
                  Team team = scoreboard.getScoreHolderTeam(entry.owner());
                  String line = clean(Team.decorateName(team, name).getString());
                  if (rank == null) {
                     rank = textValue(line, RANK_LABEL);
                  }

                  if (currencyValue == null) {
                     for (AnarchyElement.Currency candidate : order) {
                        String value = numericValue(line, candidate.pattern);
                        if (value != null) {
                           currencyValue = value;
                           currency = candidate;
                           break;
                        }
                     }
                  }

                  if (played == null) {
                     played = textValue(line, PLAYED_LABEL);
                  }
               }
            }
         }

         boolean live = rank != null || coins != null || currencyValue != null || played != null;
         return new AnarchyElement.Snapshot(rank, coins, currency, currencyValue, played, live);
      }
   }

   private static String textValue(String line, Pattern label) {
      Matcher matcher = label.matcher(line);
      if (!matcher.find()) {
         return null;
      } else {
         String tail = line.substring(matcher.end()).replaceFirst("^[\\s:：=|»>\\-—]+", "").strip();
         if (tail.isEmpty()) {
            return null;
         } else {
            int separator = tail.indexOf(124);
            if (separator >= 0) {
               tail = tail.substring(0, separator).strip();
            }

            return tail.length() > 24 ? tail.substring(0, 24).strip() : tail;
         }
      }
   }

   private static String numericValue(String line, Pattern label) {
      String tail = textValue(line, label);
      if (tail == null) {
         return null;
      } else {
         Matcher number = NUMBER.matcher(tail);
         if (!number.find()) {
            return null;
         } else {
            String digits = number.group().replaceAll("[^0-9]", "");
            return digits.isEmpty() ? null : digits;
         }
      }
   }

   private static String clean(String value) {
      return FORMATTING_CODE.matcher(value == null ? "" : value).replaceAll("").replace(' ', ' ').strip();
   }

   private static String valueOrDash(String value) {
      return value != null && !value.isBlank() ? value : "—";
   }

   private static Identifier figma(String file) {
      return Identifier.of("ryzen:textures/hud/figma/" + file);
   }

   @Environment(EnvType.CLIENT)
   private static enum Currency {
      TOKENS("Токенов", "(?:tokens?|токен(?:ы|ов|а)?|жетон(?:ы|ов|а)?)", "funtime", "spookytime"),
      SAPPHIRES("Сапфиров", "(?:sapphires?|сапфир(?:ы|ов|а)?)", "holyworld", "holy-world", "faketime", "space-time"),
      RELICS("Риликов", "(?:relics?|рилик(?:и|ов|а)?|релик(?:и|ов|а)?)", "reallyworld", "really-world", "dreamworld", "dream-world");

      private final String label;
      private final Pattern pattern;
      private final String[] hosts;

      private Currency(String label, String regex, String... hosts) {
         this.label = label;
         this.pattern = Pattern.compile(regex, 66);
         this.hosts = hosts;
      }

      static AnarchyElement.Currency forServer(MinecraftClient mc) {
         ServerInfo server = mc.getCurrentServerEntry();
         if (server == null) {
            return TOKENS;
         } else {
            String identity = ((server.name == null ? "" : server.name) + " " + (server.address == null ? "" : server.address)).toLowerCase(Locale.ROOT);

            for (AnarchyElement.Currency currency : values()) {
               if (currency.matches(identity)) {
                  return currency;
               }
            }

            return TOKENS;
         }
      }

      AnarchyElement.Currency[] order() {
         AnarchyElement.Currency[] ordered = new AnarchyElement.Currency[values().length];
         ordered[0] = this;
         int next = 1;

         for (AnarchyElement.Currency currency : values()) {
            if (currency != this) {
               ordered[next++] = currency;
            }
         }

         return ordered;
      }

      private boolean matches(String identity) {
         for (String host : this.hosts) {
            if (identity.contains(host)) {
               return true;
            }
         }

         return false;
      }
   }

   @Environment(EnvType.CLIENT)
   private static record Row(String label, String value) {
   }

   @Environment(EnvType.CLIENT)
   private static record Snapshot(String rank, String coins, AnarchyElement.Currency currency, String currencyValue, String played, boolean hasLiveValue) {
   }
}
