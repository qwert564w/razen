package org.ryzen.feature.impl.movement;

import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.util.PlayerInput;
import org.lwjgl.glfw.GLFW;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.packet.PacketSendEvent;
import org.ryzen.event.events.screen.ScreenCloseEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.mixin.accessor.KeyMappingAccessor;

@Environment(EnvType.CLIENT)
public final class InventoryMoveFeature extends Feature implements MinecraftContext {
   public final BooleanSetting tickDilation = this.register(new BooleanSetting("Tick Dilation", false));
   private final Deque<Packet<?>> deferredClicks = new ArrayDeque<>();
   private InventoryMoveFeature.ReplayState replayState = InventoryMoveFeature.ReplayState.IDLE;
   private int phaseTicks;

   public InventoryMoveFeature() {
      super("InventoryMove", "Walk around while your inventory is open", FeatureCategory.MOVEMENT, -1);
   }

   public static InventoryMoveFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(InventoryMoveFeature.class);
   }

   public static PlayerInput screenInput() {
      InventoryMoveFeature feature = getEnabled();
      return feature != null && mc.player != null && feature.screen() instanceof InventoryScreen
         ? new PlayerInput(
            isKeyDown(mc.options.forwardKey),
            isKeyDown(mc.options.backKey),
            isKeyDown(mc.options.leftKey),
            isKeyDown(mc.options.rightKey),
            isKeyDown(mc.options.jumpKey),
            isKeyDown(mc.options.sneakKey),
            isKeyDown(mc.options.sprintKey)
         )
         : null;
   }

   public static boolean shouldStopMovement() {
      InventoryMoveFeature feature = getEnabled();
      return feature != null && feature.replayState != InventoryMoveFeature.ReplayState.IDLE;
   }

   public static boolean isClickPipelineBusy() {
      InventoryMoveFeature feature = getEnabled();
      return feature != null && (feature.replayState != InventoryMoveFeature.ReplayState.IDLE || !feature.deferredClicks.isEmpty());
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPhase() == PacketSendEvent.Phase.PRE
         && event.getPacket() instanceof ClickSlotC2SPacket
         && this.replayState == InventoryMoveFeature.ReplayState.IDLE
         && this.screen() instanceof InventoryScreen
         && (this.isMoving() || !this.deferredClicks.isEmpty())) {
         this.deferredClicks.addLast(event.getPacket());
         event.cancel();
      }
   }

   @EventTarget
   public void onScreenClose(ScreenCloseEvent event) {
      if (event.getScreen() instanceof InventoryScreen) {
         KeyBinding.updatePressedStates();
         if (!this.deferredClicks.isEmpty() && this.replayState == InventoryMoveFeature.ReplayState.IDLE) {
            this.replayState = InventoryMoveFeature.ReplayState.PREPARING;
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.replayState != InventoryMoveFeature.ReplayState.IDLE) {
         ClientPlayerEntity player = this.player();
         if (player == null) {
            this.reset();
         } else {
            this.phaseTicks++;
            if (this.phaseTicks >= (this.tickDilation.getValue() ? 2 : 1)) {
               this.phaseTicks = 0;
               switch (this.replayState) {
                  case PREPARING:
                     this.replayState = InventoryMoveFeature.ReplayState.SENDING;
                     break;
                  case SENDING:
                     Packet<?> packet = this.deferredClicks.pollFirst();
                     if (packet != null) {
                        player.networkHandler.sendPacket(packet);
                     }

                     if (this.deferredClicks.isEmpty()) {
                        this.replayState = InventoryMoveFeature.ReplayState.SETTLING;
                     }
                     break;
                  case SETTLING:
                     this.reset();
                     break;
                  default:
                     this.reset();
               }
            }
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.reset();
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = this.player();
      if (player != null) {
         while (!this.deferredClicks.isEmpty()) {
            player.networkHandler.sendPacket(this.deferredClicks.pollFirst());
         }
      }

      this.reset();
   }

   private boolean isMoving() {
      ClientPlayerEntity player = this.player();
      return player != null && player.input != null && player.input.getMovementInput().lengthSquared() > 1.0E-4F;
   }

   private void reset() {
      this.deferredClicks.clear();
      this.replayState = InventoryMoveFeature.ReplayState.IDLE;
      this.phaseTicks = 0;
   }

   private static boolean isKeyDown(KeyBinding mapping) {
      Key key = ((KeyMappingAccessor)mapping).getKey();
      return key.getCategory() == Type.MOUSE
         ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == 1
         : InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
   }

   @Environment(EnvType.CLIENT)
   private static enum ReplayState {
      IDLE,
      PREPARING,
      SENDING,
      SETTLING;
   }
}
