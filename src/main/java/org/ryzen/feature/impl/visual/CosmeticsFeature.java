package org.ryzen.feature.impl.visual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.FriendManager;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.Theme;
import org.ryzen.utils.render.world.WorldMeshRenderer;

@Environment(EnvType.CLIENT)
public final class CosmeticsFeature extends Feature {
   private static final String TYPE_WINGS = "Wings";
   private static final String TYPE_KATANA = "Katana";
   private static final String STYLE_GRADIENT = "Gradient";
   private static final String STYLE_SOLID = "Solid";
   private static final String PALETTE_ACCENT = "Accent";
   private static final String PALETTE_PLASMA = "Plasma";
   private static final String PALETTE_BALATRO = "Balatro";
   private static final double[][] WING_OUTLINE = new double[][]{
      {0.06, -0.38, -0.01},
      {0.28, -0.52, -0.04},
      {0.22, -0.36, -0.06},
      {0.48, -0.44, -0.09},
      {0.38, -0.26, -0.11},
      {0.66, -0.3, -0.13},
      {0.56, -0.1, -0.14},
      {0.74, -0.02, -0.15},
      {0.58, 0.12, -0.12},
      {0.4, 0.22, -0.08},
      {0.22, 0.24, -0.04},
      {0.08, 0.14, -0.01}
   };
   private static final int[] WING_BONES = new int[]{1, 3, 5, 7};
   private static final double WING_ANCHOR_UP = 1.25;
   private static final double WING_ANCHOR_BACK = 0.28;
   private static final double WING_ROOT_SIDE = 0.13;
   private static final float WING_SWEEP_IDLE = 15.0F;
   private static final float WING_SWEEP_GLIDING = 35.0F;
   private static final double BONE_ROOT_HALF_WIDTH = 0.022;
   private static final double BONE_TIP_HALF_WIDTH = 0.004;
   private static final double EDGE_HALF_WIDTH = 0.01;
   private static final float GLIDE_SMOOTHING = 0.12F;
   private static final long START_MILLIS = System.currentTimeMillis();
   private static final double KATANA_ANCHOR_UP = 1.501;
   private static final double KATANA_ANCHOR_SIDE = 0.3;
   private static final double KATANA_ANCHOR_BACK = 0.18;
   private static final float KATANA_TILT_DEGREES = -38.0F;
   private static final float KATANA_YAW_DEGREES = 2.0F;
   public final ModeSetting type = this.register(new ModeSetting("Type", "Wings", "Wings", "Katana"));
   public final ModeSetting style = this.register(new ModeSetting("Style", "Gradient", "Gradient", "Solid"));
   public final ModeSetting palette = this.register(new ModeSetting("Palette", "Accent", "Accent", "Plasma", "Balatro"));
   public final NumberSetting size = this.register(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05, "x"));
   public final NumberSetting glow = this.register(new NumberSetting("Glow", 1.0, 0.0, 2.0, 0.1, "").visibleWhen(() -> this.type.is("Wings")));
   public final BooleanSetting onFriends = this.register(new BooleanSetting("On Friends", true));
   public final BooleanSetting thirdPersonOnly = this.register(new BooleanSetting("Third Person Only", false));
   private final Map<UUID, Float> glideBlend = new HashMap<>();

   public CosmeticsFeature() {
      super("Cosmetics", "Procedural wings or a katana on you and your friends", FeatureCategory.VISUAL, -1);
   }

   public static CosmeticsFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(CosmeticsFeature.class);
   }

   @Override
   protected void onDisable() {
      this.glideBlend.clear();
   }

   public void renderWorld(float tickDelta) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world != null && client.player != null) {
         List<WorldMeshRenderer.Tri> tris = new ArrayList<>();
         boolean wings = this.type.is("Wings");

         for (PlayerEntity player : client.world.getPlayers()) {
            if (this.shouldDecorate(client, player)) {
               CosmeticsFeature.Frame frame = this.frameOf(player, tickDelta);
               if (wings) {
                  this.buildWings(player, frame, tris);
               } else {
                  this.buildKatana(frame, tris);
               }
            }
         }

         WorldMeshRenderer.WorldMesh mesh = new WorldMeshRenderer.WorldMesh(List.of(), List.of(), List.of(), tris);
         if (!mesh.isEmpty()) {
            WorldMeshRenderer.render(mesh, false);
         }
      }
   }

   private boolean shouldDecorate(MinecraftClient client, PlayerEntity player) {
      if (!player.isAlive() || player.isSpectator() || player.isInvisible()) {
         return false;
      } else {
         return player == client.player
            ? !this.thirdPersonOnly.getValue() || !client.options.getPerspective().isFirstPerson()
            : this.onFriends.getValue() && FriendManager.INSTANCE.isFriend(player.getGameProfile().name());
      }
   }

   private CosmeticsFeature.Frame frameOf(PlayerEntity player, float tickDelta) {
      Vec3d position = Render3DUtil.interpolatedPosition(player, tickDelta);
      float yaw = MathHelper.lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);
      double radians = Math.toRadians((double)yaw);
      double sin = Math.sin(radians);
      double cos = Math.cos(radians);
      Vec3d forward = new Vec3d(-sin, 0.0, cos);
      Vec3d right = new Vec3d(-cos, 0.0, -sin);
      double scale = this.size.getValue() * (player.isInSneakingPose() ? 0.85 : 1.0);
      return new CosmeticsFeature.Frame(position, right, forward.multiply(-1.0), scale);
   }

   private void buildWings(PlayerEntity player, CosmeticsFeature.Frame frame, List<WorldMeshRenderer.Tri> tris) {
      float seconds = animationSeconds();
      float glide = this.glideBlend(player);
      double breathe = 1.0 + Math.sin((double)seconds * 1.8) * 0.02;
      float flap = player.isGliding() ? (float)(Math.sin((double)seconds * 4.0) * 0.06) : 0.0F;
      float sweep = MathHelper.lerp(glide, 15.0F, 35.0F);
      int accent = this.tintedAccent(seconds);
      int membraneInner = ColorUtil.withAlpha(accent, 216);
      int membraneOuter = ColorUtil.withAlpha(scaleColor(accent, 0.3F), 130);
      int boneColor = ColorUtil.withAlpha(scaleColor(accent, 1.5F), 216);
      double glowStrength = this.glow.getValue();
      double yaw = Math.toRadians((double)sweep);
      double tilt = Math.toRadians((double)flap * 30.0);

      for (boolean mirrored : new boolean[]{false, true}) {
         double side = mirrored ? -1.0 : 1.0;
         if (glowStrength > 0.0) {
            double pulse = 1.0 + Math.sin((double)seconds * 3.0) * 0.15;
            double[][] halo = new double[][]{{1.15, 0.12}, {1.3, 0.06}, {1.5, 0.025}};

            for (double[] layer : halo) {
               int color = ColorUtil.withAlpha(accent, (int)(layer[1] * glowStrength * pulse * 255.0));
               this.addWingFan(frame, tris, side, yaw, tilt, breathe * layer[0], color, color);
            }
         }

         this.addWingFan(frame, tris, side, yaw, tilt, breathe, membraneInner, membraneOuter);
         this.addWingStructure(frame, tris, side, yaw, tilt, breathe, boneColor);
      }
   }

   private void addWingFan(
      CosmeticsFeature.Frame frame, List<WorldMeshRenderer.Tri> tris, double side, double yaw, double tilt, double scale, int innerColor, int outerColor
   ) {
      boolean gradient = this.style.is("Gradient") && innerColor != outerColor;
      Vec3d root = this.wingPoint(frame, side, yaw, tilt, scale, 0.0, 0.0, 0.0);

      for (int index = 0; index < WING_OUTLINE.length - 1; index++) {
         Vec3d first = this.wingPoint(frame, side, yaw, tilt, scale, WING_OUTLINE[index]);
         Vec3d second = this.wingPoint(frame, side, yaw, tilt, scale, WING_OUTLINE[index + 1]);
         int firstColor;
         int secondColor;
         if (gradient) {
            firstColor = sheen(innerColor, first.subtract(root));
            secondColor = sheen(innerColor, second.subtract(root));
         } else {
            firstColor = membraneColor(innerColor, outerColor, WING_OUTLINE[index]);
            secondColor = membraneColor(innerColor, outerColor, WING_OUTLINE[index + 1]);
         }

         tris.add(new WorldMeshRenderer.Tri(root, first, second, innerColor, firstColor, secondColor));
      }
   }

   private void addWingStructure(CosmeticsFeature.Frame frame, List<WorldMeshRenderer.Tri> tris, double side, double yaw, double tilt, double scale, int color) {
      int edgeColor = ColorUtil.multiplyAlpha(color, 0.7F);

      for (int index : WING_BONES) {
         double[] tip = WING_OUTLINE[index];
         this.addWingStrip(frame, tris, side, yaw, tilt, scale, 0.0, 0.0, 0.0, tip[0], tip[1], tip[2], 0.022, 0.004, color);
      }

      double[] previous = new double[]{0.0, 0.0, 0.0};

      for (double[] point : WING_OUTLINE) {
         this.addWingStrip(frame, tris, side, yaw, tilt, scale, previous[0], previous[1], previous[2], point[0], point[1], point[2], 0.01, 0.01, edgeColor);
         previous = point;
      }

      this.addWingStrip(frame, tris, side, yaw, tilt, scale, previous[0], previous[1], previous[2], 0.0, 0.0, 0.0, 0.01, 0.01, edgeColor);
   }

   private void addWingStrip(
      CosmeticsFeature.Frame frame,
      List<WorldMeshRenderer.Tri> tris,
      double side,
      double yaw,
      double tilt,
      double scale,
      double fromX,
      double fromY,
      double fromZ,
      double toX,
      double toY,
      double toZ,
      double fromHalfWidth,
      double toHalfWidth,
      int color
   ) {
      double dirX = toX - fromX;
      double dirY = toY - fromY;
      double length = Math.sqrt(dirX * dirX + dirY * dirY);
      if (!(length < 1.0E-6)) {
         double normalX = -dirY / length;
         double normalY = dirX / length;
         double zLift = -0.004;
         Vec3d a = this.wingPoint(frame, side, yaw, tilt, scale, fromX + normalX * fromHalfWidth, fromY + normalY * fromHalfWidth, fromZ + zLift);
         Vec3d b = this.wingPoint(frame, side, yaw, tilt, scale, fromX - normalX * fromHalfWidth, fromY - normalY * fromHalfWidth, fromZ + zLift);
         Vec3d c = this.wingPoint(frame, side, yaw, tilt, scale, toX - normalX * toHalfWidth, toY - normalY * toHalfWidth, toZ + zLift);
         Vec3d d = this.wingPoint(frame, side, yaw, tilt, scale, toX + normalX * toHalfWidth, toY + normalY * toHalfWidth, toZ + zLift);
         tris.add(new WorldMeshRenderer.Tri(a, b, c, color));
         tris.add(new WorldMeshRenderer.Tri(a, c, d, color));
      }
   }

   private static int membraneColor(int innerColor, int outerColor, double[] point) {
      double distance = Math.sqrt(point[0] * point[0] + point[1] * point[1] + point[2] * point[2]);
      return ColorUtil.lerp(innerColor, outerColor, (float)Math.min(distance / 0.7, 1.0));
   }

   private Vec3d wingPoint(CosmeticsFeature.Frame frame, double side, double yaw, double tilt, double scale, double[] point) {
      return this.wingPoint(frame, side, yaw, tilt, scale, point[0], point[1], point[2]);
   }

   private Vec3d wingPoint(CosmeticsFeature.Frame frame, double side, double yaw, double tilt, double scale, double localX, double localY, double localZ) {
      double geometryScale = scale * frame.scale();
      double x = localX * geometryScale;
      double y = localY * geometryScale;
      double z = localZ * geometryScale;
      double tiltedX = x * Math.cos(tilt) - y * Math.sin(tilt);
      double tiltedY = x * Math.sin(tilt) + y * Math.cos(tilt);
      double sweptX = tiltedX * Math.cos(yaw) - z * Math.sin(yaw);
      double sweptZ = tiltedX * Math.sin(yaw) + z * Math.cos(yaw);
      return frame.at((0.13 + sweptX) * side, 1.25 + tiltedY, 0.28 + sweptZ);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (event.getClient().world != null) {
         Set<UUID> present = new HashSet<>();

         for (PlayerEntity player : event.getClient().world.getPlayers()) {
            UUID id = player.getUuid();
            present.add(id);
            float target = player.isGliding() ? 1.0F : 0.0F;
            float current = this.glideBlend.getOrDefault(id, target);
            this.glideBlend.put(id, current + (target - current) * 0.12F);
         }

         this.glideBlend.keySet().retainAll(present);
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.glideBlend.clear();
   }

   private float glideBlend(PlayerEntity player) {
      return this.glideBlend.getOrDefault(player.getUuid(), player.isGliding() ? 1.0F : 0.0F);
   }

   private void buildKatana(CosmeticsFeature.Frame frame, List<WorldMeshRenderer.Tri> tris) {
      float seconds = animationSeconds();
      int accent = this.tintedAccent(seconds);
      double tilt = Math.toRadians(-38.0);
      double bladeHalfX = 0.018;
      double bladeHalfZ = 0.008;
      this.addKatanaBox(frame, tris, tilt, -bladeHalfX, -0.74, -bladeHalfZ, bladeHalfX, -0.06, bladeHalfZ, ColorUtil.withAlpha(accent, 247));
      this.addKatanaTip(frame, tris, tilt, bladeHalfX, bladeHalfZ, ColorUtil.withAlpha(accent, 247));
      this.addKatanaBox(frame, tris, tilt, -0.04, -0.069, -0.016, 0.04, -0.051, 0.016, ColorUtil.withAlpha(scaleColor(accent, 0.6F), 247));
      double wrapStep = 0.0275;

      for (int segment = 0; segment < 8; segment++) {
         double top = -0.06 + (double)segment * wrapStep;
         double bottom = -0.06 + (double)(segment + 1) * wrapStep;
         int color = ColorUtil.withAlpha(scaleColor(accent, (segment & 1) == 0 ? 1.3F : 1.7F), 247);
         this.addKatanaBox(frame, tris, tilt, -0.016, top, -0.01, 0.016, bottom, 0.01, color);
      }

      this.addKatanaBox(frame, tris, tilt, -0.02, 0.16, -0.012, 0.02, 0.175, 0.012, ColorUtil.withAlpha(scaleColor(accent, 2.0F), 247));
   }

   private void addKatanaTip(CosmeticsFeature.Frame frame, List<WorldMeshRenderer.Tri> tris, double tilt, double halfX, double halfZ, int color) {
      Vec3d apex = this.katanaPoint(frame, tilt, 0.0, -0.8, 0.0);
      Vec3d a = this.katanaPoint(frame, tilt, -halfX, -0.74, -halfZ);
      Vec3d b = this.katanaPoint(frame, tilt, halfX, -0.74, -halfZ);
      Vec3d c = this.katanaPoint(frame, tilt, halfX, -0.74, halfZ);
      Vec3d d = this.katanaPoint(frame, tilt, -halfX, -0.74, halfZ);
      int tipColor = scalePreservingAlpha(color, 1.6F);
      tris.add(new WorldMeshRenderer.Tri(a, b, apex, color, color, tipColor));
      tris.add(new WorldMeshRenderer.Tri(b, c, apex, color, color, tipColor));
      tris.add(new WorldMeshRenderer.Tri(c, d, apex, color, color, tipColor));
      tris.add(new WorldMeshRenderer.Tri(d, a, apex, color, color, tipColor));
   }

   private void addKatanaBox(
      CosmeticsFeature.Frame frame,
      List<WorldMeshRenderer.Tri> tris,
      double tilt,
      double minX,
      double minY,
      double minZ,
      double maxX,
      double maxY,
      double maxZ,
      int color
   ) {
      Vec3d[] corners = new Vec3d[]{
         this.katanaPoint(frame, tilt, minX, minY, minZ),
         this.katanaPoint(frame, tilt, maxX, minY, minZ),
         this.katanaPoint(frame, tilt, maxX, maxY, minZ),
         this.katanaPoint(frame, tilt, minX, maxY, minZ),
         this.katanaPoint(frame, tilt, minX, minY, maxZ),
         this.katanaPoint(frame, tilt, maxX, minY, maxZ),
         this.katanaPoint(frame, tilt, maxX, maxY, maxZ),
         this.katanaPoint(frame, tilt, minX, maxY, maxZ)
      };
      int[][] faces = new int[][]{{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
      float[] faceShades = new float[]{1.0F, 0.85F, 0.7F, 0.7F, 0.95F, 0.9F};
      boolean gradient = this.style.is("Gradient");

      for (int face = 0; face < faces.length; face++) {
         Vec3d p0 = corners[faces[face][0]];
         Vec3d p1 = corners[faces[face][1]];
         Vec3d p2 = corners[faces[face][2]];
         Vec3d p3 = corners[faces[face][3]];
         int shaded = gradient ? sheen(color, p1.subtract(p0).crossProduct(p3.subtract(p0))) : scalePreservingAlpha(color, faceShades[face]);
         tris.add(new WorldMeshRenderer.Tri(p0, p1, p2, shaded));
         tris.add(new WorldMeshRenderer.Tri(p0, p2, p3, shaded));
      }
   }

   private Vec3d katanaPoint(CosmeticsFeature.Frame frame, double tilt, double localX, double localY, double localZ) {
      double x = localX * frame.scale();
      double y = localY * frame.scale();
      double z = localZ * frame.scale();
      double tiltedX = x * Math.cos(tilt) - y * Math.sin(tilt);
      double tiltedY = x * Math.sin(tilt) + y * Math.cos(tilt);
      double yaw = Math.toRadians(2.0);
      double yawedX = tiltedX * Math.cos(yaw) - z * Math.sin(yaw);
      double yawedZ = tiltedX * Math.sin(yaw) + z * Math.cos(yaw);
      return frame.at(0.3 + yawedX, 1.501 + tiltedY, 0.18 + yawedZ);
   }

   private static int sheen(int base, Vec3d direction) {
      if (direction.lengthSquared() < 1.0E-10) {
         return base;
      } else {
         Vec3d unit = direction.normalize();
         int tint = ColorUtil.rgba((float)(unit.x * 0.5 + 0.5), (float)(unit.y * 0.5 + 0.5), (float)(unit.z * 0.5 + 0.5), 1.0F);
         return ColorUtil.withAlpha(ColorUtil.lerp(base, tint, 0.55F), ColorUtil.alpha(base));
      }
   }

   private int tintedAccent(float seconds) {
      int accent = Theme.getAccent();
      if (this.palette.is("Plasma")) {
         return shiftHue(accent, seconds * 0.05F);
      } else if (this.palette.is("Balatro")) {
         float phase = (float)(Math.sin((double)seconds * 1.2) * 0.5 + 0.5);
         return ColorUtil.lerp(accent, shiftHue(accent, 0.5F), phase);
      } else {
         return accent;
      }
   }

   private static int shiftHue(int color, float turns) {
      float[] hsv = ColorUtil.hsv(color);
      return ColorUtil.fromHsv(hsv[0] + turns, hsv[1], hsv[2], ColorUtil.alpha(color));
   }

   private static int scaleColor(int color, float factor) {
      return ColorUtil.multiplyRgb(color, factor);
   }

   private static int scalePreservingAlpha(int color, float factor) {
      return ColorUtil.multiplyRgb(color, factor);
   }

   private static float animationSeconds() {
      return (float)(System.currentTimeMillis() - START_MILLIS) / 1000.0F;
   }

   @Environment(EnvType.CLIENT)
   private static record Frame(Vec3d origin, Vec3d right, Vec3d back, double scale) {
      Vec3d at(double side, double up, double backward) {
         return this.origin.add(this.right.multiply(side)).add(0.0, up, 0.0).add(this.back.multiply(backward));
      }
   }
}
