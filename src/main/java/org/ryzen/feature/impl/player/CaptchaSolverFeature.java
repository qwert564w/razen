package org.ryzen.feature.impl.player;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.map.MapState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.TextSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class CaptchaSolverFeature extends Feature {
   private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9]{2,12}");
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();
   public final TextSetting solverUrl = this.register(new TextSetting("Solver URL", "", 256));
   public final BooleanSetting autoSend = this.register(new BooleanSetting("Auto Send", true));
   public final BooleanSetting saveImage = this.register(new BooleanSetting("Save Image", true));
   private byte[] lastImage = new byte[0];
   private int tick;
   private volatile boolean solving;

   public CaptchaSolverFeature() {
      super("Captcha Solver", "Captures map captchas and submits them to an OCR service", FeatureCategory.PLAYER, -1);
   }

   @Override
   protected void onDisable() {
      this.lastImage = new byte[0];
      this.solving = false;
      this.tick = 0;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      if (client.world != null && client.player != null && !this.solving && ++this.tick >= 10 && client.crosshairTarget instanceof BlockHitResult hit) {
         this.tick = 0;
         List<ItemFrameEntity> frames = client.world
            .getEntitiesByClass(
               ItemFrameEntity.class,
               new Box(hit.getBlockPos()).expand(16.0),
               frame -> frame.containsMap() && FilledMapItem.getMapState(frame.getHeldItemStack(), client.world) != null
            );
         if (!frames.isEmpty()) {
            ItemFrameEntity origin = frames.stream()
               .min(Comparator.comparingDouble(frame -> frame.squaredDistanceTo(CaptchaSolverFeature.Vec3Holder.center(hit.getBlockPos()))))
               .orElse(null);
            if (origin != null) {
               Direction facing = origin.getHorizontalFacing();
               frames = frames.stream().filter(frame -> frame.getHorizontalFacing() == facing).toList();
               byte[] png = this.render(client, frames, facing);
               if (png.length != 0 && !Arrays.equals(png, this.lastImage)) {
                  this.lastImage = png;
                  if (this.saveImage.getValue()) {
                     save(client, png);
                  }

                  String endpoint = this.solverUrl.getValue().trim();
                  if (endpoint.isEmpty()) {
                     ChatUtil.info("Captcha captured. Set Solver URL to enable automatic recognition.");
                  } else {
                     this.submit(client, endpoint, png);
                  }
               }
            }
         }
      }
   }

   private byte[] render(MinecraftClient client, List<ItemFrameEntity> frames, Direction facing) {
      boolean alongZ = facing.getAxis() == Axis.Z;
      boolean flip = facing == Direction.NORTH || facing == Direction.EAST;
      int minU = Integer.MAX_VALUE;
      int maxU = Integer.MIN_VALUE;
      int minV = Integer.MAX_VALUE;
      int maxV = Integer.MIN_VALUE;

      for (ItemFrameEntity frame : frames) {
         BlockPos pos = frame.getBlockPos();
         int u = alongZ ? pos.getX() : pos.getZ();
         minU = Math.min(minU, u);
         maxU = Math.max(maxU, u);
         minV = Math.min(minV, pos.getY());
         maxV = Math.max(maxV, pos.getY());
      }

      if (minU <= maxU && minV <= maxV && maxU - minU <= 8 && maxV - minV <= 8) {
         BufferedImage image = new BufferedImage((maxU - minU + 1) * 128, (maxV - minV + 1) * 128, 2);
         Graphics2D graphics = image.createGraphics();
         graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

         for (ItemFrameEntity frame : frames) {
            MapState data = FilledMapItem.getMapState(frame.getHeldItemStack(), client.world);
            if (data != null && data.colors != null && data.colors.length == 16384) {
               BufferedImage tile = new BufferedImage(128, 128, 2);

               for (int index = 0; index < data.colors.length; index++) {
                  int raw = data.colors[index] & 255;
                  int color = raw < 4 ? 0 : MapColor.getRenderColor(raw);
                  tile.setRGB(index % 128, index / 128, color);
               }

               BlockPos pos = frame.getBlockPos();
               int u = alongZ ? pos.getX() : pos.getZ();
               int x = (flip ? maxU - u : u - minU) * 128;
               int y = (maxV - pos.getY()) * 128;
               AffineTransform transform = AffineTransform.getTranslateInstance((double)x, (double)y);
               transform.rotate(Math.toRadians((double)(frame.getRotation() & 7) * 45.0), 64.0, 64.0);
               graphics.drawImage(tile, transform, null);
            }
         }

         graphics.dispose();

         try {
            byte[] var29;
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
               ImageIO.write(image, "png", output);
               var29 = output.toByteArray();
            }

            return var29;
         } catch (Exception var23) {
            return new byte[0];
         }
      } else {
         return new byte[0];
      }
   }

   private void submit(MinecraftClient client, String endpoint, byte[] png) {
      this.solving = true;
      JsonObject payload = new JsonObject();
      payload.addProperty("image", Base64.getEncoder().encodeToString(png));

      HttpRequest request;
      try {
         request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(20L))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
            .build();
      } catch (IllegalArgumentException var7) {
         this.solving = false;
         ChatUtil.error("Captcha Solver URL is invalid");
         return;
      }

      HTTP.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8))
         .orTimeout(22L, TimeUnit.SECONDS)
         .whenComplete((response, error) -> client.execute(() -> {
               this.solving = false;
               if (this.isEnabled()) {
                  if (error == null && response != null && response.statusCode() / 100 == 2) {
                     String code = extractCode(response.body());
                     if (code == null) {
                        ChatUtil.error("Captcha service returned no code");
                     } else {
                        ChatUtil.success("Captcha recognized: " + code);
                        if (this.autoSend.getValue() && client.player != null && client.player.networkHandler != null) {
                           client.player.networkHandler.sendChatMessage(code);
                        }
                     }
                  } else {
                     ChatUtil.error("Captcha recognition failed");
                  }
               }
            }));
   }

   private static String extractCode(String body) {
      if (body == null) {
         return null;
      } else {
         String value = body.trim();

         try {
            JsonObject json = JsonParser.parseString(value).getAsJsonObject();
            if (json.has("code")) {
               value = json.get("code").getAsString().trim();
            } else if (json.has("text")) {
               value = json.get("text").getAsString().trim();
            }
         } catch (Throwable var3) {
         }

         value = value.replaceAll("[^A-Za-z0-9]", "");
         return SAFE_CODE.matcher(value).matches() ? value : null;
      }
   }

   private static void save(MinecraftClient client, byte[] png) {
      CompletableFuture.runAsync(() -> {
         try {
            Path directory = client.runDirectory.toPath().resolve("ryzen");
            Files.createDirectories(directory);
            Path temporary = directory.resolve("captcha.png.tmp");
            Files.write(temporary, png);
            Files.move(temporary, directory.resolve("captcha.png"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (Exception var4) {
         }
      });
   }

   @Environment(EnvType.CLIENT)
   private static final class Vec3Holder {
      private static Vec3d center(BlockPos pos) {
         return Vec3d.ofCenter(pos);
      }
   }
}
