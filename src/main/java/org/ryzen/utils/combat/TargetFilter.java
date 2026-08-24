package org.ryzen.utils.combat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TargetFilter {
   private final boolean targetsPlayers;
   private final boolean targetsFriends;
   private final boolean targetsNakedPlayers;
   private final boolean targetsInvisibles;
   private final boolean targetsVillagers;
   private final boolean targetsMonsters;
   private final boolean targetsAnimals;
   private final boolean targetsTeams;
   private final double distanceWeight;
   private final double healthWeight;
   private final double armorWeight;
   private final double fovWeight;
   TargetFilter(
      boolean targetsPlayers,
      boolean targetsFriends,
      boolean targetsNakedPlayers,
      boolean targetsInvisibles,
      boolean targetsVillagers,
      boolean targetsMonsters,
      boolean targetsAnimals,
      boolean targetsTeams,
      double distanceWeight,
      double healthWeight,
      double armorWeight,
      double fovWeight
   ) {
      this.targetsPlayers = targetsPlayers;
      this.targetsFriends = targetsFriends;
      this.targetsNakedPlayers = targetsNakedPlayers;
      this.targetsInvisibles = targetsInvisibles;
      this.targetsVillagers = targetsVillagers;
      this.targetsMonsters = targetsMonsters;
      this.targetsAnimals = targetsAnimals;
      this.targetsTeams = targetsTeams;
      this.distanceWeight = distanceWeight;
      this.healthWeight = healthWeight;
      this.armorWeight = armorWeight;
      this.fovWeight = fovWeight;
   }
   public static TargetFilter.TargetFilterBuilder builder() {
      return new TargetFilter.TargetFilterBuilder();
   }
   public boolean isTargetsPlayers() {
      return this.targetsPlayers;
   }
   public boolean isTargetsFriends() {
      return this.targetsFriends;
   }
   public boolean isTargetsNakedPlayers() {
      return this.targetsNakedPlayers;
   }
   public boolean isTargetsInvisibles() {
      return this.targetsInvisibles;
   }
   public boolean isTargetsVillagers() {
      return this.targetsVillagers;
   }
   public boolean isTargetsMonsters() {
      return this.targetsMonsters;
   }
   public boolean isTargetsAnimals() {
      return this.targetsAnimals;
   }
   public boolean isTargetsTeams() {
      return this.targetsTeams;
   }
   public double getDistanceWeight() {
      return this.distanceWeight;
   }
   public double getHealthWeight() {
      return this.healthWeight;
   }
   public double getArmorWeight() {
      return this.armorWeight;
   }
   public double getFovWeight() {
      return this.fovWeight;
   }

   @Environment(EnvType.CLIENT)
   public static class TargetFilterBuilder {
      private boolean targetsPlayers;
      private boolean targetsFriends;
      private boolean targetsNakedPlayers;
      private boolean targetsInvisibles;
      private boolean targetsVillagers;
      private boolean targetsMonsters;
      private boolean targetsAnimals;
      private boolean targetsTeams;
      private double distanceWeight;
      private double healthWeight;
      private double armorWeight;
      private double fovWeight;
      TargetFilterBuilder() {
      }
      public TargetFilter.TargetFilterBuilder targetsPlayers(boolean targetsPlayers) {
         this.targetsPlayers = targetsPlayers;
         return this;
      }
      public TargetFilter.TargetFilterBuilder targetsFriends(boolean targetsFriends) {
         this.targetsFriends = targetsFriends;
         return this;
      }
      public TargetFilter.TargetFilterBuilder targetsNakedPlayers(boolean targetsNakedPlayers) {
         this.targetsNakedPlayers = targetsNakedPlayers;
         return this;
      }
      public TargetFilter.TargetFilterBuilder targetsInvisibles(boolean targetsInvisibles) {
         this.targetsInvisibles = targetsInvisibles;
         return this;
      }
      public TargetFilter.TargetFilterBuilder targetsVillagers(boolean targetsVillagers) {
         this.targetsVillagers = targetsVillagers;
         return this;
      }
      public TargetFilter.TargetFilterBuilder targetsMonsters(boolean targetsMonsters) {
         this.targetsMonsters = targetsMonsters;
         return this;
      }
      public TargetFilter.TargetFilterBuilder targetsAnimals(boolean targetsAnimals) {
         this.targetsAnimals = targetsAnimals;
         return this;
      }
      public TargetFilter.TargetFilterBuilder targetsTeams(boolean targetsTeams) {
         this.targetsTeams = targetsTeams;
         return this;
      }
      public TargetFilter.TargetFilterBuilder distanceWeight(double distanceWeight) {
         this.distanceWeight = distanceWeight;
         return this;
      }
      public TargetFilter.TargetFilterBuilder healthWeight(double healthWeight) {
         this.healthWeight = healthWeight;
         return this;
      }
      public TargetFilter.TargetFilterBuilder armorWeight(double armorWeight) {
         this.armorWeight = armorWeight;
         return this;
      }
      public TargetFilter.TargetFilterBuilder fovWeight(double fovWeight) {
         this.fovWeight = fovWeight;
         return this;
      }
      public TargetFilter build() {
         return new TargetFilter(
            this.targetsPlayers,
            this.targetsFriends,
            this.targetsNakedPlayers,
            this.targetsInvisibles,
            this.targetsVillagers,
            this.targetsMonsters,
            this.targetsAnimals,
            this.targetsTeams,
            this.distanceWeight,
            this.healthWeight,
            this.armorWeight,
            this.fovWeight
         );
      }
      @Override
      public String toString() {
         return "TargetFilter.TargetFilterBuilder(targetsPlayers="
            + this.targetsPlayers
            + ", targetsFriends="
            + this.targetsFriends
            + ", targetsNakedPlayers="
            + this.targetsNakedPlayers
            + ", targetsInvisibles="
            + this.targetsInvisibles
            + ", targetsVillagers="
            + this.targetsVillagers
            + ", targetsMonsters="
            + this.targetsMonsters
            + ", targetsAnimals="
            + this.targetsAnimals
            + ", targetsTeams="
            + this.targetsTeams
            + ", distanceWeight="
            + this.distanceWeight
            + ", healthWeight="
            + this.healthWeight
            + ", armorWeight="
            + this.armorWeight
            + ", fovWeight="
            + this.fovWeight
            + ")";
      }
   }
}
