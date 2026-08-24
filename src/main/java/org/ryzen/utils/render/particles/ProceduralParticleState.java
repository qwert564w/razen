package org.ryzen.utils.render.particles;

import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class ProceduralParticleState {
   private static final double PI = 3.141593430080217;
   private static final double LIFT = 0.0030000000694913086;
   private static final double DRIFT = 0.001999998851479194;
   private static final double DRAG = 0.9599996591960513;
   private static final double Y_SLEEP_THRESHOLD = 0.010000003725784143;
   private static final double BOUNCE = 0.5500001584216989;
   private static final double FLOOR_FRICTION = 0.75;
   private static final double OTHER_AXIS_DAMPING = 0.8999997820741558;
   private static final double COLLISION_EPSILON_POSITIVE = 9.99999563509513E-5;
   private static final double COLLISION_EPSILON_NEGATIVE = -9.999997135847346E-5;
   private static final double VELOCITY_EPSILON = 1.0000000000249859E-5;
   private static final float ROTATION_DAMPING = 0.985F;
   private static final float COLLISION_SPIN = 0.3F;
   private static final float FADE_IN_END = 0.2F;
   private static final float FADE_OUT_START = 0.8F;
   private static final double SPAWN_VELOCITY = 0.2000000000678856;
   private double previousX;
   private double previousY;
   private double previousZ;
   private double x;
   private double y;
   private double z;
   private double velocityX;
   private double velocityY;
   private double velocityZ;
   private final int lifetime;
   private final ProceduralParticleRenderer.Shape shape;
   private float age;
   private float rotation;
   private float rotationVelocity;
   private final float seed;
   private final float phase;

   public ProceduralParticleState(
      double x, double y, double z, double velocityX, double velocityY, double velocityZ, int lifetime, ProceduralParticleRenderer.Shape shape, Random random
   ) {
      this.previousX = this.x = x;
      this.previousY = this.y = y;
      this.previousZ = this.z = z;
      this.velocityX = velocityX;
      this.velocityY = velocityY;
      this.velocityZ = velocityZ;
      this.lifetime = lifetime;
      this.shape = shape;
      this.rotation = (float)(random.nextDouble() * 3.141593430080217 * 2.0);
      this.rotationVelocity = (float)(random.nextDouble() - 0.5) * 0.2F;
      this.seed = random.nextFloat();
      this.phase = random.nextFloat() * 100.0F;
   }

   public static Vec3d randomVelocity(Random random) {
      return new Vec3d(
         (random.nextDouble() - 0.5) * 0.2000000000678856, (random.nextDouble() - 0.5) * 0.2000000000678856, (random.nextDouble() - 0.5) * 0.2000000000678856
      );
   }

   public static boolean isPositionBlocked(World level, Vec3d position) {
      BlockPos blockPos = BlockPos.ofFloored(position);
      BlockState state = level.getBlockState(blockPos);
      return !state.isAir() && !state.getCollisionShape(level, blockPos).isEmpty();
   }

   public boolean update(World level, float configuredSize, float delta, Random random) {
      this.age += delta;
      if (this.age >= (float)this.lifetime) {
         return false;
      } else {
         this.previousX = this.x;
         this.previousY = this.y;
         this.previousZ = this.z;
         this.rotation = this.rotation + this.rotationVelocity * delta;
         this.rotationVelocity *= 0.985F;
         this.velocityX = this.velocityX + (random.nextDouble() - 0.5) * 0.001999998851479194 * (double)delta;
         this.velocityZ = this.velocityZ + (random.nextDouble() - 0.5) * 0.001999998851479194 * (double)delta;
         double drag = Math.pow(0.9599996591960513, (double)delta);
         this.velocityX *= drag;
         this.velocityY = this.velocityY * drag + 0.0030000000694913086 * (double)delta;
         this.velocityZ *= drag;
         if (Math.abs(this.velocityY) < 0.010000003725784143) {
            this.velocityY = 0.0;
         }

         double requestedX = this.velocityX * (double)delta;
         double requestedY = this.velocityY * (double)delta;
         double requestedZ = this.velocityZ * (double)delta;
         Box box = new Box(
            this.x - (double)configuredSize,
            this.y - (double)configuredSize,
            this.z - (double)configuredSize,
            this.x + (double)configuredSize,
            this.y + (double)configuredSize,
            this.z + (double)configuredSize
         );
         double clippedY = clipAxis(level, box, requestedY, Axis.Y);
         if (clippedY != requestedY) {
            this.velocityY = -this.velocityY * 0.5500001584216989;
            this.rotationVelocity = (float)(random.nextDouble() - 0.5) * 0.3F;
            if (requestedY < 0.0) {
               this.velocityX *= 0.75;
               this.velocityZ *= 0.75;
            }

            clippedY = signedCollisionEpsilon(clippedY, requestedY);
         }

         this.y += clippedY;
         box = box.offset(0.0, clippedY, 0.0);
         double clippedX = clipAxis(level, box, requestedX, Axis.X);
         if (clippedX != requestedX) {
            this.velocityX = -this.velocityX * 0.5500001584216989;
            this.velocityY *= 0.8999997820741558;
            this.velocityZ *= 0.8999997820741558;
            clippedX = signedCollisionEpsilon(clippedX, requestedX);
         }

         this.x += clippedX;
         box = box.offset(clippedX, 0.0, 0.0);
         double clippedZ = clipAxis(level, box, requestedZ, Axis.Z);
         if (clippedZ != requestedZ) {
            this.velocityZ = -this.velocityZ * 0.5500001584216989;
            this.velocityY *= 0.8999997820741558;
            this.velocityX *= 0.8999997820741558;
            clippedZ = signedCollisionEpsilon(clippedZ, requestedZ);
         }

         this.z += clippedZ;
         if (Math.abs(this.velocityX) < 1.0000000000249859E-5) {
            this.velocityX = 0.0;
         }

         if (Math.abs(this.velocityY) < 1.0000000000249859E-5) {
            this.velocityY = 0.0;
         }

         if (Math.abs(this.velocityZ) < 1.0000000000249859E-5) {
            this.velocityZ = 0.0;
         }

         return true;
      }
   }

   public Vec3d interpolatedPosition(float tickDelta) {
      return new Vec3d(
         this.previousX + (this.x - this.previousX) * (double)tickDelta,
         this.previousY + (this.y - this.previousY) * (double)tickDelta,
         this.previousZ + (this.z - this.previousZ) * (double)tickDelta
      );
   }

   public float renderRotation(float tickDelta) {
      return this.rotation + this.rotationVelocity * tickDelta;
   }

   public float normalizedLife() {
      return Math.clamp(this.age / (float)this.lifetime, 0.0F, 1.0F);
   }

   public float opacityEnvelope() {
      float progress = this.normalizedLife();
      if (progress < 0.2F) {
         return progress / 0.2F;
      } else {
         return progress > 0.8F ? (1.0F - progress) / 0.2F : 1.0F;
      }
   }

   public ProceduralParticleRenderer.Shape shape() {
      return this.shape;
   }

   public float seed() {
      return this.seed;
   }

   public float phase() {
      return this.phase;
   }

   private static double clipAxis(World level, Box box, double movement, Axis axis) {
      if (movement == 0.0) {
         return 0.0;
      } else {
         Box expanded = switch (axis) {
            case X -> box.stretch(movement, 0.0, 0.0);
            case Y -> box.stretch(0.0, movement, 0.0);
            case Z -> box.stretch(0.0, 0.0, movement);
            default -> throw new MatchException(null, null);
         };
         return VoxelShapes.calculateMaxOffset(axis, box, level.getBlockCollisions(null, expanded), movement);
      }
   }

   private static double signedCollisionEpsilon(double clipped, double requested) {
      if (Math.abs(clipped) >= 9.99999563509513E-5) {
         return clipped;
      } else {
         return requested < 0.0 ? 9.99999563509513E-5 : -9.999997135847346E-5;
      }
   }
}
