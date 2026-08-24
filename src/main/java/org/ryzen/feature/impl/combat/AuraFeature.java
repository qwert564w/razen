package org.ryzen.feature.impl.combat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.context.RotationContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.event.events.input.PlayerInputEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.packet.PacketSendEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorMode;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.utils.combat.AttackReach;
import org.ryzen.utils.combat.AttackTiming;
import org.ryzen.utils.combat.AuraAttackController;
import org.ryzen.utils.combat.AuraRaycast;
import org.ryzen.utils.combat.CombatTargets;
import org.ryzen.utils.combat.LocalPlayerHistory;
import org.ryzen.utils.combat.ServerSprintTracker;
import org.ryzen.utils.combat.SprintManager;
import org.ryzen.utils.combat.TargetFilter;
import org.ryzen.utils.combat.TargetUtil;
import org.ryzen.utils.combat.neuro.NeuroManager;
import org.ryzen.utils.combat.rotations.AuraRotation;
import org.ryzen.utils.combat.rotations.BuilderRotation;
import org.ryzen.utils.combat.rotations.ExpensiveRotation;
import org.ryzen.utils.combat.rotations.GrimRotation;
import org.ryzen.utils.combat.rotations.HolyWorldThreeRotation;
import org.ryzen.utils.combat.rotations.MatrixVulcanRotation;
import org.ryzen.utils.combat.rotations.PolarRotation;
import org.ryzen.utils.combat.rotations.SlothRotation;
import org.ryzen.utils.combat.rotations.SmoothRotation;
import org.ryzen.utils.combat.rotations.SolutionRotation;
import org.ryzen.utils.math.TickSimulator;

@Environment(EnvType.CLIENT)
public final class AuraFeature extends Feature implements MinecraftContext {
   private static final String PRIORITY_DISTANCE = "Distance";
   private static final String PRIORITY_HEALTH = "Health";
   private static final String PRIORITY_ARMOR = "Armor";
   private static final String PRIORITY_FOV = "FOV";
   private static final String PRIORITY_ALL = "All";
   private static final String ROTATION_MATRIX_VULCAN = "Matrix Vulcan";
   private static final String ROTATION_GRIM = "Grim";
   private static final String ROTATION_GRIM_LEGACY = "Grim 1";
   private static final String ROTATION_SMOOTH = "Smooth";
   private static final String ROTATION_POLAR = "Polar";
   private static final String ROTATION_SOLUTION = "Solution";
   private static final String ROTATION_NEURO_NOT_AI = "NeuroNotAiTreinig";
   private static final String ROTATION_SLOTH = "Sloth";
   private static final String ROTATION_HOLY_WORLD_3 = "HolyWorld 3";
   private static final String ROTATION_SPOOKY_TIME_2 = "SpookyTime 2";
   private static final String ROTATION_NEURO = "Neuro";
   private static final String ROTATION_BUILDER = "Builder";
   private static final String ROTATION_FUNTIME_NEW = "FunTime New";
   private static final String ROTATION_FUNTIME_FOV = "FunTime FOV";
   private static final String ROTATION_LEGIT = "Legit";
   private static final String TARGET_ESP_MARKER = "Marker";
   private static final String TARGET_ESP_GHOSTS = "Ghosts";
   private static final String TARGET_ESP_CIRCLE = "Circle";
   private static final String MOVE_CORRECTION_OFF = "Off";
   private static final String MOVE_CORRECTION_CAMERA = "Camera";
   private static final int PREDICTION_SCAN_TICKS = 3;
   private static final int WATER_PREDICTION_SCAN_TICKS = 8;
   private static final double AIM_JITTER_RANGE = 0.3;
   private static final double BUNNY_HOP_FALL_SPEED = -0.2000000000020557;
   public final NumberSetting attackRange = this.register(new NumberSetting("Attack Range", 0.0, -2.0, 3.0, 0.5, " blocks").warning(0.5, 2.5));
   public final NumberSetting aimRange = this.register(new NumberSetting("Aim Range", 1.0, 0.0, 5.0, 0.5, " blocks"));
   public final ModeSetting targetEsp = this.register(new ModeSetting("Target ESP", "Marker", "Marker", "Ghosts", "Circle"));
   public final ModeSetting targetEspColorMode = this.register(ColorMode.setting());
   public final ColorSetting targetEspColor = this.register(
      new ColorSetting("Target ESP Color", -15400961).visibleWhen(() -> ColorMode.isCustom(this.targetEspColorMode))
   );
   public final MultiSelectSetting targets = this.register(
      new MultiSelectSetting(
         "Targets", List.of("Players", "Naked Players"), "Players", "Friends", "Naked Players", "Invisibles", "Monsters", "Animals", "Villagers"
      )
   );
   public final BooleanSetting criticalsOnly = this.register(new BooleanSetting("Only Criticals", true));
   public final BooleanSetting smartCriticals = this.register(
      new BooleanSetting("Smart Criticals", true).configKey("combat.attackaura.critsWithSpace").visibleWhen(() -> this.criticalsOnly.getValue())
   );
   public final BooleanSetting tpsSync = this.register(new BooleanSetting("TPS Sync", true).configKey("combat.attackaura.tpsSync"));
   public final BooleanSetting dontHitWhileEating = this.register(
      new BooleanSetting("Don't Hit While Eating", true).configKey("combat.attackaura.noHitWhileEatingOffhand")
   );
   public final BooleanSetting releaseShield = this.register(new BooleanSetting("Release Shield", true).configKey("combat.attackaura.unPressShield"));
   public final BooleanSetting breakShield = this.register(new BooleanSetting("Break Shield", false).configKey("combat.attackaura.breakShield"));
   public final NumberSetting fov = this.register(new NumberSetting("FOV", 360.0, 30.0, 360.0, 5.0, "").configKey("combat.attackaura.fov"));
   public final ModeSetting movementCorrection = this.register(
      new ModeSetting("Move Correction", "Camera", "Off", "Camera").configKey("combat.attackaura.moveCorrection")
   );
   public final BooleanSetting throughWalls = this.register(new BooleanSetting("Through Walls", false));
   public final ModeSetting priority = this.register(new ModeSetting("Priority", "All", "Distance", "Health", "Armor", "FOV", "All"));
   public final ModeSetting rotation = this.register(
      new ModeSetting(
            "Rotation",
            "FunTime New",
            "FunTime New",
            "FunTime FOV",
            "Legit",
            "Matrix Vulcan",
            "Grim",
            "HolyWorld 3",
            "SpookyTime 2",
            "Smooth",
            "Polar",
            "Solution",
            "NeuroNotAiTreinig",
            "Sloth",
            "Neuro",
            "Builder"
         )
         .chips()
         .renamedFrom("Grim 1", "Grim")
         .renamedFrom("ФанТайм", "FunTime New")
         .renamedFrom("ФанТайм ФОВ", "FunTime FOV")
         .renamedFrom("Легит", "Legit")
   );
   private final AuraRotation matrixVulcanRotation = new MatrixVulcanRotation();
   private final AuraRotation grimRotation = new GrimRotation();
   private final AuraRotation holyWorldThreeRotation = new HolyWorldThreeRotation();
   private final AuraRotation slothRotation = new SlothRotation();
   private final AuraRotation smoothRotation = new SmoothRotation();
   private final AuraRotation polarRotation = new PolarRotation();
   private final AuraRotation solutionRotation = new SolutionRotation();
   private final AuraRotation neuroNotAiRotation = new PolarRotation();
   private final AuraRotation spookyTimeTwoRotation = ExpensiveRotation.spookyTime();
   private final AuraRotation neuroRotation = NeuroManager.activeRotation();
   private final AuraRotation builderRotation = new BuilderRotation();
   private final AttackTiming timing = new AttackTiming();
   private final AuraAttackController attackController = new AuraAttackController();
   private LivingEntity target;
   private boolean targetGraced;
   private Vec3d aimJitter = Vec3d.ZERO;

   public AuraFeature() {
      super("Aura", "Automatically attacks entities around you.", FeatureCategory.COMBAT, 82);
   }

   @Override
   protected void onDisable() {
      this.resetCombatState(mc.player);
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.resetCombatState(event.getClient().player);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient mc = event.getClient();
      ClientPlayerEntity player = mc.player;
      ClientWorld level = mc.world;
      if (player == null || level == null || !player.isAlive()) {
         this.resetCombatState(player);
      } else if (pveControlsCombat()) {
         this.resetCombatState(player);
      } else {
         this.attackController.tick(player);
         this.selectTarget(mc, player, level);
         if (this.target == null) {
            this.clearRotationState();
         } else {
            int predictionTicks = this.ticksUntilAttackReady(player);
            TickSimulator.SimState predictedState = TickSimulator.getPredictedState(this.target, predictionTicks, level);
            Vec3d aimPosition = predictionTicks == 0 ? this.target.getEntityPos() : predictedState.pos;
            Vec3d aimPoint = AuraRaycast.findAimPoint(
               player, this.target, aimPosition, this.aimRangeBlocks(player), this.throughWalls.getValue(), this.aimJitter
            );
            if (aimPoint == null) {
               this.clearRotationState();
               this.target = null;
            } else {
               boolean attackLikely = !this.targetGraced
                  && AuraRaycast.predictedHitboxDistanceSqr(player, this.target, aimPosition) <= squared(this.attackRangeBlocks(player));
               this.selectedRotation().tick(player, this.target, aimPoint, attackLikely);
            }
         }
      }
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPost()) {
         if (!pveControlsCombat()) {
            this.timing.tick();
            ClientPlayerEntity player = event.getPlayer();
            if (player != null && this.target != null && this.target.isAlive()) {
               if (this.canAttack(player, 1)) {
                  SprintManager.markAttackImminent();
                  if (this.releaseShield.getValue()) {
                     this.attackController.releaseShieldBeforeAttack(player);
                  }
               }

               float serverYaw = player.getYaw();
               float serverPitch = player.getPitch();
               boolean rotationOnTarget = AuraRaycast.rotationIntersectsTarget(
                  player,
                  this.target,
                  serverYaw,
                  serverPitch,
                  this.attackRangeBlocks(player),
                  AttackReach.min(player),
                  AttackReach.margin(player),
                  this.throughWalls.getValue()
               );
               if (rotationOnTarget && this.canAttack(player, 0) && !ServerSprintTracker.isServerSprinting()) {
                  if (this.attackController.attack(player, this.target, this.breakShield.getValue())) {
                     this.timing.onAttack();
                     this.rollAimJitter();
                     this.selectedRotation().onAttack();
                  }
               }
            }
         }
      }
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPhase() == PacketSendEvent.Phase.POST && this.target != null) {
         if (event.getPacket() instanceof HandSwingC2SPacket || event.getPacket() instanceof UpdateSelectedSlotC2SPacket) {
            boolean usePattern = !this.rotation.is("Grim");
            this.timing.onSwingPacket(usePattern, this.tpsSync.getValue());
         }
      }
   }

   @EventTarget
   public void onPlayerInput(PlayerInputEvent event) {
      ClientPlayerEntity player = mc.player;
      if (player != null
         && this.target != null
         && this.target.isAlive()
         && this.movementCorrection.is("Camera")
         && RotationContext.isActive()
         && !(event.getMoveVector().lengthSquared() <= 1.0E-6F)) {
         Vec2f input = event.getMoveVector();
         double radians = Math.toRadians((double)(RotationContext.getFreeYaw() - player.getYaw()));
         float cosine = (float)Math.cos(radians);
         float sine = (float)Math.sin(radians);
         event.setMoveVector(new Vec2f(input.x * cosine - input.y * sine, input.y * cosine + input.x * sine));
      }
   }

   private void selectTarget(MinecraftClient mc, ClientPlayerEntity player, ClientWorld level) {
      TargetFilter filter = CombatTargets.groups(TargetFilter.builder(), this.targets)
         .distanceWeight(this.distanceWeight())
         .healthWeight(this.healthWeight())
         .armorWeight(this.armorWeight())
         .fovWeight(this.fovWeight())
         .build();
      double attackRangeBlocks = this.attackRangeBlocks(player);
      double aimRangeBlocks = this.aimRangeBlocks(player);
      double attackRangeSqr = squared(attackRangeBlocks);
      double aimRangeSqr = squared(aimRangeBlocks);
      Predicate<LivingEntity> visibleTarget = this.throughWalls.getValue()
         ? entity -> true
         : entity -> AuraRaycast.canSeeTarget(player, entity, aimRangeBlocks);
      Predicate<LivingEntity> eligibleTarget = entity -> this.isWithinFov(player, entity) && visibleTarget.test(entity);
      if (this.target != null && TargetUtil.isValidTarget(player, this.target, aimRangeSqr, filter) && eligibleTarget.test(this.target)) {
         this.targetGraced = player.squaredDistanceTo(this.target) > attackRangeSqr;
      } else {
         LivingEntity picked = null;
         if (mc.targetedEntity instanceof LivingEntity crosshairTarget
            && TargetUtil.isValidTarget(player, crosshairTarget, attackRangeSqr, filter)
            && eligibleTarget.test(crosshairTarget)) {
            picked = crosshairTarget;
         }

         if (picked == null) {
            picked = TargetUtil.getBestTarget(player, level, attackRangeBlocks, filter, eligibleTarget);
         }

         boolean graced = false;
         if (picked == null) {
            picked = TargetUtil.getBestTarget(player, level, aimRangeBlocks, filter, eligibleTarget);
            graced = picked != null;
         }

         this.target = picked;
         this.targetGraced = graced;
      }
   }

   private boolean canAttack(ClientPlayerEntity player, int ticksAhead) {
      for (int tick = 0; tick <= ticksAhead; tick++) {
         if (this.canAttackAt(player, tick)) {
            return true;
         }
      }

      return false;
   }

   private boolean canAttackAt(ClientPlayerEntity player, int tick) {
      if (this.target == null || !this.target.isAlive()) {
         return false;
      } else if (mc.currentScreen instanceof GenericContainerScreen) {
         return false;
      } else if (player.isRiding()) {
         return false;
      } else if (this.usingItemBlocksAttack(player)) {
         return false;
      } else if (player.isBelowMinimumAttackCharge(player.getMainHandStack(), tick)) {
         return false;
      } else {
         return AuraRaycast.findAimPoint(
                  player, this.target, this.target.getEntityPos(), this.attackRangeBlocks(player), this.throughWalls.getValue(), this.aimJitter
               )
               == null
            ? false
            : this.canCrit(player, tick);
      }
   }

   private boolean canCrit(ClientPlayerEntity player, int ticksAhead) {
      if (!this.timing.cooldownReady(player, ticksAhead)) {
         return false;
      } else if (!this.criticalsOnly.getValue()) {
         return true;
      } else {
         TickSimulator.SimState sim = TickSimulator.simulateLocalPlayer(player, ticksAhead, player.getEntityWorld());
         boolean jumpHeld = player.input != null && player.input.playerInput.jump();
         if (!sim.inWater) {
            if (this.smartCriticals.getValue() && sim.onGround && !jumpHeld) {
               return true;
            } else {
               return player.getMainHandStack().isOf(Items.MACE)
                  ? MaceItem.shouldDealAdditionalDamage(player)
                  : this.criticalStateValid(player, ticksAhead, sim);
            }
         } else {
            return sim.swimming || sim.submergedInWater;
         }
      }
   }

   private boolean criticalStateValid(ClientPlayerEntity player, int ticksAhead, TickSimulator.SimState sim) {
      if (player.hasStatusEffect(StatusEffects.LEVITATION)
         || player.hasStatusEffect(StatusEffects.BLINDNESS)
         || player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
         return true;
      } else if (!sim.inCobweb && !sim.inLava && !sim.climbing && !player.getAbilities().flying) {
         TickSimulator.SimState next = TickSimulator.simulateLocalPlayer(player, ticksAhead + 1, player.getEntityWorld());
         TickSimulator.SimState afterNext = TickSimulator.simulateLocalPlayer(player, ticksAhead + 2, player.getEntityWorld());
         boolean landingWithFall = sim.fallDistance > 1.0F && next.onGround;
         boolean fallMatchesVelocity = sim.fallDistance == (float)sim.motion.y && next.onGround;
         boolean bunnyHopLanding = sim.motion.y < -0.2000000000020557
            && afterNext.onGround
            && (
               LocalPlayerHistory.verticalCollisionBelow(4, player.groundCollision)
                  || LocalPlayerHistory.verticalCollisionBelow(5, player.groundCollision)
                  || LocalPlayerHistory.verticalCollisionBelow(6, player.groundCollision)
                  || LocalPlayerHistory.verticalCollisionBelow(7, player.groundCollision)
            );
         return !sim.onGround && sim.fallDistance > 0.0F && !bunnyHopLanding && !fallMatchesVelocity && !landingWithFall;
      } else {
         return true;
      }
   }

   private boolean usingItemBlocksAttack(ClientPlayerEntity player) {
      if (!player.isUsingItem()) {
         return false;
      } else {
         UseAction animation = player.getActiveItem().getUseAction();
         boolean blockingAnimation = animation == UseAction.EAT
            || animation == UseAction.DRINK
            || animation == UseAction.CROSSBOW
            || animation == UseAction.SPEAR
            || animation == UseAction.TRIDENT
            || animation == UseAction.BOW;
         if (!blockingAnimation) {
            return false;
         } else {
            return player.getActiveHand() == Hand.MAIN_HAND ? true : this.dontHitWhileEating.getValue();
         }
      }
   }

   private int ticksUntilAttackReady(ClientPlayerEntity player) {
      int horizon = player.isTouchingWater() ? 8 : 3;

      for (int tick = 0; tick < horizon; tick++) {
         if (this.canAttackAt(player, tick)) {
            return tick;
         }
      }

      return horizon;
   }

   private void rollAimJitter() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      this.aimJitter = new Vec3d(random.nextDouble(-0.3, 0.3), random.nextDouble(-0.3, 0.3), random.nextDouble(-0.3, 0.3));
   }

   public static AuraFeature getMarkerFeature() {
      return FeatureManager.INSTANCE.getFeature("Aura") instanceof AuraFeature auraFeature ? auraFeature : null;
   }

   public LivingEntity getCurrentTarget() {
      return this.isEnabled() ? this.target : null;
   }

   public int getMarkerColor() {
      return ColorMode.resolve(this.targetEspColorMode, this.targetEspColor);
   }

   public boolean usesGhostTargetEsp() {
      return "Ghosts".equals(this.targetEsp.getValue());
   }

   public boolean usesCircleTargetEsp() {
      return "Circle".equals(this.targetEsp.getValue());
   }

   public boolean shouldAutoJump(ClientPlayerEntity player) {
      return this.isEnabled() && player != null && this.target != null && this.target.isAlive() && !this.targetGraced
         ? this.criticalsOnly.getValue() && player.isOnGround()
         : false;
   }

   private boolean isWithinFov(ClientPlayerEntity player, LivingEntity entity) {
      double cone = this.fov.getValue();
      if (cone >= 360.0) {
         return true;
      } else {
         float viewYaw = RotationContext.isActive() ? RotationContext.getFreeYaw() : player.getYaw();
         float viewPitch = RotationContext.isActive() ? RotationContext.getFreePitch() : player.getPitch();
         Vec3d look = Vec3d.fromPolar(viewPitch, viewYaw);
         Vec3d toTarget = entity.getBoundingBox().getCenter().subtract(player.getEyePos()).normalize();
         return look.dotProduct(toTarget) > Math.cos(Math.toRadians(cone * 0.5));
      }
   }

   private void resetCombatState(ClientPlayerEntity player) {
      this.clearRotationState();
      SprintManager.reset();
      this.target = null;
      this.targetGraced = false;
      this.timing.reset();
      this.attackController.reset(player);
   }

   private void clearRotationState() {
      RotationContext.clear();
      this.resetRotations();
   }

   private AuraRotation selectedRotation() {
      String var1 = this.rotation.getValue();

      return switch (var1) {
         case "FunTime New", "FunTime FOV" -> this.matrixVulcanRotation;
         case "Legit" -> this.smoothRotation;
         case "Grim" -> this.grimRotation;
         case "HolyWorld 3" -> this.holyWorldThreeRotation;
         case "Sloth" -> this.slothRotation;
         case "Smooth" -> this.smoothRotation;
         case "Polar" -> this.polarRotation;
         case "Solution" -> this.solutionRotation;
         case "NeuroNotAiTreinig" -> this.neuroNotAiRotation;
         case "SpookyTime 2" -> this.spookyTimeTwoRotation;
         case "Neuro" -> this.neuroRotation;
         case "Builder" -> this.builderRotation;
         default -> this.matrixVulcanRotation;
      };
   }

   private void resetRotations() {
      this.matrixVulcanRotation.reset();
      this.grimRotation.reset();
      this.holyWorldThreeRotation.reset();
      this.slothRotation.reset();
      this.smoothRotation.reset();
      this.polarRotation.reset();
      this.solutionRotation.reset();
      this.neuroNotAiRotation.reset();
      this.spookyTimeTwoRotation.reset();
      this.builderRotation.reset();
   }

   private double attackRangeBlocks(ClientPlayerEntity player) {
      double baseRange = AttackReach.max(player);
      HitBoxesFeature hitBoxes = HitBoxesFeature.getEnabled();
      double extraRange = hitBoxes == null ? 0.0 : hitBoxes.getAuraExpansion();
      return Math.max(0.0, baseRange + this.attackRange.getValue() + extraRange);
   }

   private double aimRangeBlocks(ClientPlayerEntity player) {
      return this.attackRangeBlocks(player) + this.aimRange.getValue();
   }

   private static double squared(double value) {
      return value * value;
   }

   private static boolean pveControlsCombat() {
      return PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.COMBAT)
         || PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.ROTATION)
         || PveAutomationCoordinator.INSTANCE.isClaimed(AutomationResource.INVENTORY);
   }

   private double distanceWeight() {
      String var1 = this.priority.getValue();

      return switch (var1) {
         case "Distance" -> 1.0;
         case "All" -> 0.5;
         default -> 0.0;
      };
   }

   private double healthWeight() {
      String var1 = this.priority.getValue();

      return switch (var1) {
         case "Health" -> 1.0;
         case "All" -> 1.2;
         default -> 0.0;
      };
   }

   private double armorWeight() {
      String var1 = this.priority.getValue();

      return switch (var1) {
         case "Armor" -> 1.0;
         case "All" -> 1.0;
         default -> 0.0;
      };
   }

   private double fovWeight() {
      String var1 = this.priority.getValue();

      return switch (var1) {
         case "FOV" -> 1.0;
         case "All" -> 0.25;
         default -> 0.0;
      };
   }
}
