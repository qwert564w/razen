package ru.fiw.proxyserver;

import java.util.ArrayList;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public final class GuiProxy extends Screen {
   private static final int WIDTH = 200;
   private static final int HEIGHT = 20;
   private final Screen parent;
   private final TestPing testPing = new TestPing();
   private boolean socks4;
   private boolean enabled;
   private String savedIp;
   private String savedUser;
   private String savedPassword;
   private String message = "";
   private TextFieldWidget ipPort;
   private TextFieldWidget username;
   private TextFieldWidget password;
   private TextFieldWidget nameInput;
   private CheckboxWidget enabledCheck;
   private int startY;
   private int centerX;

   public GuiProxy(Screen parent) {
      super(Text.literal("Proxy Settings"));
      this.parent = parent;
      Config.loadConfig();
      Proxy current = ProxyServer.proxy.copy();
      this.socks4 = current.type == Proxy.ProxyType.SOCKS4;
      this.enabled = ProxyServer.proxyEnabled;
      this.savedIp = current.ipPort;
      this.savedUser = current.username;
      this.savedPassword = current.password;
   }

   protected void init() {
      this.centerX = this.width / 2;
      this.startY = Math.max(28, this.height / 2 - 90);
      int x = this.centerX - 100;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Type: " + (this.socks4 ? "Socks 4" : "Socks 5")), button -> {
         this.captureFields();
         this.socks4 = !this.socks4;
         this.clearAndInit();
      }).dimensions(x, this.startY, 200, 20).build());
      this.ipPort = this.field(x, this.startY + 24, "e.g. 125.1.34.1:2555", this.savedIp);
      this.username = this.field(x, this.startY + 48, this.socks4 ? "e.g. UserID123" : "e.g. username123", this.savedUser);
      if (!this.socks4) {
         this.password = this.field(x, this.startY + 72, "e.g. myPassword123", this.savedPassword);
      } else {
         this.password = null;
      }

      int enabledY = this.startY + (this.socks4 ? 76 : 100);
      this.enabledCheck = CheckboxWidget.builder(Text.literal("Enable Proxy"), this.textRenderer)
         .pos(x, enabledY)
         .checked(this.enabled)
         .callback((checkbox, checked) -> this.enabled = checked)
         .build();
      this.addDrawableChild(this.enabledCheck);
      int actionY = this.startY + (this.socks4 ? 105 : 129);
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), button -> this.apply()).dimensions(x, actionY, 64, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Test"), button -> this.test()).dimensions(x + 68, actionY, 64, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> this.close()).dimensions(x + 136, actionY, 64, 20).build());
      int saveY = this.startY + (this.socks4 ? 132 : 156);
      this.nameInput = this.field(x, saveY, "Name", "", 120);
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.savePreset()).dimensions(x + 124, saveY, 76, 20).build());
      int presetY = this.startY + (this.socks4 ? 160 : 184);

      for (Entry<String, Proxy> entry : new ArrayList<>(Config.accounts.entrySet())) {
         String name = entry.getKey();
         Proxy preset = entry.getValue().copy();
         this.addDrawableChild(ButtonWidget.builder(Text.literal(name), button -> this.loadPreset(preset)).dimensions(x, presetY, 175, 20).build());
         this.addDrawableChild(ButtonWidget.builder(Text.literal("X"), button -> {
            Config.removeAccount(name);
            this.clearAndInit();
         }).dimensions(x + 180, presetY, 20, 20).build());
         presetY += 22;
      }
   }

   private TextFieldWidget field(int x, int y, String hint, String value) {
      return this.field(x, y, hint, value, 200);
   }

   private TextFieldWidget field(int x, int y, String hint, String value, int width) {
      TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, width, 20, Text.empty());
      field.setMaxLength(256);
      field.setPlaceholder(Text.literal(hint).formatted(Formatting.DARK_GRAY));
      field.setText(value == null ? "" : value);
      this.addDrawableChild(field);
      return field;
   }

   private void captureFields() {
      if (this.ipPort != null) {
         this.savedIp = this.ipPort.getText().trim();
      }

      if (this.username != null) {
         this.savedUser = this.username.getText();
      }

      if (this.password != null) {
         this.savedPassword = this.password.getText();
      }

      if (this.enabledCheck != null) {
         this.enabled = this.enabledCheck.isChecked();
      }
   }

   private Proxy editedProxy() {
      this.captureFields();
      return new Proxy(this.socks4, this.savedIp, this.savedUser, this.savedPassword);
   }

   private void apply() {
      Proxy proxy = this.editedProxy();
      if (this.enabled && !proxy.isUsable()) {
         this.message = Formatting.RED + "Use host:port";
      } else {
         ProxyServer.proxy = proxy;
         ProxyServer.proxyEnabled = this.enabled;
         Config.saveConfig();
         this.client.setScreen(this.parent);
      }
   }

   private void test() {
      Proxy proxy = this.editedProxy();
      if (!proxy.isUsable()) {
         this.message = Formatting.RED + "Use host:port";
      } else {
         this.message = "";
         this.testPing.run("mc.hypixel.net", 25565, proxy);
      }
   }

   private void savePreset() {
      Proxy proxy = this.editedProxy();
      if (!proxy.isUsable()) {
         this.message = Formatting.RED + "Use host:port";
      } else {
         String name = this.nameInput.getText().trim();
         if (name.isEmpty()) {
            name = "Proxy_" + System.currentTimeMillis();
         }

         Config.putAccount(name, proxy);
         this.message = Formatting.GREEN + "Saved " + name;
         this.clearAndInit();
      }
   }

   private void loadPreset(Proxy preset) {
      this.captureFields();
      this.socks4 = preset.type == Proxy.ProxyType.SOCKS4;
      this.savedIp = preset.ipPort;
      this.savedUser = preset.username;
      this.savedPassword = preset.password;
      this.message = "";
      this.clearAndInit();
   }

   public void close() {
      this.client.setScreen(this.parent);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.centerX, this.startY - 20, -1);
      String status = this.message.isEmpty() ? this.testPing.state : this.message;
      if (status != null && !status.isEmpty()) {
         context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.centerX, this.startY - 9, -1);
      }
   }
}
