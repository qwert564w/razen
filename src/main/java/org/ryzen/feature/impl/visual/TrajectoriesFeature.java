package org.ryzen.feature.impl.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EyeOfEnderEntity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.LlamaSpitEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.LingeringPotionEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.entity.projectile.thrown.SplashPotionEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.render.Render2DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.Theme;
import org.ryzen.utils.render.gui.Render2DUtil;
import org.ryzen.utils.render.gui.ScaleUtil;
import org.ryzen.utils.render.gui.TextAlign;
import org.ryzen.utils.render.gui.UiFontStyle;
import org.ryzen.utils.render.gui.UiFonts;
import org.ryzen.utils.render.world.WorldMeshRenderer;

@Environment(EnvType.CLIENT)
public final class TrajectoriesFeature extends Feature implements MinecraftContext {
   private static final String TARGET_SELF = "Self";
   private static final String TARGET_PLAYERS = "Players";
   private final MultiSelectSetting predictTrajectory = this.register(new MultiSelectSetting("Predict Trajectory", Set.of("Self"), "Self", "Players"));
   private final BooleanSetting showOwner = this.register(new BooleanSetting("Show Owner", true));
   private final List<TrajectoriesFeature.ScreenLine> pendingScreenLines = new ArrayList<>();
   private final List<TrajectoriesFeature.ImpactPoint> points = new ArrayList<>();
   private int tpSkipTicks;
   private static final int MAX_SIMULATION_TICKS = 300;
   private static final float DESIGN_SCALE = 1.6F;
   private static final float SCREEN_CONNECTOR_THICKNESS = 1.35F;
   private static final float TAG_WIDTH = 36.0F;
   private static final float TAG_CARD = 36.0F;
   private static final float TAG_HEIGHT = 48.0F;
   private static final float TAG_GAP = 4.0F;
   private static final float TAG_RADIUS = 10.0F;
   private static final float TAG_BORDER = 1.2F;
   private static final float TAG_PADDING = 6.0F;
   private static final float TAG_ICON = 20.0F;
   private static final float TAG_TEXT_SIZE = 8.0F;
   private static final float TAG_TEXT_ROW = 8.0F;
   private static final int IMPACT_RING_SEGMENTS = 180;
   private static final double IMPACT_RING_HALF_WIDTH = 0.004;
   private static final double IMPACT_CROSS_RADIUS_FACTOR = 0.72;
   private static final double IMPACT_CROSS_HALF_WIDTH = 0.003;
   private static final int POTION_AREA_RING_SEGMENTS = 220;
   private static final double POTION_AREA_RING_HALF_WIDTH = 0.018;
   private static final double POTION_AREA_Y_OFFSET = 0.012;
   private static final double POTION_GROUND_SEARCH_UP = 0.35;
   private static final double POTION_GROUND_SEARCH_DOWN = 2.5;
   private static final double SPLASH_POTION_RADIUS = 4.0;
   private static final double LINGERING_POTION_RADIUS = 3.0;
   private static final double SPLASH_ENTITY_VERTICAL_RANGE = 2.0;
   private static final double SPLASH_MIN_DISTANCE = 0.001;
   private static final double SPLASH_LINE_MIN_ALPHA = 0.38;
   private static final double SPLASH_LINE_MAX_ALPHA = 0.92;
   private static final float POTION_AREA_ALPHA = 0.55F;
   private static final float POTION_AREA_CONNECTOR_ALPHA = 0.67F;
   private static final double ENTITY_HIT_BOX_EXPAND = 0.028;
   private static final float ENTITY_HIT_BOX_ALPHA = 0.94F;
   private static final float ENTITY_HIT_BOX_FILL_ALPHA = 0.09F;
   private static final int SPLASH_LINE_START_COLOR = -12386427;
   private static final int SPLASH_LINE_END_COLOR = -42920;
   private static final double AIR_INERTIA = 0.99;
   private static final double THROWABLE_WATER_INERTIA = 0.8;
   private static final double ARROW_WATER_INERTIA = 0.6;
   private static final double TRIDENT_WATER_INERTIA = 0.99;
   private static final double THROWABLE_GRAVITY = 0.03;
   private static final double POTION_GRAVITY = 0.05;
   private static final double EXPERIENCE_BOTTLE_GRAVITY = 0.07;
   private static final double ARROW_GRAVITY = 0.05;

   public TrajectoriesFeature() {
      super("Trajectories", "Predicts projectile paths and impact points", FeatureCategory.VISUAL, -1);
   }

   public static TrajectoriesFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(TrajectoriesFeature.class);
   }

   @EventTarget
   public void on2DRender(Render2DEvent event) {
      if (!this.pendingScreenLines.isEmpty() || !this.points.isEmpty()) {
         float unit = ScaleUtil.toGuiPixels(1.6F, (double)mc.getWindow().getScaleFactor());
         float centerX = (float)event.getGuiGraphicsExtractor().getScaledWindowWidth() * 0.5F;
         float centerY = (float)event.getGuiGraphicsExtractor().getScaledWindowHeight() * 0.5F;
         float connectorThickness = Math.max(0.5F, 1.35F * unit);

         for (TrajectoriesFeature.ScreenLine line : this.pendingScreenLines) {
            this.drawScreenLine(event, centerX, centerY, line.x(), line.y(), connectorThickness, line.color());
         }

         for (TrajectoriesFeature.ImpactPoint point : this.points) {
            TrajectoriesFeature.ScreenPoint screen = this.projectToScreen(point.pos());
            if (screen != null) {
               this.renderImpactTag(event, point, screen.x(), screen.y(), unit);
            }
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.clear();
   }

   public void renderWorld() {
      this.pendingScreenLines.clear();
      this.points.clear();
      if (mc.world != null && mc.player != null) {
         Vec3d oldPlayerPos = mc.player.getLastRenderPos();
         double dx = mc.player.getX() - oldPlayerPos.x;
         double dy = mc.player.getY() - oldPlayerPos.y;
         double dz = mc.player.getZ() - oldPlayerPos.z;
         if (dx * dx + dy * dy + dz * dz > 36.0) {
            this.tpSkipTicks = 2;
         }

         if (this.tpSkipTicks > 0) {
            this.tpSkipTicks--;
            this.pendingScreenLines.clear();
         } else {
            List<WorldMeshRenderer.Line> lines = new ArrayList<>();
            List<WorldMeshRenderer.Ring> rings = new ArrayList<>();
            List<WorldMeshRenderer.PlaneRect> planeRects = new ArrayList<>();
            if (this.predictTrajectory.isSelected("Self")) {
               this.drawPredictionInHand(lines, rings, planeRects);
            }

            if (this.predictTrajectory.isSelected("Players")) {
               this.drawOtherPlayersPrediction(lines, rings, planeRects);
            }

            for (Entity entity : mc.world.getEntities()) {
               if ((entity instanceof ProjectileEntity || entity instanceof ItemEntity) && !this.isStationary(entity)) {
                  Vec3d motion = entity.getVelocity();
                  float partialTicks = mc.getRenderTickCounter().getTickProgress(false);
                  double startX = MathHelper.lerp((double)partialTicks, entity.lastX, entity.getX());
                  double startY = MathHelper.lerp((double)partialTicks, entity.lastY, entity.getY());
                  double startZ = MathHelper.lerp((double)partialTicks, entity.lastZ, entity.getZ());
                  Vec3d pos = new Vec3d(startX, startY, startZ);

                  for (int tick = 0; tick < 300; tick++) {
                     TrajectoriesFeature.TrajectoryStep step;
                     if (entity instanceof ProjectileEntity projectile) {
                        step = this.simulateProjectileStep(projectile, pos, motion);
                     } else {
                        Vec3d nextPos = pos.add(motion);
                        Vec3d nextMotion = this.calculateMotion(entity, pos, motion);
                        HitResult hit = this.raycastBlock(pos, nextPos, entity);
                        step = new TrajectoriesFeature.TrajectoryStep(
                           hit.getType() != Type.MISS ? hit.getPos() : nextPos, nextMotion, hit.getType() != Type.MISS ? hit : null
                        );
                     }

                     Vec3d renderEnd = step.hitResult() != null ? step.hitResult().getPos() : step.nextPos();
                     float alpha = MathHelper.clamp((float)tick / 7.0F, 0.0F, 1.0F);
                     lines.add(new WorldMeshRenderer.Line(pos, renderEnd, ColorUtil.applyAlpha(this.animatedAccentColor(tick), alpha)));
                     if (step.hitResult() != null || step.nextPos().y < -128.0) {
                        this.registerImpact(entity, renderEnd, tick);
                        break;
                     }

                     pos = step.nextPos();
                     motion = step.nextMotion();
                  }
               }
            }

            WorldMeshRenderer.render(new WorldMeshRenderer.WorldMesh(lines, rings, planeRects));
         }
      } else {
         this.clear();
      }
   }

   @Override
   protected void onDisable() {
      this.clear();
   }

   private void drawPredictionInHand(List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.Ring> rings, List<WorldMeshRenderer.PlaneRect> planeRects) {
      if (mc.world != null && mc.player != null) {
         this.drawPredictionForPlayer(mc.player, true, lines, rings, planeRects);
      }
   }

   private void drawOtherPlayersPrediction(List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.Ring> rings, List<WorldMeshRenderer.PlaneRect> planeRects) {
      if (mc.world != null && mc.player != null) {
         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && player.isAlive() && !player.isRemoved()) {
               this.drawPredictionForPlayer(player, false, lines, rings, planeRects);
            }
         }
      }
   }

   private void drawPredictionForPlayer(
      PlayerEntity player,
      boolean screenAnchored,
      List<WorldMeshRenderer.Line> lines,
      List<WorldMeshRenderer.Ring> rings,
      List<WorldMeshRenderer.PlaneRect> planeRects
   ) {
      if (mc.world != null && player != null) {
         ItemStack activeStack = player.getActiveItem();
         ItemStack[] stacks = new ItemStack[]{player.getMainHandStack(), player.getOffHandStack()};

         for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
               float partialTicks = mc.getRenderTickCounter().getTickProgress(false);
               float pitch = MathHelper.lerp(partialTicks, player.lastPitch, player.getPitch());
               float yaw = MathHelper.lerp(partialTicks, player.lastYaw, player.getYaw());
               List<TrajectoriesFeature.Prediction> predictions = new ArrayList<>();
               Item item = stack.getItem();
               if (item instanceof ExperienceBottleItem) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new ExperienceBottleEntity(mc.world, player, stack), 0.7, pitch, yaw, -20.0F, screenAnchored)
                  );
               } else if (item instanceof SplashPotionItem) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new SplashPotionEntity(mc.world, player, stack), 0.5, pitch, yaw, -20.0F, screenAnchored)
                  );
               } else if (item instanceof LingeringPotionItem) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new LingeringPotionEntity(mc.world, player, stack), 0.5, pitch, yaw, -20.0F, screenAnchored)
                  );
               } else if (item instanceof TridentItem && !activeStack.isEmpty() && activeStack.getItem() == item && player.getItemUseTime() >= 10) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new TridentEntity(mc.world, player, stack), 2.5, pitch, yaw, 0.0F, screenAnchored)
                  );
               } else if (item instanceof SnowballItem) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new SnowballEntity(mc.world, player, stack), 1.5, pitch, yaw, 0.0F, screenAnchored)
                  );
               } else if (item instanceof EggItem) {
                  this.addPrediction(predictions, this.checkTrajectory(player, new EggEntity(mc.world, player, stack), 1.5, pitch, yaw, 0.0F, screenAnchored));
               } else if (item instanceof EnderPearlItem) {
                  this.addPrediction(
                     predictions, this.checkTrajectory(player, new EnderPearlEntity(mc.world, player, stack), 1.5, pitch, yaw, 0.0F, screenAnchored)
                  );
               } else if (item instanceof BowItem && !activeStack.isEmpty() && activeStack.getItem() == item && player.isUsingItem()) {
                  float power = BowItem.getPullProgress(player.getItemUseTime()) * 3.0F;
                  this.addPrediction(predictions, this.checkTrajectory(player, this.newArrow(player, stack), (double)power, pitch, yaw, 0.0F, screenAnchored));
               } else if (item instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
                  ChargedProjectilesComponent charged = (ChargedProjectilesComponent)stack.get(DataComponentTypes.CHARGED_PROJECTILES);
                  if (charged != null && !charged.isEmpty()) {
                     double velocity = charged.contains(Items.FIREWORK_ROCKET) ? 1.6 : 3.15;
                     this.addPrediction(
                        predictions,
                        this.checkTrajectory(
                           player, this.buildCrossbowDirection(player, partialTicks, 0.0F), this.newArrow(player, stack), velocity, screenAnchored
                        )
                     );
                     if (charged.getProjectiles().size() > 2) {
                        this.addPrediction(
                           predictions,
                           this.checkTrajectory(
                              player, this.buildCrossbowDirection(player, partialTicks, -10.0F), this.newArrow(player, stack), velocity, screenAnchored
                           )
                        );
                        this.addPrediction(
                           predictions,
                           this.checkTrajectory(
                              player, this.buildCrossbowDirection(player, partialTicks, 10.0F), this.newArrow(player, stack), velocity, screenAnchored
                           )
                        );
                     }
                  }
               }

               for (TrajectoriesFeature.Prediction prediction : predictions) {
                  if (screenAnchored) {
                     TrajectoriesFeature.ScreenPoint connectorEnd = this.projectToScreen(prediction.screenConnectorEnd());
                     if (connectorEnd != null) {
                        this.pendingScreenLines.add(new TrajectoriesFeature.ScreenLine(connectorEnd.x(), connectorEnd.y(), this.animatedAccentColor(0)));
                     }
                  }

                  lines.addAll(prediction.lines());
                  if (prediction.result() != null) {
                     this.addImpactMarker(lines, rings, planeRects, prediction.projectile(), prediction.result());
                  }
               }
            }
         }
      }
   }

   private ArrowEntity newArrow(LivingEntity shooter, ItemStack weapon) {
      return new ArrowEntity(mc.world, shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ(), new ItemStack(Items.ARROW), weapon);
   }

   private TrajectoriesFeature.Prediction checkTrajectory(
      LivingEntity shooter, ProjectileEntity entity, double velocity, float pitch, float yaw, float angleOffset, boolean screenAnchored
   ) {
      return this.checkTrajectory(shooter, this.buildShootFromRotationDirection(pitch, yaw, angleOffset), entity, velocity, screenAnchored);
   }

   private TrajectoriesFeature.Prediction checkTrajectory(LivingEntity shooter, Vec3d lookVec, ProjectileEntity entity, double velocity, boolean screenAnchored) {
      if (shooter == null) {
         return new TrajectoriesFeature.Prediction(entity, null, List.of(), null);
      } else {
         float partialTicks = mc.getRenderTickCounter().getTickProgress(false);
         double startX = MathHelper.lerp((double)partialTicks, shooter.lastX, shooter.getX());
         double startY = MathHelper.lerp((double)partialTicks, shooter.lastY, shooter.getY()) + (double)shooter.getStandingEyeHeight();
         double startZ = MathHelper.lerp((double)partialTicks, shooter.lastZ, shooter.getZ());
         Vec3d startPos = new Vec3d(startX, startY - 0.1, startZ);
         entity.setPosition(startPos.x, startPos.y, startPos.z);
         if (entity instanceof PersistentProjectileEntity arrow && arrow.getWeaponStack() != null && arrow.getWeaponStack().getItem() == Items.CROSSBOW) {
            Vec3d motion = lookVec.normalize().multiply(velocity);
            return this.traceTrajectory(startPos, motion, entity, screenAnchored);
         }

         Vec3d motion = this.buildShootFromRotationMotion(shooter, lookVec, velocity, true);
         return this.traceTrajectory(startPos, motion, entity, screenAnchored);
      }
   }

   private TrajectoriesFeature.Prediction traceTrajectory(Vec3d start, Vec3d startMotion, ProjectileEntity entity, boolean screenAnchored) {
      List<WorldMeshRenderer.Line> lines = new ArrayList<>();
      Vec3d screenConnectorEnd = null;
      Vec3d pos = start;
      Vec3d motion = startMotion;

      for (int tick = 0; tick < 300; tick++) {
         TrajectoriesFeature.TrajectoryStep step = this.simulateProjectileStep(entity, pos, motion);
         Vec3d renderEnd = step.hitResult() != null ? step.hitResult().getPos() : step.nextPos();
         float alpha = MathHelper.clamp((float)tick / 7.0F, 0.0F, 1.0F);
         if (tick == 0 && screenAnchored) {
            screenConnectorEnd = renderEnd;
         } else {
            lines.add(new WorldMeshRenderer.Line(pos, renderEnd, ColorUtil.applyAlpha(this.animatedAccentColor(tick), alpha)));
         }

         if (step.hitResult() != null) {
            return new TrajectoriesFeature.Prediction(entity, step.hitResult(), lines, screenConnectorEnd);
         }

         if (step.nextPos().y < -128.0) {
            break;
         }

         pos = step.nextPos();
         motion = step.nextMotion();
      }

      return new TrajectoriesFeature.Prediction(entity, null, lines, screenConnectorEnd);
   }

   private TrajectoriesFeature.TrajectoryStep simulateProjectileStep(ProjectileEntity projectile, Vec3d pos, Vec3d motion) {
      return projectile instanceof PersistentProjectileEntity arrow
         ? this.simulateArrowStep(arrow, pos, motion)
         : this.simulateThrowableStep(projectile, pos, motion);
   }

   private TrajectoriesFeature.TrajectoryStep simulateThrowableStep(ProjectileEntity projectile, Vec3d pos, Vec3d motion) {
      Vec3d nextMotion = motion.add(0.0, -this.projectileGravity(projectile), 0.0).multiply(this.isInWater(pos) ? 0.8 : 0.99);
      HitResult hit = this.raycastProjectile(pos, nextMotion, projectile);
      return new TrajectoriesFeature.TrajectoryStep(hit != null ? hit.getPos() : pos.add(nextMotion), nextMotion, hit);
   }

   private TrajectoriesFeature.TrajectoryStep simulateArrowStep(PersistentProjectileEntity arrow, Vec3d pos, Vec3d motion) {
      boolean inWater = this.isInWater(pos);
      Vec3d moveDelta = inWater ? motion.multiply(arrow instanceof TridentEntity ? 0.99 : 0.6) : motion;
      HitResult hit = this.raycastProjectile(pos, moveDelta, arrow);
      Vec3d nextPos = hit != null ? hit.getPos() : pos.add(moveDelta);
      Vec3d nextMotion = inWater ? moveDelta.add(0.0, -0.05, 0.0) : moveDelta.multiply(0.99).add(0.0, -0.05, 0.0);
      return new TrajectoriesFeature.TrajectoryStep(nextPos, nextMotion, hit);
   }

   private HitResult raycastProjectile(Vec3d start, Vec3d motion, ProjectileEntity projectile) {
      if (mc.world == null) {
         return null;
      } else {
         Vec3d end = start.add(motion);
         HitResult hit = mc.world.getCollisionsIncludingWorldBorder(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, projectile));
         if (hit.getType() != Type.MISS) {
            end = hit.getPos();
         }

         Box boxAtStart = projectile.getBoundingBox().offset(start.subtract(projectile.getEntityPos()));
         EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
            mc.world, projectile, start, end, boxAtStart.stretch(motion).expand(1.0), candidate -> this.canHitProjectileTarget(projectile, candidate)
         );
         if (entityHit != null) {
            hit = entityHit;
         }

         return hit.getType() != Type.MISS ? hit : null;
      }
   }

   private boolean canHitProjectileTarget(ProjectileEntity projectile, Entity candidate) {
      if (!candidate.canBeHitByProjectile()) {
         return false;
      } else {
         Entity owner = projectile.getOwner();
         if (owner == null) {
            return true;
         } else if (candidate != owner && !owner.isConnectedThroughVehicle(candidate)) {
            if (projectile instanceof PersistentProjectileEntity
               && owner instanceof PlayerEntity ownerPlayer
               && candidate instanceof PlayerEntity targetPlayer
               && !ownerPlayer.shouldDamagePlayer(targetPlayer)) {
               return false;
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private double projectileGravity(ProjectileEntity projectile) {
      if (projectile instanceof PersistentProjectileEntity) {
         return 0.05;
      } else if (projectile instanceof ExperienceBottleEntity) {
         return 0.07;
      } else if (projectile instanceof PotionEntity) {
         return 0.05;
      } else {
         return projectile instanceof ThrownItemEntity ? 0.03 : 0.03;
      }
   }

   private boolean isInWater(Vec3d pos) {
      return mc.world != null && mc.world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
   }

   private Vec3d buildShootFromRotationMotion(LivingEntity shooter, Vec3d direction, double velocity, boolean addShooterMovement) {
      Vec3d motion = direction.normalize().multiply(velocity);
      if (addShooterMovement) {
         Vec3d knownMovement = shooter.getMovement();
         motion = motion.add(knownMovement.x, shooter.isOnGround() ? 0.0 : knownMovement.y, knownMovement.z);
      }

      return motion;
   }

   private Vec3d buildShootFromRotationDirection(float pitch, float yaw, float angleOffset) {
      double pitchRad = (double)pitch * (Math.PI / 180.0);
      double yawRad = (double)yaw * (Math.PI / 180.0);
      double x = -Math.sin(yawRad) * Math.cos(pitchRad);
      double y = -Math.sin((double)(pitch + angleOffset) * (Math.PI / 180.0));
      double z = Math.cos(yawRad) * Math.cos(pitchRad);
      return new Vec3d(x, y, z);
   }

   private Vec3d buildCrossbowDirection(LivingEntity shooter, float partialTicks, float angle) {
      if (angle == 0.0F) {
         return shooter.getRotationVec(partialTicks);
      } else {
         Vec3d up = shooter.getOppositeRotationVector(partialTicks);
         Quaternionf rotation = new Quaternionf().setAngleAxis((double)angle * (Math.PI / 180.0), up.x, up.y, up.z);
         Vector3f rotated = shooter.getRotationVec(partialTicks).toVector3f().rotate(rotation);
         return new Vec3d((double)rotated.x, (double)rotated.y, (double)rotated.z);
      }
   }

   private Vec3d calculateMotion(Entity entity, Vec3d prevPos, Vec3d motion) {
      boolean water = mc.world != null && mc.world.getFluidState(BlockPos.ofFloored(prevPos)).isIn(FluidTags.WATER);
      double inertia;
      double gravity;
      if (entity instanceof TridentEntity) {
         inertia = 0.99;
         gravity = 0.05;
      } else if (entity instanceof PersistentProjectileEntity) {
         inertia = water ? 0.6 : 0.99;
         gravity = 0.05;
      } else if (entity instanceof ExperienceBottleEntity) {
         inertia = water ? 0.8 : 0.99;
         gravity = 0.07;
      } else if (entity instanceof PotionEntity) {
         inertia = water ? 0.8 : 0.99;
         gravity = 0.05;
      } else if (entity instanceof ThrownItemEntity) {
         inertia = water ? 0.8 : 0.99;
         gravity = 0.03;
      } else if (entity instanceof ItemEntity) {
         inertia = water ? 0.8 : 0.98;
         gravity = 0.04;
      } else {
         inertia = 0.99;
         gravity = 0.03;
      }

      return motion.multiply(inertia).add(0.0, -gravity, 0.0);
   }

   private void addImpactMarker(
      List<WorldMeshRenderer.Line> lines,
      List<WorldMeshRenderer.Ring> rings,
      List<WorldMeshRenderer.PlaneRect> planeRects,
      ProjectileEntity projectile,
      HitResult result
   ) {
      Direction direction = this.getDirection(result);
      int color = result.getType() == Type.ENTITY ? -48060 : this.animatedAccentColor(0);
      Vec3d center = result.getPos().add((double)direction.getOffsetX() * 0.01, (double)direction.getOffsetY() * 0.01, (double)direction.getOffsetZ() * 0.01);
      double width = 0.12;
      Vec3d u;
      Vec3d v;
      switch (direction.getAxis()) {
         case X:
            u = new Vec3d(0.0, 1.0, 0.0);
            v = new Vec3d(0.0, 0.0, 1.0);
            break;
         case Y:
            u = new Vec3d(1.0, 0.0, 0.0);
            v = new Vec3d(0.0, 0.0, 1.0);
            break;
         case Z:
            u = new Vec3d(1.0, 0.0, 0.0);
            v = new Vec3d(0.0, 1.0, 0.0);
            break;
         default:
            u = new Vec3d(1.0, 0.0, 0.0);
            v = new Vec3d(0.0, 0.0, 1.0);
      }

      rings.add(new WorldMeshRenderer.Ring(center, u, v, width, 0.004, color, 180));
      double crossRadius = width * 0.72;
      planeRects.add(new WorldMeshRenderer.PlaneRect(center, u, v, crossRadius, 0.003, color));
      planeRects.add(new WorldMeshRenderer.PlaneRect(center, v, u, crossRadius, 0.003, color));
      if (result instanceof EntityHitResult entityHit) {
         this.addEntityHitBox(lines, planeRects, entityHit.getEntity());
      }

      Double areaRadius = this.potionAreaRadius(projectile);
      if (areaRadius != null) {
         Vec3d areaCenter = this.resolvePotionAreaCenter(projectile, result);
         int areaColor = this.animatedAccentColor(0);
         rings.add(
            new WorldMeshRenderer.Ring(
               areaCenter, new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 0.0, 1.0), areaRadius, 0.018, ColorUtil.applyAlpha(areaColor, 0.55F), 220
            )
         );
         if (areaCenter.squaredDistanceTo(result.getPos()) > 1.0E-4) {
            lines.add(new WorldMeshRenderer.Line(result.getPos(), areaCenter, ColorUtil.applyAlpha(areaColor, 0.67F)));
         }

         if (projectile instanceof SplashPotionEntity splashPotion) {
            this.addSplashExposureLines(lines, splashPotion, result, areaCenter);
         }
      }
   }

   private void registerImpact(Entity entity, Vec3d pos, int ticks) {
      ItemStack stack = this.iconFor(entity);
      String ownerName = entity instanceof ProjectileEntity projectile ? this.ownerName(projectile.getOwner()) : null;
      List<StatusEffectInstance> effects = new ArrayList<>();
      PotionContentsComponent potionContents = (PotionContentsComponent)stack.get(DataComponentTypes.POTION_CONTENTS);
      if (potionContents != null) {
         potionContents.getEffects().forEach(effects::add);
      }

      this.points.add(new TrajectoriesFeature.ImpactPoint(stack.copy(), pos, ticks, entity.age, ownerName, List.copyOf(effects)));
   }

   private String ownerName(Entity owner) {
      return owner instanceof LivingEntity living ? living.getName().getString() : null;
   }

   private ItemStack iconFor(Entity entity) {
      if (entity instanceof ItemEntity itemEntity) {
         return itemEntity.getStack();
      } else {
         if (entity instanceof FlyingItemEntity supplier) {
            ItemStack supplied = supplier.getStack();
            if (!supplied.isEmpty()) {
               return supplied;
            }
         }

         if (entity instanceof PersistentProjectileEntity arrow) {
            ItemStack pickup = arrow.getItemStack();
            if (!pickup.isEmpty()) {
               return pickup;
            }
         }

         ItemStack picked = entity.getPickBlockStack();
         return picked != null && !picked.isEmpty() ? picked : new ItemStack(this.fallbackIcon(entity));
      }
   }

   private Item fallbackIcon(Entity entity) {
      if (entity instanceof TridentEntity) {
         return Items.TRIDENT;
      } else if (entity instanceof SpectralArrowEntity) {
         return Items.SPECTRAL_ARROW;
      } else if (entity instanceof PersistentProjectileEntity) {
         return Items.ARROW;
      } else if (entity instanceof AbstractWindChargeEntity) {
         return Items.WIND_CHARGE;
      } else if (entity instanceof DragonFireballEntity) {
         return Items.DRAGON_BREATH;
      } else if (entity instanceof WitherSkullEntity) {
         return Items.WITHER_SKELETON_SKULL;
      } else if (entity instanceof AbstractFireballEntity) {
         return Items.FIRE_CHARGE;
      } else if (entity instanceof ShulkerBulletEntity) {
         return Items.SHULKER_SHELL;
      } else if (entity instanceof FishingBobberEntity) {
         return Items.FISHING_ROD;
      } else if (entity instanceof FireworkRocketEntity) {
         return Items.FIREWORK_ROCKET;
      } else if (entity instanceof EyeOfEnderEntity) {
         return Items.ENDER_EYE;
      } else {
         return entity instanceof LlamaSpitEntity ? Items.SLIME_BALL : Items.SNOWBALL;
      }
   }

   private void renderImpactTag(Render2DEvent event, TrajectoriesFeature.ImpactPoint point, float screenX, float screenY, float unit) {
      float width = 36.0F * unit;
      float card = 36.0F * unit;
      float height = 48.0F * unit;
      float gap = 4.0F * unit;
      float radius = 10.0F * unit;
      float border = Math.max(1.0F, 1.2F * unit);
      float icon = 20.0F * unit;
      float textSize = 8.0F * unit;
      float textRow = 8.0F * unit;
      float x = screenX - width * 0.5F;
      float y = screenY - height * 0.5F;
      int totalTicks = point.ticks() + point.entityAge();
      float progress = totalTicks > 0 ? MathHelper.clamp((float)point.ticks() / (float)totalTicks, 0.0F, 1.0F) : 0.0F;
      int accent = Theme.getAccent();
      Render2DUtil.rect(x, y, card, card).color(Theme.Colors.BACKGROUND_PRIMARY_50).radius(radius).blur(8.0F * unit).draw();
      this.drawRoundedProgressBorder(event, x, y, card, radius, border, progress, accent);
      float iconOffset = (card - icon) * 0.5F;
      this.drawScaledItem(event, point.stack(), x + iconOffset, y + iconOffset, icon);
      String time = this.formatSeconds(point.ticks());
      float textCenterX = x + width * 0.5F;
      float textCenterY = y + card + gap + textRow * 0.5F;
      Render2DUtil.text(textCenterX, UiFonts.sfProDisplay().centeredTextY(textCenterY, textSize), textSize, time)
         .style(UiFontStyle.MEDIUM)
         .align(TextAlign.CENTER)
         .color(-1)
         .draw();
   }

   private void drawScaledItem(Render2DEvent event, ItemStack stack, float x, float y, float size) {
      if (!stack.isEmpty()) {
         Render2DUtil.flush();
         double guiScale = (double)mc.getWindow().getScaleFactor();
         float scale = size / 16.0F;
         float itemX = (float)((double)Math.round((double)x * guiScale) / guiScale);
         float itemY = (float)((double)Math.round((double)y * guiScale) / guiScale);
         Matrix3x2fStack pose = event.getGuiGraphicsExtractor().getMatrices();
         pose.pushMatrix();
         pose.translate(itemX, itemY);
         pose.scale(scale);
         event.getGuiGraphicsExtractor().drawItem(stack, 0, 0);
         pose.popMatrix();
      }
   }

   private void drawRoundedProgressBorder(Render2DEvent event, float x, float y, float size, float radius, float thickness, float progress, int color) {
      progress = MathHelper.clamp(progress, 0.0F, 1.0F);
      if (!(progress <= 0.001F) && !(thickness <= 0.0F)) {
         float straight = Math.max(0.0F, size - radius * 2.0F);
         float arc = (float)((Math.PI / 2) * (double)radius);
         float perimeter = straight * 4.0F + arc * 4.0F;
         float target = perimeter * progress;
         float half = thickness * 0.5F;
         int samples = Math.max(48, Math.round(perimeter / 1.5F));
         float traveled = 0.0F;
         float prevX = x + size * 0.5F;
         float prevY = y;

         for (int i = 1; i <= samples; i++) {
            float distance = perimeter * ((float)i / (float)samples);
            float[] point = pointOnRoundedRect(x, y, size, radius, straight, arc, distance);
            float seg = distance - traveled;
            if (traveled < target) {
               float drawLen = Math.min(seg, target - traveled);
               float t = drawLen / seg;
               float endX = prevX + (point[0] - prevX) * t;
               float endY = prevY + (point[1] - prevY) * t;
               this.strokeSegment(event, prevX, prevY, endX, endY, half, color);
            }

            traveled = distance;
            prevX = point[0];
            prevY = point[1];
            if (distance >= target) {
               break;
            }
         }
      }
   }

   private static float[] pointOnRoundedRect(float x, float y, float size, float radius, float straight, float arc, float distance) {
      float halfTop = straight * 0.5F;
      if (distance <= halfTop) {
         return new float[]{x + size * 0.5F + distance, y};
      } else {
         float cursor = distance - halfTop;
         if (cursor <= arc) {
            float angle = (float)((-Math.PI / 2) + (double)(cursor / radius));
            return new float[]{x + size - radius + (float)Math.cos((double)angle) * radius, y + radius + (float)Math.sin((double)angle) * radius};
         } else {
            cursor -= arc;
            if (cursor <= straight) {
               return new float[]{x + size, y + radius + cursor};
            } else {
               cursor -= straight;
               if (cursor <= arc) {
                  float angle = cursor / radius;
                  return new float[]{x + size - radius + (float)Math.cos((double)angle) * radius, y + size - radius + (float)Math.sin((double)angle) * radius};
               } else {
                  cursor -= arc;
                  if (cursor <= straight) {
                     return new float[]{x + size - radius - cursor, y + size};
                  } else {
                     cursor -= straight;
                     if (cursor <= arc) {
                        float angle = (float)((Math.PI / 2) + (double)(cursor / radius));
                        return new float[]{x + radius + (float)Math.cos((double)angle) * radius, y + size - radius + (float)Math.sin((double)angle) * radius};
                     } else {
                        cursor -= arc;
                        if (cursor <= straight) {
                           return new float[]{x, y + size - radius - cursor};
                        } else {
                           cursor -= straight;
                           if (cursor <= arc) {
                              float angle = (float)(Math.PI + (double)(cursor / radius));
                              return new float[]{x + radius + (float)Math.cos((double)angle) * radius, y + radius + (float)Math.sin((double)angle) * radius};
                           } else {
                              cursor -= arc;
                              return new float[]{x + radius + Math.min(cursor, halfTop), y};
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void strokeSegment(Render2DEvent event, float x1, float y1, float x2, float y2, float halfThickness, int color) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float length = (float)Math.sqrt((double)(dx * dx + dy * dy));
      if (length < 0.001F) {
         Render2DUtil.rect(x1 - halfThickness, y1 - halfThickness, halfThickness * 2.0F, halfThickness * 2.0F).color(color).draw();
      } else {
         Matrix3x2fStack pose = event.getGuiGraphicsExtractor().getMatrices();
         pose.pushMatrix();
         pose.translate(x1, y1);
         pose.rotate((float)Math.atan2((double)dy, (double)dx));
         float overlap = halfThickness * 0.75F;
         Render2DUtil.rect(-overlap, -halfThickness, length + overlap * 2.0F, halfThickness * 2.0F).color(color).draw();
         pose.popMatrix();
      }
   }

   private void addEntityHitBox(List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.PlaneRect> planeRects, Entity entity) {
      Box box = entity.getBoundingBox().expand(0.028);
      Vec3d center = new Vec3d((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
      double hx = box.getLengthX() * 0.5;
      double hy = box.getLengthY() * 0.5;
      double hz = box.getLengthZ() * 0.5;
      int fillColor = ColorUtil.applyAlpha(this.animatedAccentColor(22), 0.09F);
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3d(box.minX, center.y, center.z), new Vec3d(0.0, 1.0, 0.0), new Vec3d(0.0, 0.0, 1.0), hy, hz, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3d(box.maxX, center.y, center.z), new Vec3d(0.0, 1.0, 0.0), new Vec3d(0.0, 0.0, 1.0), hy, hz, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3d(center.x, box.minY, center.z), new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 0.0, 1.0), hx, hz, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3d(center.x, box.maxY, center.z), new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 0.0, 1.0), hx, hz, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3d(center.x, center.y, box.minZ), new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 1.0, 0.0), hx, hy, fillColor)
      );
      planeRects.add(
         new WorldMeshRenderer.PlaneRect(new Vec3d(center.x, center.y, box.maxZ), new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 1.0, 0.0), hx, hy, fillColor)
      );
      Vec3d[] corners = new Vec3d[]{
         new Vec3d(box.minX, box.minY, box.minZ),
         new Vec3d(box.minX, box.minY, box.maxZ),
         new Vec3d(box.minX, box.maxY, box.minZ),
         new Vec3d(box.minX, box.maxY, box.maxZ),
         new Vec3d(box.maxX, box.minY, box.minZ),
         new Vec3d(box.maxX, box.minY, box.maxZ),
         new Vec3d(box.maxX, box.maxY, box.minZ),
         new Vec3d(box.maxX, box.maxY, box.maxZ)
      };
      int[][] edges = new int[][]{{0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3}, {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}};

      for (int i = 0; i < edges.length; i++) {
         int edgeColor = ColorUtil.applyAlpha(this.animatedAccentColor(i * 11), 0.94F);
         lines.add(new WorldMeshRenderer.Line(corners[edges[i][0]], corners[edges[i][1]], edgeColor));
      }
   }

   private void addSplashExposureLines(List<WorldMeshRenderer.Line> lines, SplashPotionEntity projectile, HitResult result, Vec3d areaCenter) {
      if (mc.world != null) {
         Entity hitEntity = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
         Box affectedBox = new Box(areaCenter.x - 4.0, areaCenter.y - 2.0, areaCenter.z - 4.0, areaCenter.x + 4.0, areaCenter.y + 2.0, areaCenter.z + 4.0);

         for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, affectedBox, candidate -> candidate.isAlive() && !candidate.isRemoved())) {
            if (entity != mc.player) {
               Vec3d targetPoint = new Vec3d(entity.getX(), areaCenter.y, entity.getZ());
               Vec3d offset = targetPoint.subtract(areaCenter);
               Vec3d horizontal = new Vec3d(offset.x, 0.0, offset.z);
               double distance = Math.sqrt(horizontal.x * horizontal.x + horizontal.z * horizontal.z);
               if (!(distance > 4.0) && !(distance < 0.001)) {
                  double exposure = entity == hitEntity ? 1.0 : MathHelper.clamp(1.0 - distance / 4.0, 0.0, 1.0);
                  float alpha = (float)(0.38 + 0.54 * exposure);
                  lines.add(new WorldMeshRenderer.Line(areaCenter, targetPoint, ColorUtil.applyAlpha(-12386427, alpha), ColorUtil.applyAlpha(-42920, alpha)));
               }
            }
         }
      }
   }

   private Double potionAreaRadius(ProjectileEntity projectile) {
      if (projectile instanceof SplashPotionEntity) {
         return 4.0;
      } else {
         return projectile instanceof LingeringPotionEntity ? 3.0 : null;
      }
   }

   private Vec3d resolvePotionAreaCenter(ProjectileEntity projectile, HitResult result) {
      if (mc.world == null) {
         return result.getPos();
      } else {
         Vec3d start = result.getPos().add(0.0, 0.35, 0.0);
         Vec3d end = result.getPos().add(0.0, -2.5, 0.0);
         HitResult groundHit = mc.world.getCollisionsIncludingWorldBorder(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, projectile));
         return groundHit.getType() != Type.MISS ? groundHit.getPos().add(0.0, 0.012, 0.0) : result.getPos();
      }
   }

   private Direction getDirection(HitResult result) {
      if (result instanceof BlockHitResult blockHit) {
         return blockHit.getSide();
      } else if (mc.player == null) {
         return Direction.UP;
      } else {
         Vec3d vec = result.getPos().subtract(mc.player.getEyePos()).normalize();
         return Direction.getFacing((float)vec.x, (float)vec.y, (float)vec.z);
      }
   }

   private boolean isStationary(Entity entity) {
      boolean posChange = entity.getEntityPos().equals(entity.getLastRenderPos());
      boolean itemEntityCheck = entity instanceof ItemEntity
         && (entity.isOnGround() || mc.world != null && mc.world.getFluidState(entity.getBlockPos()).isIn(FluidTags.WATER));
      return posChange || itemEntityCheck;
   }

   private HitResult raycastBlock(Vec3d start, Vec3d end, Entity entity) {
      return mc.world == null
         ? BlockHitResult.createMissed(end, Direction.UP, BlockPos.ofFloored(end))
         : mc.world.raycast(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, entity));
   }

   private void drawScreenLine(Render2DEvent event, float x1, float y1, float x2, float y2, float thickness, int color) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float length = (float)Math.sqrt((double)(dx * dx + dy * dy));
      if (!(length < 0.001F)) {
         Matrix3x2fStack pose = event.getGuiGraphicsExtractor().getMatrices();
         pose.pushMatrix();
         pose.translate(x1, y1);
         pose.rotate((float)Math.atan2((double)dy, (double)dx));
         Render2DUtil.rect(0.0F, -thickness * 0.5F, length, thickness).color(color).draw();
         pose.popMatrix();
      }
   }

   private String formatSeconds(int ticks) {
      int seconds = Math.max(0, Math.round((float)ticks / 20.0F));
      return seconds + "s";
   }

   private TrajectoriesFeature.ScreenPoint projectToScreen(Vec3d pos) {
      Render3DUtil.ScreenPoint point = Render3DUtil.projectToScreen(mc, pos);
      return point == null ? null : new TrajectoriesFeature.ScreenPoint(point.x(), point.y());
   }

   private void addPrediction(List<TrajectoriesFeature.Prediction> predictions, TrajectoriesFeature.Prediction prediction) {
      if (prediction != null) {
         predictions.add(prediction);
      }
   }

   private void clear() {
      this.tpSkipTicks = 0;
      this.pendingScreenLines.clear();
      this.points.clear();
   }

   private int animatedAccentColor(int index) {
      return ColorUtil.fade(8, index * 11, Theme.getAccent(), ColorUtil.rgb(101, 228, 255));
   }

   @Environment(EnvType.CLIENT)
   private static record ImpactPoint(ItemStack stack, Vec3d pos, int ticks, int entityAge, String ownerName, List<StatusEffectInstance> effects) {
   }

   @Environment(EnvType.CLIENT)
   private static record Prediction(ProjectileEntity projectile, HitResult result, List<WorldMeshRenderer.Line> lines, Vec3d screenConnectorEnd) {
   }

   @Environment(EnvType.CLIENT)
   private static record ScreenLine(float x, float y, int color) {
   }

   @Environment(EnvType.CLIENT)
   private static record ScreenPoint(float x, float y) {
   }

   @Environment(EnvType.CLIENT)
   private static record TrajectoryStep(Vec3d nextPos, Vec3d nextMotion, HitResult hitResult) {
   }
}
