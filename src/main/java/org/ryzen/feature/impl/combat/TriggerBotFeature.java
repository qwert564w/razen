package org.ryzen.feature.impl.combat;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.game.PlayerTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.combat.AttackWindow;
import org.ryzen.utils.combat.CombatTargets;
import org.ryzen.utils.combat.ServerSprintTracker;
import org.ryzen.utils.combat.ServerTickSync;
import org.ryzen.utils.combat.SprintManager;
import org.ryzen.utils.combat.TargetFilter;
import org.ryzen.utils.combat.TargetUtil;

@Environment(EnvType.CLIENT)
public final class TriggerBotFeature extends Feature implements MinecraftContext {
   private static final float READY_CHARGE = 0.9F;
   private static final String SPRINT_LEGIT = "Legit";
   private static final String SPRINT_RAGE = "Rage";
   private static final String BLOCK_CONTAINER = "Container Open";
   private static final String BLOCK_EATING = "Using Item";
   private static final int MISS_MIN = 30;
   private static final int MISS_MAX = 41;
   public final MultiSelectSetting targets = this.register(
      new MultiSelectSetting(
         "Targets", List.of("Players", "Naked Players"), "Players", "Friends", "Naked Players", "Invisibles", "Monsters", "Animals", "Villagers"
      )
   );
   public final MultiSelectSetting blockers = this.register(
      new MultiSelectSetting("Do Not Attack While", List.of("Container Open"), "Container Open", "Using Item")
   );
   public final ModeSetting sprintReset = this.register(new ModeSetting("Sprint Reset", "Legit", "Legit", "Rage"));
   public final NumberSetting distance = this.register(new NumberSetting("Distance", 3.0, 0.5, 5.0, 0.1, " blocks"));
   public final BooleanSetting noWalls = this.register(new BooleanSetting("No Walls", false));
   public final BooleanSetting wallBypass = this.register(new BooleanSetting("Wall Bypass", true).visibleWhen(() -> !this.noWalls.getValue()));
   public final BooleanSetting criticalsOnly = this.register(new BooleanSetting("Only Criticals", false));
   public final BooleanSetting whileJumping = this.register(new BooleanSetting("Only While Jumping", false).visibleWhen(this.criticalsOnly::getValue));
   public final BooleanSetting tpsSync = this.register(new BooleanSetting("TPS Sync", false));
   public final BooleanSetting missSometimes = this.register(new BooleanSetting("Miss Sometimes", false));
   public final BooleanSetting breakShield = this.register(new BooleanSetting("Break Shields", true));
   public final NumberSetting delay = this.register(new NumberSetting("Delay", 0.0, 0.0, 1000.0, 10.0, " ms"));
   public final BooleanSetting randomDelay = this.register(new BooleanSetting("Random Delay", false).visibleWhen(() -> this.delay.getValue() > 0.0));
   private LivingEntity currentTarget;
   private boolean blockedByWall;
   private long nextAttackAtMs;
   private int hitsSinceMiss;
   private int missAfterHits = ThreadLocalRandom.current().nextInt(30, 41);

   public TriggerBotFeature() {
      super("TriggerBot", "Attacks the entity you are aiming at", FeatureCategory.COMBAT, -1);
   }

   public static TriggerBotFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(TriggerBotFeature.class);
   }

   public LivingEntity getCurrentTarget() {
      return this.isEnabled() ? this.currentTarget : null;
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.reset();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient mc = event.getClient();
      ClientPlayerEntity player = mc.player;
      LivingEntity next = player != null && mc.world != null ? this.findTarget(mc, player) : null;
      if (next != this.currentTarget) {
         this.currentTarget = next;
         this.scheduleNextAttack();
      }

      this.blockedByWall = next != null && this.noWalls.getValue() && !this.hasLineOfSight(mc, player, next);
   }

   @EventTarget
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.isPost()) {
         ClientPlayerEntity player = event.getPlayer();
         LivingEntity target = this.currentTarget;
         if (player != null && target != null && target.isAlive() && !this.blockedByWall && !this.isBlocked()) {
            if (this.canAttack(player, 1) && System.currentTimeMillis() >= this.nextAttackAtMs) {
               boolean rage = this.sprintReset.is("Rage");
               if (!rage) {
                  SprintManager.markAttackImminent();
                  if (!this.canAttack(player, 0) || ServerSprintTracker.isServerSprinting()) {
                     return;
                  }
               } else if (!this.canAttack(player, 0)) {
                  return;
               }

               if (this.findTarget(mc, player) == target) {
                  if (rage && player.isSprinting() && !isInFluidOrGliding(player)) {
                     player.setSprinting(false);
                     if (player.networkHandler != null) {
                        player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.STOP_SPRINTING));
                     }
                  }

                  if (this.missSometimes.getValue() && ++this.hitsSinceMiss >= this.missAfterHits) {
                     this.hitsSinceMiss = 0;
                     this.missAfterHits = ThreadLocalRandom.current().nextInt(30, 41);
                     player.swingHand(Hand.MAIN_HAND);
                     this.scheduleNextAttack();
                  } else {
                     if (!this.noWalls.getValue() && this.wallBypass.getValue() && !player.canSee(target)) {
                        this.sendWallBypass(player, target);
                     }

                     AttackWindow.attack(target);
                     if (this.breakShield.getValue()) {
                        this.breakShield(player, target);
                     }

                     this.scheduleNextAttack();
                  }
               }
            }
         }
      }
   }

   private LivingEntity findTarget(MinecraftClient mc, ClientPlayerEntity player) {
      double range = this.distance.getValue();
      Vec3d eye = player.getCameraPosVec(1.0F);
      Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(range));
      LivingEntity best = null;
      double bestDistance = Double.MAX_VALUE;

      for (LivingEntity candidate : TargetUtil.getTargets(player, mc.world, range + 2.0, this.filter())) {
         Box box = candidate.getBoundingBox();
         if (box.contains(eye) || !box.raycast(eye, end).isEmpty()) {
            double distance = player.squaredDistanceTo(candidate);
            if (distance < bestDistance) {
               bestDistance = distance;
               best = candidate;
            }
         }
      }

      return best;
   }

   private boolean hasLineOfSight(MinecraftClient mc, ClientPlayerEntity player, LivingEntity target) {
      Vec3d eye = player.getCameraPosVec(1.0F);
      Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(this.distance.getValue()));
      Optional<Vec3d> entry = target.getBoundingBox().raycast(eye, end);
      Vec3d contact = entry.orElse(eye);
      return mc.world.raycast(new RaycastContext(eye, contact, ShapeType.OUTLINE, FluidHandling.NONE, player)).getType() == Type.MISS;
   }

   private void sendWallBypass(ClientPlayerEntity player, LivingEntity target) {
      if (player.networkHandler != null) {
         Vec3d eye = player.getEyePos();
         Vec3d delta = target.getEyePos().subtract(eye);
         double length = delta.length();
         if (!(length < 0.01)) {
            int steps = Math.max(1, (int)Math.ceil(length * 2.0));
            Set<BlockPos> visited = new HashSet<>();

            for (int step = 0; step <= steps; step++) {
               double progress = (double)step / (double)steps;
               BlockPos pos = BlockPos.ofFloored(eye.x + delta.x * progress, eye.y + delta.y * progress, eye.z + delta.z * progress);
               if (visited.add(pos) && !player.getEntityWorld().getBlockState(pos).isAir()) {
                  Direction face = dominantFace(eye.subtract(Vec3d.ofCenter(pos)));
                  player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, face, 0));
                  player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, face, 0));
               }
            }
         }
      }
   }

   private static Direction dominantFace(Vec3d offset) {
      double absX = Math.abs(offset.x);
      double absY = Math.abs(offset.y);
      double absZ = Math.abs(offset.z);
      if (absY >= absX && absY >= absZ) {
         return offset.y > 0.0 ? Direction.UP : Direction.DOWN;
      } else if (absX >= absZ) {
         return offset.x > 0.0 ? Direction.EAST : Direction.WEST;
      } else {
         return offset.z > 0.0 ? Direction.SOUTH : Direction.NORTH;
      }
   }

   private void breakShield(ClientPlayerEntity player, LivingEntity target) {
      if (target.getOffHandStack().isOf(Items.SHIELD) || target.getMainHandStack().isOf(Items.SHIELD)) {
         int axeSlot = -1;

         for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof AxeItem) {
               axeSlot = slot;
               break;
            }
         }

         if (axeSlot != -1 && player.networkHandler != null && mc.interactionManager != null) {
            int originalSlot = player.getInventory().getSelectedSlot();
            if (axeSlot != originalSlot) {
               player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
               mc.interactionManager.attackEntity(player, target);
               player.swingHand(Hand.MAIN_HAND);
               player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            }
         }
      }
   }

   private void scheduleNextAttack() {
      long wait = this.delay.getValue().longValue();
      if (this.tpsSync.getValue()) {
         float tps = MathHelper.clamp(ServerTickSync.INSTANCE.effectiveTps(), 1.0F, 20.0F);
         wait = (long)Math.round((float)wait * (20.0F / tps));
      }

      if (wait > 0L && this.randomDelay.getValue()) {
         wait = ThreadLocalRandom.current().nextLong(wait / 2L + 1L, wait + 1L);
      }

      this.nextAttackAtMs = wait <= 0L ? 0L : System.currentTimeMillis() + wait;
   }

   private boolean isBlocked() {
      ClientPlayerEntity player = this.player();
      if (player == null) {
         return true;
      } else {
         return this.blockers.isSelected("Using Item") && player.isUsingItem()
            ? true
            : this.blockers.isSelected("Container Open") && mc.currentScreen instanceof HandledScreen;
      }
   }

   private boolean canAttack(ClientPlayerEntity player, int ticksAhead) {
      if (player.isBelowMinimumAttackCharge(player.getMainHandStack(), ticksAhead)) {
         return false;
      } else {
         float charge = this.tpsSync.getValue()
            ? MathHelper.clamp(20.0F / Math.max(1.0F, ServerTickSync.INSTANCE.effectiveTps()), 0.5F, 2.0F) * ((float)ticksAhead + 0.5F)
            : (float)ticksAhead + 0.5F;
         return player.getAttackCooldownProgress(charge) <= 0.9F ? false : this.canAttackEnvironment(player);
      }
   }

   private TargetFilter filter() {
      return CombatTargets.groups(TargetFilter.builder(), this.targets).build();
   }

   private boolean canAttackEnvironment(ClientPlayerEntity player) {
      if (isInFluidOrGliding(player) || player.isClimbing()) {
         return false;
      } else if (!this.criticalsOnly.getValue()) {
         return true;
      } else {
         return this.whileJumping.getValue() && !mc.options.jumpKey.isPressed() ? false : !player.isOnGround() && player.fallDistance > 0.0;
      }
   }

   private static boolean isInFluidOrGliding(ClientPlayerEntity player) {
      return player.isTouchingWater() && player.isSubmergedIn(FluidTags.WATER) || player.isInLava() || player.isSwimming() || player.isGliding();
   }

   private void reset() {
      this.currentTarget = null;
      this.blockedByWall = false;
      this.nextAttackAtMs = 0L;
      this.hitsSinceMiss = 0;
      SprintManager.reset();
   }
}
