package org.ryzen.feature.impl.visual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3fc;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.AttackEvent;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.world.WorldMeshRenderer;

@Environment(EnvType.CLIENT)
public final class KillEffectFeature extends Feature {
   private static final String EFFECT_BEAM = "Beam";
   private static final String EFFECT_SOUL = "Soul";
   private static final String EFFECT_RUNES = "Runes";
   private static final long CREDIT_WINDOW_MS = 3000L;
   private static final int RING_SEGMENTS = 48;
   public final ModeSetting effect = this.register(new ModeSetting("Effect", "Beam", "Beam", "Soul", "Runes"));
   public final NumberSetting duration = this.register(new NumberSetting("Duration", 1200.0, 400.0, 3500.0, 50.0, " ms"));
   public final NumberSetting intensity = this.register(new NumberSetting("Intensity", 1.2, 0.1, 3.0, 0.05, ""));
   public final NumberSetting beamHeight = this.register(
      new NumberSetting("Beam Height", 10.0, 3.0, 25.0, 0.5, " blocks").visibleWhen(() -> this.effect.is("Beam"))
   );
   public final NumberSetting soulHeight = this.register(
      new NumberSetting("Soul Height", 2.5, 0.6, 6.0, 0.1, " blocks").visibleWhen(() -> this.effect.is("Soul"))
   );
   public final NumberSetting runeSize = this.register(new NumberSetting("Rune Size", 3.2, 0.8, 8.0, 0.1, " blocks").visibleWhen(() -> this.effect.is("Runes")));
   public final ColorSetting color = this.register(new ColorSetting("Color", 16777215));
   private final List<KillEffectFeature.Burst> bursts = new ArrayList<>();
   private final Map<Integer, Long> recentHits = new HashMap<>();
   private final Map<Integer, Vec3d> lastKnownPositions = new HashMap<>();

   public KillEffectFeature() {
      super("KillEffect", "Plays an effect where a player you killed died", FeatureCategory.VISUAL, -1);
   }

   public static KillEffectFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(KillEffectFeature.class);
   }

   @Override
   protected void onDisable() {
      this.clear();
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.clear();
   }

   @EventTarget
   public void onAttack(AttackEvent event) {
      if (event.getClient().targetedEntity instanceof PlayerEntity player) {
         this.recentHits.put(player.getId(), System.currentTimeMillis());
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      if (client.world != null) {
         long now = System.currentTimeMillis();
         this.recentHits.entrySet().removeIf(entry -> now - entry.getValue() > 3000L);
         Iterator<Entry<Integer, Long>> iterator = this.recentHits.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<Integer, Long> entry = iterator.next();
            Entity entity = client.world.getEntityById(entry.getKey());
            if (entity instanceof LivingEntity living && living.isAlive()) {
               this.lastKnownPositions.put(entry.getKey(), living.getEntityPos());
               continue;
            }

            Vec3d position = entity != null ? entity.getEntityPos() : this.lastKnownPositions.get(entry.getKey());
            if (position != null) {
               this.bursts.add(new KillEffectFeature.Burst(position, now));
            }

            this.lastKnownPositions.remove(entry.getKey());
            iterator.remove();
         }

         long lifetime = this.duration.getValue().longValue();
         this.bursts.removeIf(burst -> now - burst.startedAt() > lifetime);
      }
   }

   public void renderWorld() {
      if (!this.bursts.isEmpty()) {
         long now = System.currentTimeMillis();
         double lifetime = Math.max(1.0, this.duration.getValue());
         List<WorldMeshRenderer.Line> lines = new ArrayList<>();
         List<WorldMeshRenderer.Ring> rings = new ArrayList<>();
         List<WorldMeshRenderer.PlaneRect> quads = new ArrayList<>();

         for (KillEffectFeature.Burst burst : List.copyOf(this.bursts)) {
            double progress = MathHelper.clamp((double)(now - burst.startedAt()) / lifetime, 0.0, 1.0);
            float alpha = (float)Math.pow(1.0 - progress, 1.6) * this.intensity.getValue().floatValue();
            if (!(alpha <= 0.01F)) {
               String var13 = this.effect.getValue();
               switch (var13) {
                  case "Soul":
                     this.buildSoul(burst, progress, alpha, quads);
                     break;
                  case "Runes":
                     this.buildRunes(burst, progress, alpha, rings);
                     break;
                  default:
                     this.buildBeam(burst, progress, alpha, lines);
               }
            }
         }

         WorldMeshRenderer.WorldMesh mesh = new WorldMeshRenderer.WorldMesh(lines, rings, quads);
         if (!mesh.isEmpty()) {
            WorldMeshRenderer.render(mesh, true);
         }
      }
   }

   private void buildBeam(KillEffectFeature.Burst burst, double progress, float alpha, List<WorldMeshRenderer.Line> lines) {
      double height = this.beamHeight.getValue() * (0.4 + progress * 0.6);
      Vec3d base = burst.position();
      Vec3d top = base.add(0.0, height, 0.0);
      int bottomColor = ColorUtil.withAlpha(this.color.getValue(), (int)(alpha * 255.0F));
      int topColor = ColorUtil.withAlpha(this.color.getValue(), 0);
      lines.add(new WorldMeshRenderer.Line(base, top, bottomColor, topColor));

      for (int strand = 0; strand < 3; strand++) {
         double angle = progress * Math.PI * 2.0 + (double)strand * (Math.PI * 2.0 / 3.0);
         double radius = 0.25 + progress * 0.35;
         Vec3d offset = new Vec3d(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
         lines.add(new WorldMeshRenderer.Line(base.add(offset), top.add(offset.multiply(0.3)), bottomColor, topColor));
      }
   }

   private void buildSoul(KillEffectFeature.Burst burst, double progress, float alpha, List<WorldMeshRenderer.PlaneRect> quads) {
      double rise = this.soulHeight.getValue() * progress;
      double size = 0.45 * (1.0 - progress * 0.55);
      Vec3d center = burst.position().add(0.0, 0.9 + rise, 0.0);
      Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
      Vector3fc left = camera.getDiagonalPlane();
      Vector3fc up = camera.getVerticalPlane();
      Vec3d axis = new Vec3d((double)left.x(), (double)left.y(), (double)left.z());
      Vec3d normal = new Vec3d((double)up.x(), (double)up.y(), (double)up.z()).crossProduct(axis);
      if (!(axis.lengthSquared() < 1.0E-6) && !(normal.lengthSquared() < 1.0E-6)) {
         quads.add(
            new WorldMeshRenderer.PlaneRect(
               center, axis.normalize(), normal.normalize(), size, size, ColorUtil.withAlpha(this.color.getValue(), (int)(alpha * 200.0F))
            )
         );
      }
   }

   private void buildRunes(KillEffectFeature.Burst burst, double progress, float alpha, List<WorldMeshRenderer.Ring> rings) {
      Vec3d center = burst.position().add(0.0, 0.05, 0.0);
      Vec3d u = new Vec3d(1.0, 0.0, 0.0);
      Vec3d v = new Vec3d(0.0, 0.0, 1.0);
      double base = this.runeSize.getValue();

      for (int ring = 0; ring < 3; ring++) {
         double scale = 0.4 + progress * (0.6 + (double)ring * 0.25);
         rings.add(
            new WorldMeshRenderer.Ring(
               center.add(0.0, (double)ring * 0.15, 0.0),
               u,
               v,
               base * scale * (1.0 - (double)ring * 0.18),
               0.03 + (double)ring * 0.01,
               ColorUtil.withAlpha(this.color.getValue(), (int)(alpha * (float)(200 - ring * 45))),
               48
            )
         );
      }
   }

   private void clear() {
      this.bursts.clear();
      this.recentHits.clear();
      this.lastKnownPositions.clear();
   }

   @Environment(EnvType.CLIENT)
   private static record Burst(Vec3d position, long startedAt) {
   }
}
