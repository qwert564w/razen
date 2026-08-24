package org.ryzen.feature.impl.pve;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.feature.setting.TextSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.utils.FriendManager;

@Environment(EnvType.CLIENT)
public final class AutoLeaveFeature extends PveFeature {
   public static final String ACTION_DISCONNECT = "Disconnect";
   public static final String ACTION_SERVER_COMMAND = "Server Command";
   public static final String CONDITION_LOW_HEALTH = "Low Health";
   public static final String CONDITION_MODERATOR = "Moderator";
   public static final String CONDITION_NEARBY_PLAYER = "Nearby Player";
   private static final long CLEAR_DEBOUNCE_MILLIS = 500L;
   public final ModeSetting action = this.register(new ModeSetting("Action", "Server Command", "Disconnect", "Server Command"));
   public final TextSetting command = this.register(this.commandSetting());
   public final MultiSelectSetting conditions = this.register(
      new MultiSelectSetting("Conditions", List.of("Low Health", "Moderator", "Nearby Player"), "Low Health", "Moderator", "Nearby Player")
   );
   public final NumberSetting minimumHealth = this.register(
      new NumberSetting("Minimum Health", 5.0, 1.0, 20.0, 0.5, " HP").visibleWhen(() -> this.conditions.isSelected("Low Health"))
   );
   public final NumberSetting leaveDistance = this.register(
      new NumberSetting("Trigger Distance", 10.0, 1.0, 100.0, 1.0, " blocks").visibleWhen(() -> this.conditions.isSelected("Nearby Player"))
   );
   public final NumberSetting cooldown = this.register(new NumberSetting("Cooldown", 5.0, 1.0, 30.0, 1.0, " s"));
   private boolean dangerLatched;
   private long clearStartedAt;
   private long nextActionAt;
   private ClientPlayNetworkHandler connection;

   public AutoLeaveFeature() {
      super("AutoLeave", "Leaves when health, nearby players, or moderator presence becomes unsafe", -1, AutomationPriority.EMERGENCY);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      this.syncConnection(client);
      ClientPlayerEntity self = client.player;
      ClientWorld level = client.world;
      if (self != null && level != null && client.getNetworkHandler() != null && client.getCurrentServerEntry() != null) {
         long now = System.currentTimeMillis();
         if (!self.isCreative() && !self.isSpectator()) {
            boolean danger = this.hasDanger(client, self, level);
            if (!danger) {
               this.clearLatchAfterDebounce(now);
            } else {
               this.clearStartedAt = 0L;
               if (!this.dangerLatched && now >= this.nextActionAt) {
                  if (this.performAction(client, self)) {
                     this.dangerLatched = true;
                     this.nextActionAt = now + Math.round(this.cooldown.getValue() * 1000.0);
                  }
               }
            }
         } else {
            this.clearLatchAfterDebounce(now);
         }
      }
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntimeState();
   }

   @Override
   protected void onPveDisable() {
      this.resetRuntimeState();
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.resetRuntimeState();
   }

   private boolean hasDanger(MinecraftClient client, ClientPlayerEntity self, ClientWorld level) {
      if (this.conditions.isSelected("Low Health") && self.getHealth() <= this.minimumHealth.getValue().floatValue()) {
         return true;
      } else {
         return this.conditions.isSelected("Moderator") && ModeratorDetector.find(client, self).isPresent()
            ? true
            : this.conditions.isSelected("Nearby Player") && nearbyNonFriend(self, level, this.leaveDistance.getValue());
      }
   }

   private boolean performAction(MinecraftClient client, ClientPlayerEntity self) {
      if (this.action.is("Disconnect")) {
         client.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
         return true;
      } else {
         Optional<String> safeCommand = SafeServerCommand.normalize(this.command.getValue());
         if (!safeCommand.isEmpty() && this.claim(AutomationResource.CHAT, new AutomationResource[0])) {
            boolean var5;
            try {
               self.networkHandler.sendChatCommand(safeCommand.get());
               return true;
            } catch (RuntimeException var9) {
               var5 = false;
            } finally {
               PveAutomationCoordinator.INSTANCE.release(this);
            }

            return var5;
         } else {
            return false;
         }
      }
   }

   private void clearLatchAfterDebounce(long now) {
      if (!this.dangerLatched) {
         this.clearStartedAt = 0L;
      } else if (this.clearStartedAt == 0L) {
         this.clearStartedAt = now;
      } else {
         if (now - this.clearStartedAt >= 500L) {
            this.dangerLatched = false;
            this.clearStartedAt = 0L;
         }
      }
   }

   private void resetRuntimeState() {
      this.connection = null;
      this.clearDebounceState();
      PveAutomationCoordinator.INSTANCE.release(this);
   }

   private void syncConnection(MinecraftClient client) {
      ClientPlayNetworkHandler current = client.getNetworkHandler();
      if (current != this.connection) {
         this.connection = current;
         this.clearDebounceState();
         PveAutomationCoordinator.INSTANCE.release(this);
      }
   }

   private void clearDebounceState() {
      this.dangerLatched = false;
      this.clearStartedAt = 0L;
      this.nextActionAt = 0L;
   }

   static boolean nearbyNonFriend(ClientPlayerEntity self, ClientWorld level, double distance) {
      double maxDistanceSquared = distance * distance;

      for (PlayerEntity candidate : level.getPlayers()) {
         if (candidate != self
            && !candidate.getUuid().equals(self.getUuid())
            && candidate.isAlive()
            && !candidate.isCreative()
            && !candidate.isSpectator()
            && !FriendManager.INSTANCE.isFriend(candidate.getGameProfile().name())
            && self.squaredDistanceTo(candidate) <= maxDistanceSquared) {
            return true;
         }
      }

      return false;
   }

   private TextSetting commandSetting() {
      return new TextSetting("Command", "/hub", 64).visibleWhen(() -> this.action.is("Server Command"));
   }
}
