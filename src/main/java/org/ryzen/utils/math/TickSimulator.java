package org.ryzen.utils.math;

import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseEffectsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.World;
import org.ryzen.mixin.accessor.LivingEntityAccessor;

@Environment(EnvType.CLIENT)
public final class TickSimulator {
   private static final double COLLISION_EPSILON = 1.0E-7;
   private static final float DEFAULT_FRICTION = 0.6F;
   private static final double BELOW_OFFSET = 0.5000001;

   private TickSimulator() {
   }

   public static void simulateNextTick(TickSimulator.SimState state, World level) {
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(level, "level");
      if (state.noJumpDelay > 0) {
         state.noJumpDelay--;
      }

      updateFluidState(state, level);
      clampSmallMovement(state);
      tickJump(state, level);
      if (hasEffect(state, StatusEffects.SLOW_FALLING) || hasEffect(state, StatusEffects.LEVITATION)) {
         state.fallDistance = 0.0F;
      }

      boolean useFluidTravel = (state.inWater || state.inLava) && !state.flying && !canStandOnFluid(state);
      if (useFluidTravel) {
         if (state.inWater) {
            travelInWater(state, level);
         } else {
            travelInLava(state, level);
         }
      } else {
         travelInAir(state, level);
      }
   }

   public static TickSimulator.SimState getPredictedState(LivingEntity entity, int ticks, World level) {
      if (ticks < 0) {
         throw new IllegalArgumentException("Prediction tick count cannot be negative");
      } else {
         Objects.requireNonNull(level, "level");
         TickSimulator.SimState state = new TickSimulator.SimState(entity);

         for (int i = 0; i < ticks; i++) {
            simulateNextTick(state, level);
         }

         return state;
      }
   }

   public static TickSimulator.SimState simulateLocalPlayer(ClientPlayerEntity player, int ticks, World level) {
      if (ticks < 0) {
         throw new IllegalArgumentException("Prediction tick count cannot be negative");
      } else {
         Objects.requireNonNull(level, "level");
         TickSimulator.SimState state = new TickSimulator.SimState(player).withLocalInput(player);

         for (int i = 0; i < ticks; i++) {
            simulateNextTick(state, level);
         }

         return state;
      }
   }

   public static int ticksUntilCriticalWindow(LivingEntity entity, int maxTicks, World level) {
      TickSimulator.SimState state = entity instanceof ClientPlayerEntity player
         ? new TickSimulator.SimState(player).withLocalInput(player)
         : new TickSimulator.SimState(entity);
      if (!state.mobilityRestricted && !state.passenger) {
         for (int tick = 0; tick <= maxTicks; tick++) {
            if (state.isFallingCriticalWindow()) {
               return tick;
            }

            if (tick < maxTicks) {
               simulateNextTick(state, level);
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private static void clampSmallMovement(TickSimulator.SimState state) {
      double dx = state.motion.x;
      double dy = state.motion.y;
      double dz = state.motion.z;
      if (state.source instanceof PlayerEntity) {
         if (state.motion.horizontalLengthSquared() < 9.0E-6) {
            dx = 0.0;
            dz = 0.0;
         }
      } else {
         if (Math.abs(dx) < 0.003) {
            dx = 0.0;
         }

         if (Math.abs(dz) < 0.003) {
            dz = 0.0;
         }
      }

      if (Math.abs(dy) < 0.003) {
         dy = 0.0;
      }

      state.motion = new Vec3d(dx, dy, dz);
   }

   private static void tickJump(TickSimulator.SimState state, World level) {
      if (!state.jumpHeld) {
         state.noJumpDelay = 0;
      } else {
         double fluidHeight = state.inLava ? state.lavaHeight : state.waterHeight;
         boolean inWaterWithHeight = state.inWater && fluidHeight > 0.0;
         double jumpThreshold = fluidJumpThreshold(state);
         if (!inWaterWithHeight || state.onGround && !(fluidHeight > jumpThreshold)) {
            if (!state.inLava || state.onGround && !(fluidHeight > jumpThreshold)) {
               if ((state.onGround || inWaterWithHeight && fluidHeight <= jumpThreshold) && state.noJumpDelay == 0) {
                  jumpFromGround(state, level);
                  state.noJumpDelay = 10;
               }
            } else {
               state.motion = state.motion.add(0.0, 0.04, 0.0);
            }
         } else {
            state.motion = state.motion.add(0.0, 0.04, 0.0);
         }
      }
   }

   private static void jumpFromGround(TickSimulator.SimState state, World level) {
      float jumpPower = (float)attribute(state, EntityAttributes.JUMP_STRENGTH) * blockJumpFactor(state, level) + jumpBoostPower(state);
      if (!(jumpPower <= 1.0E-5F)) {
         state.motion = new Vec3d(state.motion.x, Math.max((double)jumpPower, state.motion.y), state.motion.z);
         if (state.isSprinting) {
            float angle = state.yaw * (float) (Math.PI / 180.0);
            state.motion = state.motion.add((double)(-MathHelper.sin((double)angle)) * 0.2, 0.0, (double)MathHelper.cos((double)angle) * 0.2);
         }

         state.onGround = false;
      }
   }

   private static void travelInAir(TickSimulator.SimState state, World level) {
      BlockPos posBelow = blockPosBelow(state);
      float blockFriction = state.onGround ? level.getBlockState(posBelow).getBlock().getSlipperiness() : 1.0F;
      moveRelative(state, frictionInfluencedSpeed(state, blockFriction));
      state.motion = handleOnClimbable(state, level);
      move(state, level);
      Vec3d movement = state.motion;
      if ((state.horizontalCollision || state.jumpHeld) && state.climbing) {
         movement = new Vec3d(movement.x, 0.2, movement.z);
      }

      double movementY = movement.y;
      StatusEffectInstance levitation = effect(state, StatusEffects.LEVITATION);
      if (levitation != null) {
         movementY += (0.05 * (double)(levitation.getAmplifier() + 1) - movement.y) * 0.2;
         state.fallDistance = 0.0F;
      } else {
         movementY -= effectiveGravity(state, movement.y);
      }

      float airDrag = 0.91F;
      float friction = blockFriction * airDrag;
      float verticalDrag = 0.98F;
      state.motion = new Vec3d(movement.x * (double)friction, movementY * (double)verticalDrag, movement.z * (double)friction);
   }

   private static void travelInWater(TickSimulator.SimState state, World level) {
      boolean isFalling = state.motion.y <= 0.0;
      double oldY = state.pos.y;
      double gravity = effectiveGravity(state, state.motion.y);
      float slowDown = state.isSprinting ? 0.9F : 0.8F;
      float speed = 0.02F;
      float waterEfficiency = (float)attribute(state, EntityAttributes.WATER_MOVEMENT_EFFICIENCY);
      if (!state.onGround) {
         waterEfficiency *= 0.5F;
      }

      if (waterEfficiency > 0.0F) {
         slowDown += (0.54600006F - slowDown) * waterEfficiency;
         speed += (speedAttribute(state) - speed) * waterEfficiency;
      }

      if (hasEffect(state, StatusEffects.DOLPHINS_GRACE)) {
         slowDown = 0.96F;
      }

      moveRelative(state, speed);
      move(state, level);
      Vec3d movement = state.motion;
      if (state.horizontalCollision && state.climbing) {
         movement = new Vec3d(movement.x, 0.2, movement.z);
      }

      movement = movement.multiply((double)slowDown, 0.8, (double)slowDown);
      state.motion = fluidFallingAdjustedMovement(state, gravity, isFalling, movement);
      jumpOutOfFluid(state, level, oldY);
   }

   private static void travelInLava(TickSimulator.SimState state, World level) {
      boolean isFalling = state.motion.y <= 0.0;
      double oldY = state.pos.y;
      double gravity = effectiveGravity(state, state.motion.y);
      moveRelative(state, 0.02F);
      move(state, level);
      if (state.lavaHeight <= fluidJumpThreshold(state)) {
         state.motion = state.motion.multiply(0.5, 0.8, 0.5);
         state.motion = fluidFallingAdjustedMovement(state, gravity, isFalling, state.motion);
      } else {
         state.motion = state.motion.multiply(0.5);
      }

      if (gravity != 0.0) {
         state.motion = state.motion.add(0.0, -gravity / 4.0, 0.0);
      }

      jumpOutOfFluid(state, level, oldY);
   }

   private static void move(TickSimulator.SimState state, World level) {
      Vec3d delta = state.motion;
      if (state.stuckSpeedMultiplier.lengthSquared() > 1.0E-7) {
         delta = delta.multiply(state.stuckSpeedMultiplier);
         state.stuckSpeedMultiplier = Vec3d.ZERO;
         state.motion = Vec3d.ZERO;
      }

      Vec3d resolved = Entity.adjustMovementForCollisions(state.source, delta, state.boundingBox, level, List.of());
      boolean collidedX = differs(delta.x, resolved.x);
      boolean collidedY = differs(delta.y, resolved.y);
      boolean collidedZ = differs(delta.z, resolved.z);
      state.horizontalCollision = collidedX || collidedZ;
      state.verticalCollision = collidedY;
      state.verticalCollisionBelow = collidedY && delta.y < 0.0;
      state.onGround = state.verticalCollisionBelow;
      state.boundingBox = state.boundingBox.offset(resolved);
      state.pos = state.pos.add(resolved);
      if (!state.inWater && resolved.y < 0.0) {
         state.fallDistance = state.fallDistance + (float)(-resolved.y);
      }

      if (state.onGround) {
         state.fallDistance = 0.0F;
      }

      state.motion = new Vec3d(collidedX ? 0.0 : delta.x, collidedY ? 0.0 : delta.y, collidedZ ? 0.0 : delta.z);
      float speedFactor = blockSpeedFactor(state, level);
      state.motion = state.motion.multiply((double)speedFactor, 1.0, (double)speedFactor);
      applyStuckBlocks(state, level);
      state.climbing = isClimbing(state, level);
   }

   private static void applyStuckBlocks(TickSimulator.SimState state, World level) {
      state.inCobweb = false;
      Box box = state.boundingBox.contract(1.0E-7);
      int minX = MathHelper.floor(box.minX);
      int maxX = MathHelper.floor(box.maxX);
      int minY = MathHelper.floor(box.minY);
      int maxY = MathHelper.floor(box.maxY);
      int minZ = MathHelper.floor(box.minZ);
      int maxZ = MathHelper.floor(box.maxZ);
      Mutable cursor = new Mutable();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               cursor.set(x, y, z);
               BlockState blockState = level.getBlockState(cursor);
               if (blockState.isOf(Blocks.COBWEB)) {
                  state.inCobweb = true;
                  state.stuckSpeedMultiplier = new Vec3d(0.25, 0.05, 0.25);
                  state.fallDistance = 0.0F;
               } else if (blockState.isOf(Blocks.POWDER_SNOW)) {
                  state.stuckSpeedMultiplier = new Vec3d(0.9, 1.5, 0.9);
                  state.fallDistance = 0.0F;
               } else if (blockState.isOf(Blocks.SWEET_BERRY_BUSH)) {
                  state.stuckSpeedMultiplier = new Vec3d(0.8, 0.75, 0.8);
                  state.fallDistance = 0.0F;
               }
            }
         }
      }
   }

   private static void updateFluidState(TickSimulator.SimState state, World level) {
      Box box = state.boundingBox.contract(0.001);
      state.waterHeight = fluidHeight(level, box, FluidTags.WATER);
      state.lavaHeight = fluidHeight(level, box, FluidTags.LAVA);
      state.inWater = state.waterHeight > 0.0;
      state.inLava = state.lavaHeight > 0.0;
      double eyeY = state.pos.y + (double)state.source.getStandingEyeHeight();
      state.submergedInWater = state.inWater && box.minY + state.waterHeight > eyeY;
      if (state.inWater) {
         state.fallDistance = 0.0F;
      }
   }

   private static double fluidHeight(World level, Box box, TagKey<Fluid> tag) {
      int minX = MathHelper.floor(box.minX);
      int maxX = MathHelper.ceil(box.maxX);
      int minY = MathHelper.floor(box.minY);
      int maxY = MathHelper.ceil(box.maxY);
      int minZ = MathHelper.floor(box.minZ);
      int maxZ = MathHelper.ceil(box.maxZ);
      double highest = 0.0;
      Mutable cursor = new Mutable();

      for (int x = minX; x < maxX; x++) {
         for (int y = minY; y < maxY; y++) {
            for (int z = minZ; z < maxZ; z++) {
               cursor.set(x, y, z);
               FluidState fluid = level.getFluidState(cursor);
               if (fluid.isIn(tag)) {
                  double surface = (double)((float)y + fluid.getHeight(level, cursor));
                  if (surface >= box.minY) {
                     highest = Math.max(highest, surface - box.minY);
                  }
               }
            }
         }
      }

      return highest;
   }

   private static Vec3d handleOnClimbable(TickSimulator.SimState state, World level) {
      Vec3d delta = state.motion;
      if (!state.climbing) {
         return delta;
      } else {
         state.fallDistance = 0.0F;
         double xd = MathHelper.clamp(delta.x, -0.15, 0.15);
         double zd = MathHelper.clamp(delta.z, -0.15, 0.15);
         double yd = Math.max(delta.y, -0.15);
         if (yd < 0.0
            && state.source instanceof PlayerEntity player
            && player.isHoldingOntoLadder()
            && !level.getBlockState(BlockPos.ofFloored(state.pos)).isOf(Blocks.SCAFFOLDING)) {
            yd = 0.0;
         }

         return new Vec3d(xd, yd, zd);
      }
   }

   private static void jumpOutOfFluid(TickSimulator.SimState state, World level, double oldY) {
      if (state.horizontalCollision) {
         Vec3d movement = state.motion;
         Vec3d climbStep = new Vec3d(movement.x, movement.y + 0.6 - state.pos.y + oldY, movement.z);
         Vec3d resolved = Entity.adjustMovementForCollisions(state.source, climbStep, state.boundingBox, level, List.of());
         if (resolved.equals(climbStep)) {
            state.motion = new Vec3d(movement.x, 0.3, movement.z);
         }
      }
   }

   private static Vec3d fluidFallingAdjustedMovement(TickSimulator.SimState state, double gravity, boolean isFalling, Vec3d movement) {
      if (gravity != 0.0 && !state.isSprinting) {
         double yd;
         if (isFalling && Math.abs(movement.y - 0.005) >= 0.003 && Math.abs(movement.y - gravity / 16.0) < 0.003) {
            yd = -0.003;
         } else {
            yd = movement.y - gravity / 16.0;
         }

         return new Vec3d(movement.x, yd, movement.z);
      } else {
         return movement;
      }
   }

   private static void moveRelative(TickSimulator.SimState state, float speed) {
      Vec3d input = new Vec3d((double)state.impulseX, 0.0, (double)state.impulseZ);
      double lengthSqr = input.lengthSquared();
      if (!(lengthSqr < 1.0E-7)) {
         Vec3d scaled = (lengthSqr > 1.0 ? input.normalize() : input).multiply((double)speed);
         float sin = MathHelper.sin((double)(state.yaw * (float) (Math.PI / 180.0)));
         float cos = MathHelper.cos((double)(state.yaw * (float) (Math.PI / 180.0)));
         state.motion = state.motion.add(scaled.x * (double)cos - scaled.z * (double)sin, scaled.y, scaled.z * (double)cos + scaled.x * (double)sin);
      }
   }

   private static float frictionInfluencedSpeed(TickSimulator.SimState state, float blockFriction) {
      if (state.onGround) {
         float speed = speedAttribute(state);
         return blockFriction > 0.6F ? speed * (0.21600002F / (blockFriction * blockFriction * blockFriction)) : speed;
      } else {
         return state.isSprinting ? 0.025999999F : 0.02F;
      }
   }

   private static float speedAttribute(TickSimulator.SimState state) {
      return (float)attribute(state, EntityAttributes.MOVEMENT_SPEED);
   }

   private static double effectiveGravity(TickSimulator.SimState state, double motionY) {
      double gravity = attribute(state, EntityAttributes.GRAVITY);
      boolean isFalling = motionY <= 0.0;
      if (isFalling && hasEffect(state, StatusEffects.SLOW_FALLING)) {
         state.fallDistance = 0.0F;
         return Math.min(gravity, 0.01);
      } else {
         return gravity;
      }
   }

   private static float blockSpeedFactor(TickSimulator.SimState state, World level) {
      if (state.flying) {
         return 1.0F;
      } else {
         BlockState inState = level.getBlockState(BlockPos.ofFloored(state.pos));
         float factor = inState.getBlock().getVelocityMultiplier();
         if (!inState.isOf(Blocks.WATER) && !inState.isOf(Blocks.BUBBLE_COLUMN) && factor == 1.0F) {
            factor = level.getBlockState(blockPosBelow(state)).getBlock().getVelocityMultiplier();
         }

         float efficiency = (float)attribute(state, EntityAttributes.MOVEMENT_EFFICIENCY);
         return MathHelper.lerp(efficiency, factor, 1.0F);
      }
   }

   private static float blockJumpFactor(TickSimulator.SimState state, World level) {
      float inFactor = level.getBlockState(BlockPos.ofFloored(state.pos)).getBlock().getJumpVelocityMultiplier();
      return inFactor == 1.0F ? level.getBlockState(blockPosBelow(state)).getBlock().getJumpVelocityMultiplier() : inFactor;
   }

   private static float jumpBoostPower(TickSimulator.SimState state) {
      StatusEffectInstance jumpBoost = effect(state, StatusEffects.JUMP_BOOST);
      return jumpBoost == null ? 0.0F : 0.1F * ((float)jumpBoost.getAmplifier() + 1.0F);
   }

   private static boolean isClimbing(TickSimulator.SimState state, World level) {
      BlockState inState = level.getBlockState(BlockPos.ofFloored(state.pos));
      return inState.isIn(BlockTags.CLIMBABLE);
   }

   private static boolean canStandOnFluid(TickSimulator.SimState state) {
      return false;
   }

   private static double fluidJumpThreshold(TickSimulator.SimState state) {
      return (double)state.source.getStandingEyeHeight() < 0.4 ? 0.0 : 0.4;
   }

   private static BlockPos blockPosBelow(TickSimulator.SimState state) {
      return BlockPos.ofFloored(state.pos.x, state.boundingBox.minY - 0.5000001, state.pos.z);
   }

   private static float modifiedFriction(float friction, float modifier) {
      return MathHelper.clamp(1.0F - (1.0F - friction) * modifier, 0.0F, 1.0F);
   }

   private static double attribute(TickSimulator.SimState state, RegistryEntry<EntityAttribute> attribute) {
      return state.source.getAttributeValue(attribute);
   }

   private static StatusEffectInstance effect(TickSimulator.SimState state, RegistryEntry<StatusEffect> effect) {
      return state.source.getStatusEffect(effect);
   }

   private static boolean hasEffect(TickSimulator.SimState state, RegistryEntry<StatusEffect> effect) {
      return state.source.hasStatusEffect(effect);
   }

   private static float itemUseSpeedMultiplier(ClientPlayerEntity player) {
      UseEffectsComponent useEffects = (UseEffectsComponent)player.getActiveItem().get(DataComponentTypes.USE_EFFECTS);
      return useEffects == null ? 1.0F : useEffects.speedMultiplier();
   }

   private static boolean differs(double requested, double resolved) {
      return Math.abs(requested - resolved) > 1.0E-7;
   }

   @Environment(EnvType.CLIENT)
   public static final class SimState {
      public final LivingEntity source;
      public Vec3d pos;
      public Vec3d motion;
      public Box boundingBox;
      public boolean onGround;
      public boolean horizontalCollision;
      public boolean verticalCollision;
      public boolean verticalCollisionBelow;
      public float fallDistance;
      public boolean isSprinting;
      public boolean inWater;
      public boolean submergedInWater;
      public boolean swimming;
      public boolean inLava;
      public boolean climbing;
      public boolean inCobweb;
      public boolean mobilityRestricted;
      public boolean passenger;
      public boolean flying;
      public int noJumpDelay;
      public Vec3d stuckSpeedMultiplier;
      public double waterHeight;
      public double lavaHeight;
      public float impulseX;
      public float impulseZ;
      public boolean jumpHeld;
      public float yaw;

      public SimState(LivingEntity entity) {
         this.stuckSpeedMultiplier = Vec3d.ZERO;
         this.source = Objects.requireNonNull(entity, "entity");
         this.pos = entity.getEntityPos();
         this.motion = entity.getVelocity();
         this.boundingBox = entity.getBoundingBox();
         this.onGround = entity.isOnGround();
         this.horizontalCollision = entity.horizontalCollision;
         this.verticalCollision = entity.verticalCollision;
         this.verticalCollisionBelow = entity.groundCollision;
         this.fallDistance = (float)entity.fallDistance;
         this.isSprinting = entity.isSprinting();
         this.inWater = entity.isTouchingWater();
         this.submergedInWater = entity.isSubmergedInWater();
         this.swimming = entity.isSwimming();
         this.inLava = entity.isInLava();
         this.climbing = entity.isClimbing();
         this.mobilityRestricted = entity instanceof PlayerEntity player && player.hasBlindnessEffect();
         this.passenger = entity.hasVehicle();
         this.flying = entity instanceof PlayerEntity player && player.getAbilities().flying;
         this.yaw = entity.getYaw();
         this.waterHeight = entity.getFluidHeight(FluidTags.WATER);
         this.lavaHeight = entity.getFluidHeight(FluidTags.LAVA);
         this.noJumpDelay = ((LivingEntityAccessor)entity).getNoJumpDelay();
         if (entity instanceof ClientPlayerEntity player && player.input != null) {
            this.jumpHeld = player.input.playerInput.jump();
         }

         TickSimulator.applyStuckBlocks(this, entity.getEntityWorld());
      }

      public TickSimulator.SimState withLocalInput(ClientPlayerEntity player) {
         if (player.input != null) {
            float scale = 0.98F;
            if (player.isUsingItem() && !player.hasVehicle()) {
               scale *= TickSimulator.itemUseSpeedMultiplier(player);
            }

            if (player.shouldSlowDown()) {
               scale *= (float)player.getAttributeValue(EntityAttributes.SNEAKING_SPEED);
            }

            float inputX = player.input.getMovementInput().x * scale;
            float inputZ = player.input.getMovementInput().y * scale;
            float length = (float)Math.sqrt((double)(inputX * inputX + inputZ * inputZ));
            if (length > 0.0F) {
               float absX = Math.abs(inputX / length);
               float absZ = Math.abs(inputZ / length);
               float tangent = absZ > absX ? absX / absZ : absZ / absX;
               float toUnitSquare = (float)Math.sqrt((double)(1.0F + tangent * tangent));
               float modified = Math.min(length * toUnitSquare, 1.0F) / length;
               inputX *= modified;
               inputZ *= modified;
            }

            this.impulseX = inputX;
            this.impulseZ = inputZ;
            this.jumpHeld = player.input.playerInput.jump();
         }

         return this;
      }

      public boolean isFallingCriticalWindow() {
         return this.fallDistance > 0.0F && !this.onGround && !this.inWater && !this.climbing && !this.mobilityRestricted && !this.passenger;
      }
   }
}
