package org.ryzen.feature.impl.pve;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Optional;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.text.Text;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.DisconnectEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.feature.setting.TextSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;

@Environment(EnvType.CLIENT)
public final class AutoAuthFeature extends PveFeature {
   public static final String MODE_GENERATED = "Generated";
   public static final String MODE_CUSTOM = "Custom Password";
   private static final long RESOURCE_RETRY_MILLIS = 250L;
   private static final long CREDENTIAL_RETRY_MILLIS = 5000L;
   private static final Pattern SAFE_PASSWORD = Pattern.compile("[A-Za-z0-9!@#$%^&*()_+\\-=.,:?~]{4,64}");
   public final ModeSetting passwordMode = this.register(new ModeSetting("Mode", "Generated", "Generated", "Custom Password"));
   public final TextSetting customPassword = this.register(this.customPasswordSetting());
   public final NumberSetting cooldown = this.register(new NumberSetting("Cooldown", 3.0, 1.0, 15.0, 0.5, " s"));
   private final AutoAuthCredentialStore credentialStore;
   private final ArrayDeque<AutoAuthPromptParser.Prompt> pendingPrompts = new ArrayDeque<>();
   private final EnumSet<AutoAuthPromptParser.Prompt> handledPrompts = EnumSet.noneOf(AutoAuthPromptParser.Prompt.class);
   private ClientPlayNetworkHandler connection;
   private long nextActionAt;

   public AutoAuthFeature() {
      this(new AutoAuthCredentialStore());
   }

   AutoAuthFeature(AutoAuthCredentialStore credentialStore) {
      super("AutoAuth", "Automatically responds to server login and registration prompts", -1, AutomationPriority.FEATURE);
      this.credentialStore = credentialStore;
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         String text = incomingText(event.getPacket());
         Optional<AutoAuthPromptParser.Prompt> prompt = AutoAuthPromptParser.parse(text);
         if (!prompt.isEmpty()) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
               if (this.isEnabled()) {
                  this.enqueue(client, prompt.get());
               }
            });
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      this.syncConnection(client);
      if (client.player != null && client.world != null && client.getCurrentServerEntry() != null && this.connection != null) {
         AutoAuthPromptParser.Prompt prompt = this.nextPrompt();
         if (prompt != null) {
            long now = System.currentTimeMillis();
            if (now >= this.nextActionAt) {
               AutoAuthCredentialStore.Scope scope = scope(client);

               Optional<String> password;
               try {
                  password = this.resolvePassword(scope);
               } catch (GeneralSecurityException | IOException var17) {
                  this.nextActionAt = now + 5000L;
                  return;
               }

               if (password.isEmpty()) {
                  this.nextActionAt = now + 5000L;
               } else if (!this.claim(AutomationResource.CHAT, new AutomationResource[0])) {
                  this.nextActionAt = now + 250L;
               } else {
                  String command = command(prompt, password.get());
                  boolean sent = false;

                  try {
                     client.player.networkHandler.sendChatCommand(command);
                     sent = true;
                  } catch (RuntimeException var15) {
                     this.nextActionAt = now + 250L;
                  } finally {
                     PveAutomationCoordinator.INSTANCE.release(this);
                  }

                  if (sent) {
                     this.pendingPrompts.removeFirstOccurrence(prompt);
                     this.handledPrompts.add(prompt);
                     this.nextActionAt = now + Math.round(this.cooldown.getValue() * 1000.0);
                  }
               }
            }
         }
      }
   }

   @EventTarget
   public void onDisconnect(DisconnectEvent event) {
      this.resetRuntimeState();
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntimeState();
   }

   @Override
   protected void onPveDisable() {
      this.resetRuntimeState();
      this.customPassword.setValue("");
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.resetRuntimeState();
      this.customPassword.setValue("");
   }

   static boolean isValidPassword(String password) {
      return password != null && SAFE_PASSWORD.matcher(password).matches();
   }

   static String incomingText(Packet<?> packet) {
      if (packet instanceof GameMessageS2CPacket systemChat) {
         return systemChat.content().getString();
      } else if (packet instanceof ProfilelessChatMessageS2CPacket disguisedChat) {
         return disguisedChat.message().getString();
      } else if (packet instanceof ChatMessageS2CPacket playerChat) {
         Text unsigned = playerChat.unsignedContent();
         return unsigned != null ? unsigned.getString() : playerChat.body().content();
      } else {
         return null;
      }
   }

   private void enqueue(MinecraftClient client, AutoAuthPromptParser.Prompt prompt) {
      this.syncConnection(client);
      if (this.connection != null && !this.handledPrompts.contains(prompt) && !this.pendingPrompts.contains(prompt)) {
         this.pendingPrompts.addLast(prompt);
      }
   }

   private AutoAuthPromptParser.Prompt nextPrompt() {
      while (!this.pendingPrompts.isEmpty() && this.handledPrompts.contains(this.pendingPrompts.peekFirst())) {
         this.pendingPrompts.removeFirst();
      }

      return this.pendingPrompts.peekFirst();
   }

   private Optional<String> resolvePassword(AutoAuthCredentialStore.Scope scope) throws IOException, GeneralSecurityException {
      if (this.passwordMode.is("Generated")) {
         return Optional.of(this.credentialStore.generatedPassword(scope));
      } else {
         String entered = this.customPassword.getValue();
         if (!entered.isEmpty()) {
            if (!isValidPassword(entered)) {
               return Optional.empty();
            } else {
               this.credentialStore.saveCustomPassword(scope, entered);
               this.customPassword.setValue("");
               return Optional.of(entered);
            }
         } else {
            return this.credentialStore.loadCustomPassword(scope).filter(AutoAuthFeature::isValidPassword);
         }
      }
   }

   private void syncConnection(MinecraftClient client) {
      ClientPlayNetworkHandler current = client.getNetworkHandler();
      if (current != this.connection) {
         this.connection = current;
         this.pendingPrompts.clear();
         this.handledPrompts.clear();
         this.nextActionAt = 0L;
      }
   }

   private void resetRuntimeState() {
      this.connection = null;
      this.pendingPrompts.clear();
      this.handledPrompts.clear();
      this.nextActionAt = 0L;
      PveAutomationCoordinator.INSTANCE.release(this);
   }

   private static AutoAuthCredentialStore.Scope scope(MinecraftClient client) {
      ServerInfo server = client.getCurrentServerEntry();
      String account = client.player != null ? client.player.getGameProfile().name() : client.getSession().getUsername();
      return new AutoAuthCredentialStore.Scope(server.address, account);
   }

   private static String command(AutoAuthPromptParser.Prompt prompt, String password) {
      return switch (prompt) {
         case LOGIN -> "login " + password;
         case REGISTER -> "register " + password + " " + password;
      };
   }

   private TextSetting customPasswordSetting() {
      TextSetting setting = new TextSetting("Password", "", 64).secret();
      setting.nonPersistent();
      setting.visibleWhen(() -> this.passwordMode.is("Custom Password"));
      return setting;
   }
}
