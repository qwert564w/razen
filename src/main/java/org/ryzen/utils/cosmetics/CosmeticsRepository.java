package org.ryzen.utils.cosmetics;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.ryzen.context.MinecraftContext;
import org.ryzen.utils.ConfigIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class CosmeticsRepository {
   private static final Logger LOGGER = LoggerFactory.getLogger(CosmeticsRepository.class);
   private static final String FOLDER = "cosmetics";
   private static final String AVATAR_FILE = "avatar.json";
   private static final String PREVIEW_FILE = "avatar.png";
   private static final String HEAD_PREFIX = "head-";
   private static final String WEAPON_PREFIX = "weapon-";
   private static final String BUNDLED_ROOT = "/ryzen/cosmetics/";
   private static final String BUNDLED_INDEX = "/ryzen/cosmetics.index";
   private static final int MAX_DEPTH = 4;
   private static final List<CosmeticEntry> ENTRIES = new ArrayList<>();
   private static boolean scanned;
   private static boolean extracted;

   private CosmeticsRepository() {
   }

   public static Path directory() {
      return ConfigIO.resolve("cosmetics");
   }

   public static List<CosmeticEntry> all() {
      if (!scanned) {
         rescan();
      }

      return ENTRIES;
   }

   public static List<CosmeticEntry> of(CosmeticEntry.Kind kind) {
      List<CosmeticEntry> filtered = new ArrayList<>();

      for (CosmeticEntry entry : all()) {
         if (entry.kind() == kind) {
            filtered.add(entry);
         }
      }

      return filtered;
   }

   public static CosmeticEntry byId(String id) {
      if (id != null && !id.isBlank()) {
         for (CosmeticEntry entry : all()) {
            if (entry.id().equals(id)) {
               return entry;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static void rescan() {
      scanned = true;
      ENTRIES.clear();
      Path root = directory();

      try {
         Files.createDirectories(root);
      } catch (Exception var2) {
         LOGGER.warn("Could not create the cosmetics folder at {}", root, var2);
         return;
      }

      ensureExtracted(root);
      collect(root, root, 0);
      ENTRIES.sort(Comparator.<CosmeticEntry>comparingInt(entry -> entry.kind().ordinal()).thenComparing(entry -> entry.displayName().toLowerCase(Locale.ROOT)));
   }

   private static void collect(Path root, Path dir, int depth) {
      if (depth <= 4 && Files.isDirectory(dir) && !isHidden(dir)) {
         if (Files.isRegularFile(dir.resolve("avatar.json"))) {
            if (!dir.equals(root)) {
               ENTRIES.add(toEntry(root, dir));
            }
         } else {
            try (Stream<Path> stream = Files.list(dir)) {
               for (Path child : stream.filter(x$0 -> Files.isDirectory(x$0)).toList()) {
                  collect(root, child, depth + 1);
               }
            } catch (Exception var9) {
               LOGGER.debug("Skipping unreadable cosmetics folder {}", dir, var9);
            }
         }
      }
   }

   private static boolean isHidden(Path dir) {
      Path name = dir.getFileName();
      return name != null && name.toString().startsWith(".");
   }

   private static CosmeticEntry toEntry(Path root, Path dir) {
      String id = root.relativize(dir).toString().replace('\\', '/');
      int slash = id.indexOf(47);
      String top = slash < 0 ? id : id.substring(0, slash);
      String lower = top.toLowerCase(Locale.ROOT);
      CosmeticEntry.Kind kind;
      if (lower.startsWith("head-")) {
         kind = CosmeticEntry.Kind.HEAD;
      } else if (lower.startsWith("weapon-")) {
         kind = CosmeticEntry.Kind.WEAPON;
      } else {
         kind = CosmeticEntry.Kind.MODEL;
      }

      return new CosmeticEntry(dir, id, label(dir.getFileName().toString()), kind);
   }

   private static String label(String folderName) {
      String label = folderName;
      String lower = folderName.toLowerCase(Locale.ROOT);
      if (lower.startsWith("head-")) {
         label = folderName.substring("head-".length());
      } else if (lower.startsWith("weapon-")) {
         label = folderName.substring("weapon-".length());
      }

      int dash = label.indexOf(" - ");
      if (dash > 0) {
         label = label.substring(0, dash);
      }

      return label.replace('_', ' ').trim();
   }

   private static void ensureExtracted(Path target) {
      if (!extracted) {
         extracted = true;

         try {
            label65: {
               try (Stream<Path> existing = Files.list(target)) {
                  if (!existing.findAny().isPresent()) {
                     break label65;
                  }
               }

               return;
            }
         } catch (Exception var7) {
            return;
         }

         List<String> index = readIndex();
         if (!index.isEmpty()) {
            int written = 0;

            for (String relative : index) {
               if (copyBundled(relative, target)) {
                  written++;
               }
            }

            LOGGER.info("Unpacked {} of {} bundled cosmetic files into {}", new Object[]{written, index.size(), target});
         }
      }
   }

   private static List<String> readIndex() {
      try {
         Object var11;
         try (InputStream in = CosmeticsRepository.class.getResourceAsStream("/ryzen/cosmetics.index")) {
            if (in == null) {
               LOGGER.warn("No bundled cosmetics index on the classpath at {}", "/ryzen/cosmetics.index");
               return List.of();
            }

            List<String> lines = new ArrayList<>();

            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
               String trimmed = line.strip();
               if (!trimmed.isEmpty()) {
                  lines.add(trimmed);
               }
            }

            var11 = lines;
         }

         return (List<String>)var11;
      } catch (Exception var9) {
         LOGGER.error("Failed to read the bundled cosmetics index", var9);
         return List.of();
      }
   }

   private static boolean copyBundled(String relative, Path target) {
      Path file = resolveChild(target, relative);
      if (file == null) {
         return false;
      } else {
         try {
            boolean var4;
            try (InputStream in = CosmeticsRepository.class.getResourceAsStream("/ryzen/cosmetics/" + relative)) {
               if (in == null) {
                  return false;
               }

               Files.createDirectories(file.getParent());
               Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
               var4 = true;
            }

            return var4;
         } catch (Exception var8) {
            LOGGER.debug("Skipped bundled cosmetic file {}", relative, var8);
            return false;
         }
      }
   }

   private static Path resolveChild(Path target, String relative) {
      if (relative.isEmpty()) {
         return null;
      } else {
         Path resolved = target.resolve(relative).normalize();
         return resolved.startsWith(target.normalize()) ? resolved : null;
      }
   }

   public static Identifier preview(CosmeticEntry entry) {
      if (entry == null) {
         return null;
      } else if (entry.previewLoaded()) {
         return entry.preview();
      } else {
         entry.setPreview(null, 0, 0);
         Path file = entry.folder().resolve("avatar.png");
         if (!Files.isRegularFile(file)) {
            return null;
         } else {
            try {
               Identifier var6;
               try (InputStream in = Files.newInputStream(file)) {
                  NativeImage image = NativeImage.read(in);
                  Identifier id = Identifier.of("ryzen", "cosmetics/" + sanitize(entry.id()));
                  NativeImageBackedTexture texture = new NativeImageBackedTexture(id::toString, image);
                  texture.upload();
                  MinecraftContext.mc.getTextureManager().registerTexture(id, texture);
                  entry.setPreview(id, image.getWidth(), image.getHeight());
                  var6 = id;
               }

               return var6;
            } catch (Exception var9) {
               LOGGER.debug("No usable preview for cosmetic {}", entry.id(), var9);
               return null;
            }
         }
      }
   }

   private static String sanitize(String raw) {
      StringBuilder out = new StringBuilder(raw.length());

      for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
         out.append((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_' && c != '.' && c != '-' ? '_' : c);
      }

      return out.toString();
   }
}
