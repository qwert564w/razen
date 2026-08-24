package org.ryzen.feature.impl.movement;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.Gizmo;
import net.minecraft.world.debug.gizmo.GizmoDrawer;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.GizmoDrawing.CollectorScope;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.TextSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class TpLootFeature extends Feature implements MinecraftContext {
   private static final String MODE_GRIM = "Grim";
   private static final String MODE_MATRIX = "Grim + Matrix";
   private static final long TELEPORT_COOLDOWN_MS = 50L;
   private static final long LOOT_FINISH_DELAY_MS = 100L;
   private static final double BOX_INSET = 0.5;
   private static final double CHASE_SPEED_SCALE = 0.5;
   private static final double MAX_CHASE_SPEED = 0.8;
   private static final double MIN_CHASE_DISTANCE = 0.3;
   private static final float HEALTH_ARMOR_FACTOR = 0.04F;
   private static final int PRIORITY_FALLBACK = Integer.MAX_VALUE;
   private static final int PRIORITY_OTHER = 100;
   private static final List<String> FLIGHT_MARKERS = List.of("режиме полета", "режим полёта");
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "Grim", "Grim", "Grim + Matrix"));
   public final BooleanSetting autoLeave = this.register(new BooleanSetting("Auto Leave", true));
   public final TextSetting leaveCommand = this.register(new TextSetting("Leave Command", "hub"));
   public final BooleanSetting autoDisable = this.register(new BooleanSetting("Auto Disable", true));
   public final BooleanSetting tpCommand = this.register(new BooleanSetting("Tp Command", true));
   public final TextSetting command = this.register(new TextSetting("Command", "tp"));
   public final TextSetting targetItems = this.register(new TextSetting("Target Items", ""));
   private Box chaseBox;
   private Box lootBox;
   private boolean chasingPlayer;
   private boolean teleportActive;
   private boolean tpCommandSent;
   private long lastTeleportMs;
   private long lastLootMs;

   public TpLootFeature() {
      super("TpLoot", "Teleports onto dropped loot and collects it", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onDisable() {
      this.resetState();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.resetState();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null && mc.world != null) {
         boolean foundLoot = this.scanBestLootItem(player);
         if (this.mode.is("Grim + Matrix")) {
            this.handleMatrix(player, foundLoot);
         } else {
            this.handleGrim(player, foundLoot);
         }

         if (this.teleportActive && System.currentTimeMillis() - this.lastLootMs > 100L && this.hasTargetItemsInInventory(player)) {
            if (this.autoLeave.getValue() && player.networkHandler != null) {
               player.networkHandler.sendChatCommand(stripSlash(this.leaveCommand.getValue()));
            }

            this.teleportActive = false;
            if (this.autoDisable.getValue()) {
               this.setEnabled(false);
               ChatUtil.info("TpLoot finished");
            }
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && event.getPacket() instanceof GameMessageS2CPacket chat) {
         String var4 = chat.content().getString().toLowerCase(Locale.ROOT);
         if (FLIGHT_MARKERS.stream().anyMatch(var4::contains)) {
            this.sendTpChatCommand();
            this.tpCommandSent = true;
         }
      }
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (this.mode.is("Grim + Matrix") && mc.player != null && mc.world != null && event.getClient().worldRenderer != null) {
         CollectorScope ignored = event.getClient().worldRenderer.startDrawingGizmos();

         try {
            if (this.chaseBox != null) {
               GizmoDrawing.collect(new TpLootFeature.LootGizmo(this.chaseBox, 1694498815)).ignoreOcclusion();
            }

            if (this.lootBox != null) {
               GizmoDrawing.collect(new TpLootFeature.LootGizmo(this.lootBox, 1694498560)).ignoreOcclusion();
            }
         } catch (Throwable var6) {
            if (ignored != null) {
               try {
                  ignored.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (ignored != null) {
            ignored.close();
         }
      }
   }

   private void handleGrim(ClientPlayerEntity player, boolean foundLoot) {
      if (foundLoot && this.lootBox != null) {
         if (!player.getBoundingBox().intersects(this.lootBox)) {
            this.teleportToBox(player, this.lootBox);
            this.tpCommandSent = false;
         } else {
            if (this.tpCommand.getValue() && !this.tpCommandSent) {
               this.sendTpChatCommand();
               this.tpCommandSent = true;
            }
         }
      }
   }

   private void handleMatrix(ClientPlayerEntity player, boolean foundLoot) {
      if (foundLoot && this.lootBox != null) {
         this.chasingPlayer = true;
         if (!player.getBoundingBox().intersects(this.lootBox)) {
            this.teleportToBox(player, this.lootBox);
            this.tpCommandSent = false;
         } else {
            this.chasingPlayer = false;
            if (player.getAbilities().flying && this.tpCommand.getValue() && !this.tpCommandSent) {
               this.sendTpChatCommand();
               this.tpCommandSent = true;
            }

            this.scanAndTeleportItems(player);
         }
      } else {
         PlayerEntity target = this.findWeakestPlayer(player);
         if (target != null && !this.chasingPlayer) {
            this.tpCommandSent = false;
            this.chasePlayer(player, target);
         }
      }
   }

   private boolean scanBestLootItem(ClientPlayerEntity player) {
      int bestPriority = Integer.MAX_VALUE;
      Vec3d bestPos = null;

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof ItemEntity) {
            ItemEntity itemEntity = (ItemEntity)entity;
            int priority = this.itemPriority(itemEntity.getStack().getItem());
            if (priority < bestPriority) {
               bestPriority = priority;
               bestPos = itemEntity.getEntityPos();
            }
         }
      }

      if (bestPos == null) {
         this.lootBox = null;
         return false;
      } else {
         this.lootBox = new Box(bestPos.x - 0.5, bestPos.y, bestPos.z - 0.5, bestPos.x + 0.5, bestPos.y + 1.0, bestPos.z + 0.5);
         return true;
      }
   }

   private void scanAndTeleportItems(ClientPlayerEntity player) {
      if (System.currentTimeMillis() - this.lastTeleportMs >= 50L) {
         for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity && this.isTargetItem(itemEntity.getStack().getItem())) {
               this.teleportTo(player, itemEntity.getEntityPos());
               return;
            }
         }
      }
   }

   private void teleportToBox(ClientPlayerEntity player, Box box) {
      double x = (box.minX + box.maxX) / 2.0;
      double y = box.minY;
      double z = (box.minZ + box.maxZ) / 2.0;
      this.teleportTo(player, new Vec3d(x, y, z));
      if (this.mode.is("Grim + Matrix")) {
         this.chaseBox = box;
      }
   }

   private void teleportTo(ClientPlayerEntity player, Vec3d pos) {
      if (player.networkHandler != null) {
         long now = System.currentTimeMillis();
         if (now - this.lastTeleportMs >= 50L) {
            for (int i = 0; i < 3; i++) {
               player.networkHandler.sendPacket(new OnGroundOnly(player.isOnGround(), player.horizontalCollision));
            }

            player.networkHandler.sendPacket(new PositionAndOnGround(pos.x, pos.y, pos.z, false, player.horizontalCollision));
            player.setPosition(pos.x, pos.y, pos.z);
            this.lastTeleportMs = now;
            this.teleportActive = true;
            this.lastLootMs = now;
         }
      }
   }

   private PlayerEntity findWeakestPlayer(ClientPlayerEntity self) {
      PlayerEntity best = null;
      float bestScore = Float.MAX_VALUE;

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != self && player.isAlive() && !player.isSpectator() && !player.isCreative()) {
            float score = this.playerHealthScore(player);
            if (score < bestScore) {
               bestScore = score;
               best = player;
            }
         }
      }

      return best;
   }

   private void chasePlayer(ClientPlayerEntity player, PlayerEntity target) {
      Vec3d pos = target.getEntityPos();
      double tx = pos.x;
      double ty = player.getY();
      double tz = pos.z;
      this.chaseBox = new Box(tx - 0.5, ty - 0.1, tz - 0.5, tx + 0.5, ty + 0.5, tz + 0.5);
      double dx = tx - player.getX();
      double dy = ty - player.getY();
      double dz = tz - player.getZ();
      double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (distance < 0.3) {
         player.setVelocity(0.0, 0.0, 0.0);
      } else {
         double speed = Math.min(distance * 0.5, 0.8);
         player.setVelocity(dx / distance * speed, dy / distance * speed, dz / distance * speed);
         player.knockedBack = true;
         player.setYaw((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
      }
   }

   private float playerHealthScore(PlayerEntity player) {
      float health = player.getHealth() + player.getAbsorptionAmount();
      float armorFactor = (float)player.getArmor() * 0.04F;
      return health * (1.0F - armorFactor);
   }

   private boolean hasTargetItemsInInventory(ClientPlayerEntity player) {
      for (int slot = 0; slot < 36; slot++) {
         if (this.isTargetItem(player.getInventory().getStack(slot).getItem())) {
            return true;
         }
      }

      return false;
   }

   private boolean isTargetItem(Item item) {
      List<String> filter = this.parsedTargets();
      if (filter.isEmpty()) {
         return this.itemPriority(item) != Integer.MAX_VALUE;
      } else {
         String id = this.itemId(item);
         return filter.stream().anyMatch(id::contains);
      }
   }

   private List<String> parsedTargets() {
      String raw = this.targetItems.getValue();
      return raw != null && !raw.isBlank()
         ? Arrays.stream(raw.toLowerCase(Locale.ROOT).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
         : List.of();
   }

   private int itemPriority(Item item) {
      String name = this.itemId(item);
      if (!this.parsedTargets().isEmpty() && this.parsedTargets().stream().noneMatch(name::contains)) {
         return Integer.MAX_VALUE;
      } else if (name.contains("totem")) {
         return 1;
      } else if (name.contains("elytra")) {
         return 2;
      } else if (name.contains("enchanted_golden_apple") || name.contains("notch_apple")) {
         return 3;
      } else if (name.contains("netherite") || name.contains("ancient_debris")) {
         return 4;
      } else if (name.contains("skull") || name.contains("head")) {
         return 5;
      } else if (name.contains("end_crystal")) {
         return 6;
      } else if (name.contains("pickaxe")
         || name.contains("axe")
         || name.contains("shovel")
         || name.contains("hoe")
         || name.contains("sword")
         || name.contains("bow")
         || name.contains("crossbow")
         || name.contains("trident")) {
         return 7;
      } else if (name.contains("golden_apple")) {
         return 8;
      } else if (name.contains("firework")) {
         return 9;
      } else if (name.contains("obsidian")) {
         return 10;
      } else if (name.contains("potion") || name.contains("splash") || name.contains("lingering")) {
         return 11;
      } else {
         return name.contains("golden_carrot") ? 12 : 100;
      }
   }

   private String itemId(Item item) {
      return Registries.ITEM.getId(item).getPath().toLowerCase(Locale.ROOT);
   }

   private void sendTpChatCommand() {
      ClientPlayerEntity player = mc.player;
      if (player != null && player.networkHandler != null) {
         player.networkHandler.sendChatCommand(stripSlash(this.command.getValue()));
      }
   }

   private static String stripSlash(String command) {
      return command.startsWith("/") ? command.substring(1) : command;
   }

   private void resetState() {
      this.teleportActive = false;
      this.chaseBox = null;
      this.lootBox = null;
      this.chasingPlayer = false;
      this.tpCommandSent = false;
   }

   @Environment(EnvType.CLIENT)
   private static record LootGizmo(Box box, int color) implements Gizmo {
      public void draw(GizmoDrawer consumer, float opacity) {
         Vec3d a = new Vec3d(this.box.minX, this.box.minY, this.box.minZ);
         Vec3d b = new Vec3d(this.box.maxX, this.box.minY, this.box.minZ);
         Vec3d c = new Vec3d(this.box.maxX, this.box.minY, this.box.maxZ);
         Vec3d d = new Vec3d(this.box.minX, this.box.minY, this.box.maxZ);
         Vec3d e = new Vec3d(this.box.minX, this.box.maxY, this.box.minZ);
         Vec3d f = new Vec3d(this.box.maxX, this.box.maxY, this.box.minZ);
         Vec3d g = new Vec3d(this.box.maxX, this.box.maxY, this.box.maxZ);
         Vec3d h = new Vec3d(this.box.minX, this.box.maxY, this.box.maxZ);
         float w = 1.5F;
         consumer.addLine(a, b, this.color, w);
         consumer.addLine(b, c, this.color, w);
         consumer.addLine(c, d, this.color, w);
         consumer.addLine(d, a, this.color, w);
         consumer.addLine(e, f, this.color, w);
         consumer.addLine(f, g, this.color, w);
         consumer.addLine(g, h, this.color, w);
         consumer.addLine(h, e, this.color, w);
         consumer.addLine(a, e, this.color, w);
         consumer.addLine(b, f, this.color, w);
         consumer.addLine(c, g, this.color, w);
         consumer.addLine(d, h, this.color, w);
      }
   }
}
