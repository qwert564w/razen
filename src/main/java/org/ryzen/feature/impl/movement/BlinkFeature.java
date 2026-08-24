package org.ryzen.feature.impl.movement;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.GizmoDrawing.CollectorScope;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.packet.PacketSendEvent;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureEnableRejectedException;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.world.ZoneGizmos;

@Environment(EnvType.CLIENT)
public final class BlinkFeature extends Feature implements PlayerContext {
   public final BooleanSetting flushOnAttack = this.register(new BooleanSetting("Flush On Attack", true));
   public final BooleanSetting autoFlush = this.register(new BooleanSetting("Auto Flush", true));
   public final NumberSetting autoFlushTicks = this.register(
      new NumberSetting("Flush Interval", 10.0, 1.0, 100.0, 1.0, " ticks").visibleWhen(this.autoFlush::getValue)
   );
   public final BooleanSetting render = this.register(new BooleanSetting("Show Position", true));
   public final ColorSetting color = this.register(new ColorSetting("Color", 6592255));
   private final List<Packet<?>> heldPackets = new ArrayList<>();
   private Vec3d serverPosition;
   private int ticksSinceFlush;
   private boolean flushing;

   public BlinkFeature() {
      super("Blink", "Queues your movement packets and releases them on demand", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onEnable() {
      ClientPlayerEntity player = this.localPlayer();
      if (player == null) {
         throw new FeatureEnableRejectedException("no player in the world");
      } else {
         this.serverPosition = player.getEntityPos();
         this.heldPackets.clear();
         this.ticksSinceFlush = 0;
      }
   }

   @Override
   protected void onDisable() {
      this.flush();
      this.serverPosition = null;
      this.ticksSinceFlush = 0;
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.heldPackets.clear();
      this.serverPosition = null;
      this.ticksSinceFlush = 0;
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (!this.flushing && event.getPhase() == PacketSendEvent.Phase.PRE) {
         Packet<?> packet = event.getPacket();
         if (packet instanceof PlayerMoveC2SPacket) {
            this.heldPackets.add(packet);
            event.cancel();
         } else {
            if (this.flushOnAttack.getValue() && packet instanceof PlayerInteractEntityC2SPacket && !this.heldPackets.isEmpty()) {
               this.flush();
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null) {
         if (!player.isAlive()) {
            this.setEnabled(false);
         } else {
            if (this.autoFlush.getValue() && ++this.ticksSinceFlush >= this.autoFlushTicks.getValue().intValue()) {
               this.flush();
            }
         }
      }
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.serverPosition != null) {
         Box box = new Box(
            this.serverPosition.x - 0.3,
            this.serverPosition.y,
            this.serverPosition.z - 0.3,
            this.serverPosition.x + 0.3,
            this.serverPosition.y + 1.8,
            this.serverPosition.z + 0.3
         );
         int stroke = ColorUtil.withAlpha(this.color.getValue(), 255);
         CollectorScope ignored = event.getClient().worldRenderer.startDrawingGizmos();

         try {
            GizmoDrawing.collect(ZoneGizmos.cube(box, stroke, ColorUtil.multiplyAlpha(stroke, 0.25F), 2.0F)).ignoreOcclusion();
         } catch (Throwable var8) {
            if (ignored != null) {
               try {
                  ignored.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (ignored != null) {
            ignored.close();
         }
      }
   }

   private void flush() {
      ClientPlayerEntity player = this.localPlayer();
      this.ticksSinceFlush = 0;
      if (player != null && player.networkHandler != null && !this.heldPackets.isEmpty()) {
         List<Packet<?>> queued = List.copyOf(this.heldPackets);
         this.heldPackets.clear();
         this.flushing = true;

         try {
            for (Packet<?> packet : queued) {
               player.networkHandler.sendPacket(packet);
            }
         } finally {
            this.flushing = false;
         }

         this.serverPosition = player.getEntityPos();
      } else {
         if (player != null) {
            this.serverPosition = player.getEntityPos();
         }

         this.heldPackets.clear();
      }
   }
}
