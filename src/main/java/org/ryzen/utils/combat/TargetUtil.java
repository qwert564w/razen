package org.ryzen.utils.combat;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot.Type;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.ryzen.context.RotationContext;
import org.ryzen.utils.FriendManager;

@Environment(EnvType.CLIENT)
public final class TargetUtil {
   private TargetUtil() {
   }

   public static LivingEntity getBestTarget(ClientPlayerEntity player, ClientWorld level, double range, TargetFilter filter) {
      return getBestTarget(player, level, range, filter, entity -> true);
   }

   public static LivingEntity getBestTarget(
      ClientPlayerEntity player, ClientWorld level, double range, TargetFilter filter, Predicate<LivingEntity> extraFilter
   ) {
      List<LivingEntity> targets = getTargets(player, level, range, filter);
      targets.removeIf(extraFilter.negate());
      if (targets.isEmpty()) {
         return null;
      } else {
         targets.sort(Comparator.comparingDouble(entity -> calculateTargetScore(player, entity, range, filter)));
         return targets.get(0);
      }
   }

   public static List<LivingEntity> getTargets(ClientPlayerEntity player, ClientWorld level, double range, TargetFilter filter) {
      double rangeSqr = range * range;
      return StreamSupport.<Entity>stream(level.getEntities().spliterator(), false)
         .filter(entity -> entity instanceof LivingEntity)
         .map(entity -> (LivingEntity)entity)
         .filter(entity -> isValidTarget(player, entity, rangeSqr, filter))
         .collect(Collectors.toList());
   }

   public static boolean isValidTarget(ClientPlayerEntity player, LivingEntity entity, double rangeSqr, TargetFilter filter) {
      if (entity == player) {
         return false;
      } else if (entity.isAlive() && entity.isAttackable()) {
         if (player.squaredDistanceTo(entity) > rangeSqr) {
            return false;
         } else if (entity.isInvisible() && !filter.isTargetsInvisibles()) {
            return false;
         } else {
            if (entity instanceof PlayerEntity targetPlayer) {
               if (targetPlayer.isSpectator() || targetPlayer.isCreative()) {
                  return false;
               }

               if (FriendManager.INSTANCE.isFriend(targetPlayer.getGameProfile().name()) && !filter.isTargetsFriends()) {
                  return false;
               }

               if (isNaked(targetPlayer) && !filter.isTargetsNakedPlayers()) {
                  return false;
               }

               if (!filter.isTargetsPlayers()) {
                  return false;
               }
            } else if (entity instanceof HostileEntity) {
               if (!filter.isTargetsMonsters()) {
                  return false;
               }
            } else if (!(entity instanceof AnimalEntity) && !(entity instanceof AmbientEntity)) {
               if (!(entity instanceof VillagerEntity)) {
                  return false;
               }

               if (!filter.isTargetsVillagers()) {
                  return false;
               }
            } else if (!filter.isTargetsAnimals()) {
               return false;
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private static double calculateTargetScore(ClientPlayerEntity player, LivingEntity entity, double maxRange, TargetFilter filter) {
      double distance = (double)player.distanceTo(entity);
      double normalizedDist = distance / maxRange;
      double normalizedHealth = (double)(entity.getHealth() / entity.getMaxHealth());
      double normalizedArmor = (double)entity.getArmor() / 20.0;
      double fov = (double)RotationContext.getFovToEntity(player, entity);
      double normalizedFov = fov / 180.0;
      return normalizedDist * filter.getDistanceWeight()
         + normalizedHealth * filter.getHealthWeight()
         + normalizedArmor * filter.getArmorWeight()
         + normalizedFov * filter.getFovWeight();
   }

   private static boolean isNaked(PlayerEntity player) {
      for (EquipmentSlot slot : EquipmentSlot.values()) {
         if (slot.getType() == Type.HUMANOID_ARMOR) {
            ItemStack stack = player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
               return false;
            }
         }
      }

      return true;
   }
}
