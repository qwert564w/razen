package org.ryzen.utils.render;

import com.mojang.blaze3d.shaders.ShaderType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class ShaderFallback {
   private static final Pattern MOJ_IMPORT = Pattern.compile("^\\s*#moj_import\\s+<(\\w+):([\\w./]+)>\\s*$", 8);
   private static final int MAX_DEPTH = 8;
   private static final String BUILTIN_DYNAMIC_TRANSFORMS = "layout(std140) uniform DynamicTransforms {\n    mat4 ModelViewMat;\n    vec4 ColorModulator;\n    vec3 ModelOffset;\n    mat4 TextureMat;\n};\n";
   private static final String BUILTIN_PROJECTION = "layout(std140) uniform Projection {\n    mat4 ProjMat;\n};\n";

   private ShaderFallback() {
   }

   public static String load(Identifier shaderId, ShaderType shaderType) {
      if (!shaderId.getNamespace().equals("ryzen")) {
         return null;
      } else {
         String extension = shaderType == ShaderType.VERTEX ? ".vsh" : ".fsh";
         String source = readClasspath("ryzen", shaderId.getPath() + extension);
         return source != null ? resolveImports(source, 0) : null;
      }
   }

   private static String resolveImports(String source, int depth) {
      if (depth >= 8) {
         return source;
      } else {
         Matcher matcher = MOJ_IMPORT.matcher(source);
         StringBuilder result = new StringBuilder(source.length());

         while (matcher.find()) {
            String imported = readInclude(matcher.group(1), matcher.group(2));
            if (imported == null) {
               imported = "";
            }

            imported = stripVersionDirectives(resolveImports(imported, depth + 1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(imported));
         }

         matcher.appendTail(result);
         return result.toString();
      }
   }

   private static String readInclude(String namespace, String path) {
      String fromClasspath = readClasspath(namespace, "include/" + path);
      if (fromClasspath != null) {
         return fromClasspath;
      } else {
         if (namespace.equals("minecraft")) {
            if (path.equals("dynamictransforms.glsl")) {
               return "layout(std140) uniform DynamicTransforms {\n    mat4 ModelViewMat;\n    vec4 ColorModulator;\n    vec3 ModelOffset;\n    mat4 TextureMat;\n};\n";
            }

            if (path.equals("projection.glsl")) {
               return "layout(std140) uniform Projection {\n    mat4 ProjMat;\n};\n";
            }
         }

         return null;
      }
   }

   private static String stripVersionDirectives(String source) {
      StringBuilder builder = new StringBuilder(source.length());

      for (String line : source.split("\n", -1)) {
         if (!line.stripLeading().startsWith("#version")) {
            builder.append(line).append('\n');
         }
      }

      return builder.toString();
   }

   private static String readClasspath(String namespace, String path) {
      String resourcePath = "/assets/" + namespace + "/shaders/" + path;

      try {
         String var4;
         try (InputStream stream = ShaderFallback.class.getResourceAsStream(resourcePath)) {
            var4 = stream != null ? new String(stream.readAllBytes(), StandardCharsets.UTF_8) : null;
         }

         return var4;
      } catch (Throwable var8) {
         return null;
      }
   }
}
