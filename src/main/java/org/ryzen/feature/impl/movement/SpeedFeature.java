package org.ryzen.feature.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.combat.AuraFeature;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.TimerUtil;

@Environment(EnvType.CLIENT)
public final class SpeedFeature extends Feature implements PlayerContext {
   private static final String MODE_GRIM_NEW = "Grim New";
   private static final String MODE_COLLISION = "Collision";
   private static final String MODE_COLLISION_2 = "Collision2";
   private static final String MODE_HOLY_WORLD = "HolyWorld";
   private static final String MODE_META_HVH = "MetaHvH";
   private static final String MODE_GRIM_TELEPORT = "Grim Teleport";
   private static final String MODE_REALLYWORLD = "Reallyworld";
   private static final String META_DEFAULT = "Default";
   private static final String META_CUSTOM = "Custom";
   private static final int BOUNCE_WARMUP_TICKS = 3;
   private static final double BOUNCE_STEP = 0.03;
   private static final double BOUNCE_LIFT = 0.03;
   private static final double GRIM_GROUND_STEP = 0.0855;
   private static final double REALLY_GROUND_STEP = 0.085;
   private static final float GRIM_TICK_TIMER = 1.6F;
   private static final float GRIM_FLY_TIMER = 0.4F;
   private static final float REALLY_DISABLER_TIMER = 1.7F;
   private static final float REALLY_FLY_TIMER = 0.3F;
   private static final long TELEPORT_WARMUP_MS = 100L;
   private static final long TELEPORT_CYCLE_MS = 1400L;
   private static final float TELEPORT_TIMER_EVEN = 1.5F;
   private static final float TELEPORT_TIMER_ODD = 1.2F;
   private static final double COLLISION_SCALE = 0.2;
   private static final double HOLY_SCALE = 10.0;
   private static final double HOLY_VERTICAL_SLACK = 0.15;
   private static final float META_SLOWNESS_SCALE = 0.835F;
   private static final float META_AIRBORNE_SCALE = 1.435F;
   private static final float META_HEAD_DIVISOR = 1.3F;
   private static final float META_BOOST_SCALE = 10.0F;
   private static final float META_AMP_2_TOTEM = 0.49665F;
   private static final float META_AMP_2 = 0.41598004F;
   private static final float META_AMP_1_TOTEM = 0.43F;
   private static final float META_AMP_1 = 0.36F;
   private static final float META_BASE_TOTEM = 0.2924F;
   private static final float META_BASE = 0.24480002F;
   private static final String[] META_TOTEMS = new String[]{"Шар Геракла 2", "Шар CHAMPION", "Шар GOD", "Талисман Венома", "КУБИК-РУБИК"};
   private static final double[] META_HEAD_ARMOUR = new double[]{3.0, 3.5};
   private static final int HURT_STATUS = 2;
   public final ModeSetting mode = this.register(
      new ModeSetting("Mode", "Grim New", "Grim New", "Collision", "Collision2", "HolyWorld", "MetaHvH", "Grim Teleport", "Reallyworld")
   );
   public final NumberSetting collisionRange = this.register(
      new NumberSetting("Target Range", 1.5, 0.1, 3.0, 0.01, "").visibleWhen(() -> this.mode.is("Collision") || this.mode.is("Collision2"))
   );
   public final NumberSetting collisionSpeed = this.register(
      new NumberSetting("Shove Speed", 0.15, 0.1, 1.0, 0.01, "").visibleWhen(() -> this.mode.is("Collision") || this.mode.is("Collision2"))
   );
   public final NumberSetting holyRange = this.register(
      new NumberSetting("Overlap Range", 0.35, 0.2, 0.95, 0.01, "").visibleWhen(() -> this.mode.is("HolyWorld"))
   );
   public final NumberSetting holySpeed = this.register(
      new NumberSetting("Overlap Speed", 0.35, 0.3, 1.0, 0.05, "").visibleWhen(() -> this.mode.is("HolyWorld"))
   );
   public final ModeSetting metaMode = this.register(new ModeSetting("Meta Mode", "Default", "Default", "Custom").visibleWhen(() -> this.mode.is("MetaHvH")));
   public final NumberSetting metaSpeed = this.register(
      new NumberSetting("Meta Speed", 0.2, 0.2, 1.05, 0.01, "").visibleWhen(() -> this.mode.is("MetaHvH") && this.metaMode.is("Custom"))
   );
   public final BooleanSetting metaDamageBoost = this.register(new BooleanSetting("Boost On Damage", false).visibleWhen(() -> this.mode.is("MetaHvH")));
   public final NumberSetting metaBoostAmount = this.register(
      new NumberSetting("Boost Amount", 0.7, 0.1, 5.0, 0.1, "").visibleWhen(() -> this.mode.is("MetaHvH") && this.metaDamageBoost.getValue())
   );
   public final NumberSetting metaBoostDuration = this.register(
      new NumberSetting("Boost Duration", 700.0, 100.0, 2000.0, 100.0, "ms").visibleWhen(() -> this.mode.is("MetaHvH") && this.metaDamageBoost.getValue())
   );
   public final BooleanSetting reallyDisabler = this.register(new BooleanSetting("Disabler", true).visibleWhen(() -> this.mode.is("Reallyworld")));
   public final BooleanSetting lowerFps = this.register(new BooleanSetting("Lower FPS", false).visibleWhen(() -> this.mode.is("Reallyworld")));
   public final NumberSetting fpsLimit = this.register(
      new NumberSetting("FPS Limit", 30.0, 15.0, 30.0, 1.0, "").visibleWhen(() -> this.mode.is("Reallyworld") && this.lowerFps.getValue())
   );
   private int grimTicks;
   private int reallyTicks;
   private int grimCeilingTicks;
   private int reallyCeilingTicks;
   private boolean timerTouched;
   private long teleportCycleStart;
   private boolean teleportBoosting;
   private long damageBoostStart;
   private boolean damageBoosted;
   private boolean fpsOverridden;
   private int savedFpsLimit;

   public SpeedFeature() {
      super("Speed", "Moves you faster than walking", FeatureCategory.MOVEMENT, -1);
   }

   public static SpeedFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(SpeedFeature.class);
   }

   @Override
   protected void onEnable() {
      this.grimTicks = 0;
      this.reallyTicks = 0;
      this.grimCeilingTicks = 0;
      this.reallyCeilingTicks = 0;
      this.teleportCycleStart = System.currentTimeMillis();
      this.teleportBoosting = false;
      this.damageBoosted = false;
      this.timerTouched = false;
   }

   @Override
   protected void onDisable() {
      if (this.timerTouched) {
         TimerUtil.resetTimer();
         this.timerTouched = false;
      }

      this.restoreFps();
      this.grimTicks = 0;
      this.reallyTicks = 0;
      this.grimCeilingTicks = 0;
      this.reallyCeilingTicks = 0;
      this.teleportBoosting = false;
      this.damageBoosted = false;
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPost()) {
         this.onPlayerTickPost(event);
      } else {
         ClientPlayerEntity player = event.getPlayer();
         if (player != null && this.level() != null) {
            if (!this.mode.is("Grim New")) {
               this.grimTicks = 0;
               this.grimCeilingTicks = 0;
            }

            if (!this.mode.is("Reallyworld")) {
               this.reallyTicks = 0;
               this.reallyCeilingTicks = 0;
            }

            boolean clockMode = this.mode.is("Grim New") || this.mode.is("Reallyworld") || this.mode.is("Grim Teleport");
            if (!clockMode && this.timerTouched) {
               TimerUtil.resetTimer();
               this.timerTouched = false;
            }

            this.applyFpsOverride(this.mode.is("Reallyworld") && this.lowerFps.getValue());
            String var4 = this.mode.getValue();
            switch (var4) {
               case "Grim New":
                  this.tickGrimNew(player);
                  break;
               case "Reallyworld":
                  this.tickReallyworld(player);
                  break;
               case "Grim Teleport":
                  this.tickGrimTeleport(player);
                  break;
               case "Collision":
                  this.tickCollision(player, false);
                  break;
               case "Collision2":
                  this.tickCollision(player, true);
                  break;
               case "HolyWorld":
                  this.tickHolyWorld(player);
                  break;
               case "MetaHvH":
                  this.tickMetaHvH(player);
            }
         }
      }
   }

   private void onPlayerTickPost(PlayerTickEvent event) {
      ClientPlayerEntity player = event.getPlayer();
      if (player != null && player.networkHandler != null) {
         if (this.mode.is("Reallyworld")) {
            if (this.reallyDisabler.getValue() && this.reallyTicks % 2 == 0) {
               this.setTimer(0.3F);
               this.sendFallFlyingPair(player);
            }
         } else {
            if (this.mode.is("Grim New") && this.grimTicks % 2 == 0) {
               this.setTimer(0.4F);
               this.sendFallFlyingPair(player);
            }
         }
      }
   }

   @EventTarget
   public void onPlayerInput(PlayerInputEvent event) {
      ClientPlayerEntity player = this.localPlayer();
      if (player != null) {
         if (this.mode.is("Reallyworld")) {
            this.reallyCeilingTicks = player.verticalCollision ? this.reallyCeilingTicks + 1 : 0;
            if (this.reallyCeilingTicks >= 1) {
               player.jump();
            }
         } else {
            if (this.mode.is("Grim New")) {
               this.grimCeilingTicks = player.verticalCollision ? this.grimCeilingTicks + 1 : 0;
               if (this.grimCeilingTicks >= 1) {
                  player.jump();
               }
            }
         }
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            if (this.mode.is("Grim New")) {
               if (this.grimTicks % 2 == 1) {
                  this.grimTicks++;
               }

               this.setTimer(1.0F);
            } else if (this.mode.is("Reallyworld")) {
               if (this.reallyTicks % 2 == 1) {
                  this.reallyTicks++;
               }

               this.setTimer(1.0F);
            }
         } else {
            if (this.mode.is("MetaHvH")
               && this.metaDamageBoost.getValue()
               && event.getPacket() instanceof EntityStatusS2CPacket packet
               && packet.getStatus() == 2) {
               mc.execute(() -> this.markDamaged(packet));
            }
         }
      }
   }

   private void markDamaged(EntityStatusS2CPacket packet) {
      if (this.isEnabled() && this.level() != null && packet.getEntity(this.level()) == this.player()) {
         this.damageBoosted = true;
         this.damageBoostStart = System.currentTimeMillis();
      }
   }

   private void tickGrimNew(ClientPlayerEntity player) {
      if (this.grimTicks > 3) {
         double step = 0.03;
         if (this.grimTicks % 2 == 0) {
            this.setTimer(1.6F);
            player.addVelocityInternal(new Vec3d(0.0, 0.03, 0.0));
            if (player.isOnGround()) {
               step = 0.0855;
            }
         }

         pushAlongInput(player, step);
      }

      this.grimTicks++;
   }

   private void tickReallyworld(ClientPlayerEntity player) {
      if (this.reallyDisabler.getValue()) {
         this.setTimer(1.7F);
      }

      if (this.reallyTicks > 3) {
         double step = 0.03;
         if (this.reallyTicks % 2 == 0) {
            player.addVelocityInternal(new Vec3d(0.0, 0.03, 0.0));
            step = player.isOnGround() ? 0.085 : 0.03;
         }

         pushAlongInput(player, step);
      }

      this.reallyTicks++;
   }

   private static void pushAlongInput(ClientPlayerEntity player, double step) {
      double radians = movementDirection(player, true);
      player.addVelocityInternal(new Vec3d(-Math.sin(radians) * step, 0.0, Math.cos(radians) * step));
   }

   private void sendFallFlyingPair(ClientPlayerEntity player) {
      for (int i = 0; i < 2; i++) {
         player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
      }
   }

   private void tickGrimTeleport(ClientPlayerEntity player) {
      long elapsed = System.currentTimeMillis() - this.teleportCycleStart;
      if (elapsed >= 100L) {
         this.teleportBoosting = true;
      }

      if (elapsed >= 1400L) {
         this.teleportBoosting = false;
         this.teleportCycleStart = System.currentTimeMillis();
      }

      if (!this.teleportBoosting) {
         this.setTimer(1.0F);
      } else {
         if (player.isOnGround() && !mc.options.jumpKey.isPressed()) {
            player.jump();
         }

         this.setTimer(player.age % 2 == 0 ? 1.5F : 1.2F);
      }
   }

   private void tickCollision(ClientPlayerEntity player, boolean fallBackToLook) {
      if (player.hurtTime <= 0 && !player.isOnGround()) {
         LivingEntity target = auraTarget();
         if (target != null && target != player) {
            Vec3d toTarget = target.getEntityPos().subtract(player.getEntityPos());
            if (toTarget.length() <= this.collisionRange.getValue()) {
               this.shove(player, toTarget);
               return;
            }
         }

         if (fallBackToLook) {
            Vec3d look = player.getRotationVector();
            this.shove(player, new Vec3d(look.x, 0.0, look.z));
         }
      }
   }

   private void shove(ClientPlayerEntity player, Vec3d direction) {
      double length = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
      if (!(length <= 0.0)) {
         double scale = this.collisionSpeed.getValue() * 0.2 / length;
         player.addVelocity(direction.x * scale, 0.0, direction.z * scale);
      }
   }

   private static LivingEntity auraTarget() {
      AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
      if (aura == null) {
         return null;
      } else {
         LivingEntity target = aura.getCurrentTarget();
         return target != null && target.isAlive() ? target : null;
      }
   }

   private void tickHolyWorld(ClientPlayerEntity player) {
      if (!player.getAbilities().flying && !player.isOnGround()) {
         double range = this.holyRange.getValue();
         Box overlap = player.getBoundingBox().expand(range, 0.15, range);
         boolean touching = false;

         for (PlayerEntity other : this.level().getPlayers()) {
            if (other != player && other.isAlive() && overlap.intersects(other.getBoundingBox())) {
               touching = true;
               break;
            }
         }

         if (touching) {
            double speed = Math.max(0.0, this.holySpeed.getValue() / 10.0);
            Vec3d push = inputVelocity(player, speed);
            player.addVelocity(push.x, 0.0, push.z);
         }
      }
   }

   private static Vec3d inputVelocity(ClientPlayerEntity player, double speed) {
      Vec2f move = player.input.getMovementInput();
      float forward = move.y;
      float strafe = move.x;
      float yaw = player.getYaw();
      if (forward != 0.0F) {
         if (strafe > 0.0F) {
            yaw += forward > 0.0F ? -45.0F : 45.0F;
         } else if (strafe < 0.0F) {
            yaw += forward > 0.0F ? 45.0F : -45.0F;
         }

         strafe = 0.0F;
         forward = forward > 0.0F ? 1.0F : -1.0F;
      }

      double sin = Math.sin(Math.toRadians((double)(yaw + 90.0F)));
      double cos = Math.cos(Math.toRadians((double)(yaw + 90.0F)));
      return new Vec3d((double)forward * speed * cos + (double)strafe * speed * sin, 0.0, (double)forward * speed * sin - (double)strafe * speed * cos);
   }

   private void tickMetaHvH(ClientPlayerEntity player) {
      if (!player.isGliding()) {
         float speed;
         if (this.metaMode.is("Custom")) {
            speed = this.metaSpeed.getValue().floatValue();
         } else {
            speed = defaultMetaSpeed(player);
         }

         if (player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            speed *= 0.835F;
         }

         if (!player.isOnGround()) {
            speed *= 1.435F;
         }

         if (this.metaDamageBoost.getValue() && this.consumeDamageBoost()) {
            speed += this.metaBoostAmount.getValue().floatValue() / 10.0F;
         }

         if (wearsBoostedHead(player)) {
            speed /= 1.3F;
         }

         double radians = movementDirection(player, true);
         Vec3d movement = player.getVelocity();
         player.setVelocity(-Math.sin(radians) * (double)speed, movement.y, Math.cos(radians) * (double)speed);
      }
   }

   private static float defaultMetaSpeed(ClientPlayerEntity player) {
      boolean totem = holdsMetaTotem(player);
      StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
      if (speedEffect == null) {
         return totem ? 0.2924F : 0.24480002F;
      } else {
         return switch (speedEffect.getAmplifier()) {
            case 1 -> totem ? 0.43F : 0.36F;
            case 2 -> totem ? 0.49665F : 0.41598004F;
            default -> totem ? 0.2924F : 0.24480002F;
         };
      }
   }

   private static boolean holdsMetaTotem(ClientPlayerEntity player) {
      String name = player.getOffHandStack().getName().getString();

      for (String totem : META_TOTEMS) {
         if (name.contains(totem)) {
            return true;
         }
      }

      return false;
   }

   private boolean consumeDamageBoost() {
      if (!this.damageBoosted) {
         return false;
      } else if ((double)(System.currentTimeMillis() - this.damageBoostStart) >= this.metaBoostDuration.getValue()) {
         this.damageBoosted = false;
         return false;
      } else {
         return true;
      }
   }

   private static boolean wearsBoostedHead(ClientPlayerEntity player) {
      ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
      if (!head.isOf(Items.PLAYER_HEAD)) {
         return false;
      } else {
         NbtComponent customData = (NbtComponent)head.get(DataComponentTypes.CUSTOM_DATA);
         if (customData == null) {
            return false;
         } else {
            String nbt = customData.copyNbt().toString();
            if (!nbt.contains("AttributeModifiers")) {
               return false;
            } else {
               for (double armour : META_HEAD_ARMOUR) {
                  if (nbt.contains("Amount:" + armour + "d")) {
                     return true;
                  }
               }

               return false;
            }
         }
      }
   }

   private static double movementDirection(ClientPlayerEntity player, boolean radians) {
      Vec2f move = player.input.getMovementInput();
      float forward = move.y;
      float strafe = move.x;
      float yaw = player.getYaw();
      if (forward < 0.0F) {
         yaw += 180.0F;
      }

      float strafeScale = 1.0F;
      if (forward < 0.0F) {
         strafeScale = -0.5F;
      } else if (forward > 0.0F) {
         strafeScale = 0.5F;
      }

      if (strafe > 0.0F) {
         yaw -= 90.0F * strafeScale;
      }

      if (strafe < 0.0F) {
         yaw += 90.0F * strafeScale;
      }

      return radians ? Math.toRadians((double)yaw) : (double)yaw;
   }

   private void setTimer(float multiplier) {
      TimerUtil.setTimer(multiplier);
      this.timerTouched = true;
   }

   private void applyFpsOverride(boolean wanted) {
      if (!wanted) {
         this.restoreFps();
      } else {
         int limit = this.fpsLimit.getValue().intValue();
         if (!this.fpsOverridden) {
            this.savedFpsLimit = (Integer)mc.options.getMaxFps().getValue();
            this.fpsOverridden = true;
         }

         if ((Integer)mc.options.getMaxFps().getValue() != limit) {
            mc.options.getMaxFps().setValue(limit);
         }
      }
   }

   private void restoreFps() {
      if (this.fpsOverridden) {
         mc.options.getMaxFps().setValue(this.savedFpsLimit);
         this.fpsOverridden = false;
      }
   }
}
