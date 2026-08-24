package org.ryzen.utils.render.gui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.FloatSize;
import com.github.weisj.jsvg.view.ViewBox;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.texture.NativeImage.Format;
import net.minecraft.util.Identifier;
import org.ryzen.context.MinecraftContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class GuiTexture {
   private static final Logger LOGGER = LoggerFactory.getLogger(GuiTexture.class);
   private static final Map<Identifier, GuiTexture> CACHE = new ConcurrentHashMap<>();
   private static final int MAX_VARIANTS = 8;
   private static final int SUPERSAMPLE = 2;
   private static final int MAX_TEXTURE_SIZE = 2048;
   private static final int OVERSAMPLE = 3;
   private static final int OVERSAMPLE_LIMIT = 256;
   private final Identifier textureId;
   private final boolean svg;
   private volatile SVGDocument document;
   private TextureSetup staticSetup;
   private final LinkedHashMap<Long, TextureSetup> svgVariants = new LinkedHashMap<Long, TextureSetup>(8, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<Long, TextureSetup> eldest) {
         if (this.size() <= 8) {
            return false;
         } else {
            MinecraftContext.mc.getTextureManager().destroyTexture(GuiTexture.variantId(GuiTexture.this.textureId, eldest.getKey()));
            return true;
         }
      }
   };

   private GuiTexture(Identifier textureId) {
      this.textureId = textureId;
      this.svg = textureId.getPath().endsWith(".svg");
   }

   public static GuiTexture load(Identifier textureId) {
      return CACHE.computeIfAbsent(textureId, GuiTexture::new);
   }

   public static void prewarm(Collection<Identifier> ids) {
      Thread thread = new Thread(() -> {
         for (Identifier id : ids) {
            try {
               GuiTexture texture = load(id);
               if (texture.svg) {
                  texture.document();
               }
            } catch (Throwable var4) {
               LOGGER.warn("Failed to prewarm GUI texture {}", id, var4);
            }
         }
      }, "Ryzen SVG Prewarm");
      thread.setDaemon(true);
      thread.start();
   }

   public TextureSetup textureSetup(float deviceWidth, float deviceHeight) {
      if (!this.svg) {
         return this.staticSetup();
      } else {
         long key = packSize(quantize(deviceWidth), quantize(deviceHeight));
         TextureSetup cached = this.svgVariants.get(key);
         if (cached != null) {
            return cached;
         } else {
            TextureSetup created = this.createSvgSetup(key);
            this.svgVariants.put(Long.valueOf(key), created);
            return created;
         }
      }
   }

   public TextureSetup textureSetup() {
      if (!this.svg) {
         return this.staticSetup();
      } else {
         FloatSize size = this.documentSize();
         return this.textureSetup((float)size.getWidth(), (float)size.getHeight());
      }
   }

   private TextureSetup staticSetup() {
      if (this.staticSetup == null) {
         NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> this.textureId.toString(), readPng(this.textureId));
         texture.upload();
         MinecraftContext.mc.getTextureManager().registerTexture(this.textureId, texture);
         this.staticSetup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR, false));
      }

      return this.staticSetup;
   }

   private TextureSetup createSvgSetup(long key) {
      int width = Math.min(2048, unpackWidth(key) * 2);
      int height = Math.min(2048, unpackHeight(key) * 2);
      int oversample = oversample(width, height);
      Identifier variantId = variantId(this.textureId, key);
      NativeImageBackedTexture texture = new NativeImageBackedTexture(
         variantId::toString, rasterizeSvg(this.textureId, this.document(), width, height, oversample)
      );
      texture.upload();
      MinecraftContext.mc.getTextureManager().registerTexture(variantId, texture);
      return TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR, false));
   }

   private static int oversample(int width, int height) {
      if (width <= 256 && height <= 256) {
         int factor = 3;

         while (factor > 1 && (long)Math.max(width, height) * (long)factor > 2048L) {
            factor--;
         }

         return factor;
      } else {
         return 1;
      }
   }

   private SVGDocument document() {
      SVGDocument loaded = this.document;
      if (loaded == null) {
         loaded = loadDocument(this.textureId);
         this.document = loaded;
      }

      return loaded;
   }

   private FloatSize documentSize() {
      return this.document().size();
   }

   private static int quantize(float devicePx) {
      return Math.max(1, (int)Math.ceil((double)devicePx));
   }

   private static long packSize(int width, int height) {
      return (long)width << 32 | (long)height & 4294967295L;
   }

   private static int unpackWidth(long key) {
      return (int)(key >>> 32);
   }

   private static int unpackHeight(long key) {
      return (int)key;
   }

   private static Identifier variantId(Identifier base, long key) {
      return base.withPath(path -> path + "/" + unpackWidth(key) + "x" + unpackHeight(key));
   }

   private static NativeImage readPng(Identifier textureId) {
      try {
         NativeImage var2;
         try (InputStream inputStream = MinecraftContext.mc.getResourceManager().open(textureId)) {
            var2 = NativeImage.read(inputStream);
         }

         return var2;
      } catch (IOException var6) {
         throw new IllegalStateException("Failed to read GUI texture: " + textureId, var6);
      }
   }

   private static SVGDocument loadDocument(Identifier textureId) {
      try {
         SVGDocument var4;
         try (InputStream inputStream = MinecraftContext.mc.getResourceManager().open(textureId)) {
            URI documentUri = URI.create("resource://" + textureId.getNamespace() + "/" + textureId.getPath());
            SVGDocument document = new SVGLoader().load(inputStream, documentUri, LoaderContext.createDefault());
            if (document == null) {
               throw new IOException("Failed to parse SVG document: " + textureId);
            }

            var4 = document;
         }

         return var4;
      } catch (IOException var7) {
         throw new IllegalStateException("Failed to read GUI texture: " + textureId, var7);
      }
   }

   private static NativeImage rasterizeSvg(Identifier textureId, SVGDocument document, int width, int height, int oversample) {
      int rasterWidth = width * oversample;
      int rasterHeight = height * oversample;
      BufferedImage bufferedImage = new BufferedImage(rasterWidth, rasterHeight, 3);
      Graphics2D graphics = bufferedImage.createGraphics();

      try {
         graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
         graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
         document.render(null, graphics, new ViewBox(0.0F, 0.0F, (float)rasterWidth, (float)rasterHeight));
      } finally {
         graphics.dispose();
      }

      int[] premultiplied = ((DataBufferInt)bufferedImage.getRaster().getDataBuffer()).getData();
      int clearBorder = textureId.getPath().contains("logo_boot") ? Math.max(1, Math.round(4.0F * (float)width / 512.0F)) : 0;
      NativeImage nativeImage = new NativeImage(Format.RGBA, width, height, false);

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            if (clearBorder <= 0 || x >= clearBorder && x < width - clearBorder && y >= clearBorder && y < height - clearBorder) {
               nativeImage.setColorArgb(x, y, boxFilter(premultiplied, rasterWidth, x * oversample, y * oversample, oversample));
            } else {
               nativeImage.setColorArgb(x, y, 16777215);
            }
         }
      }

      return nativeImage;
   }

   private static int boxFilter(int[] premultiplied, int stride, int startX, int startY, int size) {
      int alpha = 0;
      int red = 0;
      int green = 0;
      int blue = 0;

      for (int y = startY; y < startY + size; y++) {
         int row = y * stride;

         for (int x = startX; x < startX + size; x++) {
            int argb = premultiplied[row + x];
            alpha += argb >>> 24 & 0xFF;
            red += argb >>> 16 & 0xFF;
            green += argb >>> 8 & 0xFF;
            blue += argb & 0xFF;
         }
      }

      int samples = size * size;
      int averageAlpha = alpha / samples;
      return averageAlpha == 0
         ? 16777215
         : averageAlpha << 24
            | unpremultiply(red, samples, averageAlpha) << 16
            | unpremultiply(green, samples, averageAlpha) << 8
            | unpremultiply(blue, samples, averageAlpha);
   }

   private static int unpremultiply(int channelSum, int samples, int averageAlpha) {
      int value = Math.round((float)channelSum / (float)samples * 255.0F / (float)averageAlpha);
      return Math.min(255, Math.max(0, value));
   }
}
