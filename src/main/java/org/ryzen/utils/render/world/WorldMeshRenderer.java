package org.ryzen.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.ryzen.utils.render.Render3DUtil;

@Environment(EnvType.CLIENT)
public final class WorldMeshRenderer {
   private static final float LINE_HALF_WIDTH = 0.012F;
   private static final RenderPipeline THROUGH_WALLS = buildPipeline("line", false);
   private static final RenderPipeline DEPTH_TESTED = buildPipeline("line_depth", true);
   private static GpuBuffer vertexBuffer;

   private WorldMeshRenderer() {
   }

   private static RenderPipeline buildPipeline(String name, boolean depthTest) {
      return RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.of("ryzen:pipeline/world/" + name))
         .withVertexShader(Identifier.of("ryzen:core/blade_line"))
         .withFragmentShader(Identifier.of("ryzen:core/blade_line"))
         .withUniform("Projection", UniformType.UNIFORM_BUFFER)
         .withBlend(BlendFunction.LIGHTNING)
         .withDepthTestFunction(depthTest ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
         .withCull(false)
         .build();
   }

   public static void render(WorldMeshRenderer.WorldMesh mesh) {
      render(mesh, true);
   }

   public static void render(WorldMeshRenderer.WorldMesh mesh, boolean throughWalls) {
      if (mesh != null && !mesh.isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null && mc.gameRenderer != null) {
            Framebuffer target = mc.getFramebuffer();
            GpuTextureView colorView = target != null ? target.getColorAttachmentView() : null;
            if (colorView != null) {
               WorldMeshRenderer.BuiltMesh built = buildMesh(mc, mesh);
               if (built != null) {
                  GpuDevice device = RenderSystem.getDevice();

                  try {
                     ByteBuffer vertexData = built.meshData().getBuffer();
                     int byteSize = vertexData.remaining();
                     ensureVertexCapacity(byteSize);
                     device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(0L, (long)byteSize), vertexData);
                     GpuTextureView depthView = target.getDepthAttachmentView();
                     RenderPass pass = depthView != null
                        ? device.createCommandEncoder()
                           .createRenderPass(() -> "Ryzen World Mesh Pass", colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())
                        : device.createCommandEncoder().createRenderPass(() -> "Ryzen World Mesh Pass", colorView, OptionalInt.empty());

                     try {
                        pass.setPipeline(throughWalls ? THROUGH_WALLS : DEPTH_TESTED);
                        pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                        pass.setVertexBuffer(0, vertexBuffer);
                        pass.draw(0, built.vertexCount());
                     } finally {
                        pass.close();
                     }
                  } finally {
                     built.meshData().close();
                  }
               }
            }
         }
      }
   }

   private static void ensureVertexCapacity(int byteSize) {
      if (vertexBuffer == null || vertexBuffer.size() < (long)byteSize) {
         if (vertexBuffer != null) {
            vertexBuffer.close();
         }

         int capacity = Math.max(byteSize + byteSize / 2, 16384);
         vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Ryzen World Mesh Vertices", 40, (long)capacity);
      }
   }

   private static WorldMeshRenderer.BuiltMesh buildMesh(MinecraftClient mc, WorldMeshRenderer.WorldMesh mesh) {
      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cameraPos = camera.getCameraPos();
      Matrix4f pose = Render3DUtil.cameraViewPose(camera);
      int estimatedVertices = mesh.lines().size() * 6
         + mesh.rings().stream().mapToInt(WorldMeshRenderer.Ring::segments).sum() * 6
         + mesh.planeRects().size() * 6
         + mesh.tris().size() * 3;
      if (estimatedVertices <= 0) {
         return null;
      } else {
         int bytes = estimatedVertices * VertexFormats.POSITION_COLOR.getVertexSize();
         BufferBuilder builder = new BufferBuilder(new BufferAllocator(Math.max(bytes, 256)), DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
         int vertexCount = 0;

         for (WorldMeshRenderer.Line line : mesh.lines()) {
            addThickLine(
               builder,
               Render3DUtil.toViewSpace(line.start(), cameraPos, pose),
               Render3DUtil.toViewSpace(line.end(), cameraPos, pose),
               line.startColor(),
               line.endColor()
            );
            vertexCount += 6;
         }

         for (WorldMeshRenderer.Ring ring : mesh.rings()) {
            addRing(builder, ring, cameraPos, pose);
            vertexCount += ring.segments() * 6;
         }

         for (WorldMeshRenderer.PlaneRect rect : mesh.planeRects()) {
            addPlaneRect(builder, rect, cameraPos, pose);
            vertexCount += 6;
         }

         for (WorldMeshRenderer.Tri tri : mesh.tris()) {
            addTri(builder, tri, cameraPos, pose);
            vertexCount += 3;
         }

         if (vertexCount == 0) {
            return null;
         } else {
            BuiltBuffer meshData = builder.end();
            return new WorldMeshRenderer.BuiltMesh(meshData, vertexCount);
         }
      }
   }

   private static void addThickLine(BufferBuilder builder, Vector4f start, Vector4f end, int startColor, int endColor) {
      float dx = end.x - start.x;
      float dy = end.y - start.y;
      float length = (float)Math.sqrt((double)(dx * dx + dy * dy));
      float normalX;
      float normalY;
      if (length > 0.05F) {
         normalX = -dy / length * 0.012F;
         normalY = dx / length * 0.012F;
      } else {
         normalX = 0.012F;
         normalY = 0.0F;
      }

      float sx1 = start.x + normalX;
      float sy1 = start.y + normalY;
      float sx2 = start.x - normalX;
      float sy2 = start.y - normalY;
      float ex1 = end.x + normalX;
      float ey1 = end.y + normalY;
      float ex2 = end.x - normalX;
      float ey2 = end.y - normalY;
      builder.vertex(sx1, sy1, start.z).color(startColor);
      builder.vertex(sx2, sy2, start.z).color(startColor);
      builder.vertex(ex2, ey2, end.z).color(endColor);
      builder.vertex(sx1, sy1, start.z).color(startColor);
      builder.vertex(ex2, ey2, end.z).color(endColor);
      builder.vertex(ex1, ey1, end.z).color(endColor);
   }

   private static void addRing(BufferBuilder builder, WorldMeshRenderer.Ring ring, Vec3d cameraPos, Matrix4f pose) {
      double innerRadius = Math.max(0.0, ring.radius() - ring.halfWidth());
      double outerRadius = ring.radius() + ring.halfWidth();

      for (int i = 0; i < ring.segments(); i++) {
         double angle1 = (double)i * (Math.PI * 2) / (double)ring.segments();
         double angle2 = (double)(i + 1) * (Math.PI * 2) / (double)ring.segments();
         Vec3d outer1 = ring.center().add(ring.u().multiply(Math.cos(angle1) * outerRadius)).add(ring.v().multiply(Math.sin(angle1) * outerRadius));
         Vec3d inner1 = ring.center().add(ring.u().multiply(Math.cos(angle1) * innerRadius)).add(ring.v().multiply(Math.sin(angle1) * innerRadius));
         Vec3d outer2 = ring.center().add(ring.u().multiply(Math.cos(angle2) * outerRadius)).add(ring.v().multiply(Math.sin(angle2) * outerRadius));
         Vec3d inner2 = ring.center().add(ring.u().multiply(Math.cos(angle2) * innerRadius)).add(ring.v().multiply(Math.sin(angle2) * innerRadius));
         Vector4f outerView1 = Render3DUtil.toViewSpace(outer1, cameraPos, pose);
         Vector4f innerView1 = Render3DUtil.toViewSpace(inner1, cameraPos, pose);
         Vector4f outerView2 = Render3DUtil.toViewSpace(outer2, cameraPos, pose);
         Vector4f innerView2 = Render3DUtil.toViewSpace(inner2, cameraPos, pose);
         builder.vertex(outerView1.x, outerView1.y, outerView1.z).color(ring.color());
         builder.vertex(innerView1.x, innerView1.y, innerView1.z).color(ring.color());
         builder.vertex(innerView2.x, innerView2.y, innerView2.z).color(ring.color());
         builder.vertex(outerView1.x, outerView1.y, outerView1.z).color(ring.color());
         builder.vertex(innerView2.x, innerView2.y, innerView2.z).color(ring.color());
         builder.vertex(outerView2.x, outerView2.y, outerView2.z).color(ring.color());
      }
   }

   private static void addPlaneRect(BufferBuilder builder, WorldMeshRenderer.PlaneRect rect, Vec3d cameraPos, Matrix4f pose) {
      Vec3d p1 = rect.center().add(rect.axis().multiply(rect.halfLength())).add(rect.normal().multiply(rect.halfWidth()));
      Vec3d p2 = rect.center().add(rect.axis().multiply(rect.halfLength())).add(rect.normal().multiply(-rect.halfWidth()));
      Vec3d p3 = rect.center().add(rect.axis().multiply(-rect.halfLength())).add(rect.normal().multiply(-rect.halfWidth()));
      Vec3d p4 = rect.center().add(rect.axis().multiply(-rect.halfLength())).add(rect.normal().multiply(rect.halfWidth()));
      Vector4f v1 = Render3DUtil.toViewSpace(p1, cameraPos, pose);
      Vector4f v2 = Render3DUtil.toViewSpace(p2, cameraPos, pose);
      Vector4f v3 = Render3DUtil.toViewSpace(p3, cameraPos, pose);
      Vector4f v4 = Render3DUtil.toViewSpace(p4, cameraPos, pose);
      builder.vertex(v1.x, v1.y, v1.z).color(rect.color());
      builder.vertex(v2.x, v2.y, v2.z).color(rect.color());
      builder.vertex(v3.x, v3.y, v3.z).color(rect.color());
      builder.vertex(v1.x, v1.y, v1.z).color(rect.color());
      builder.vertex(v3.x, v3.y, v3.z).color(rect.color());
      builder.vertex(v4.x, v4.y, v4.z).color(rect.color());
   }

   private static void addTri(BufferBuilder builder, WorldMeshRenderer.Tri tri, Vec3d cameraPos, Matrix4f pose) {
      Vector4f a = Render3DUtil.toViewSpace(tri.a(), cameraPos, pose);
      Vector4f b = Render3DUtil.toViewSpace(tri.b(), cameraPos, pose);
      Vector4f c = Render3DUtil.toViewSpace(tri.c(), cameraPos, pose);
      builder.vertex(a.x, a.y, a.z).color(tri.colorA());
      builder.vertex(b.x, b.y, b.z).color(tri.colorB());
      builder.vertex(c.x, c.y, c.z).color(tri.colorC());
   }

   @Environment(EnvType.CLIENT)
   private static record BuiltMesh(BuiltBuffer meshData, int vertexCount) {
   }

   @Environment(EnvType.CLIENT)
   public static record Line(Vec3d start, Vec3d end, int startColor, int endColor) {
      public Line(Vec3d start, Vec3d end, int color) {
         this(start, end, color, color);
      }
   }

   @Environment(EnvType.CLIENT)
   public static record PlaneRect(Vec3d center, Vec3d axis, Vec3d normal, double halfLength, double halfWidth, int color) {
   }

   @Environment(EnvType.CLIENT)
   public static record Ring(Vec3d center, Vec3d u, Vec3d v, double radius, double halfWidth, int color, int segments) {
   }

   @Environment(EnvType.CLIENT)
   public static record Tri(Vec3d a, Vec3d b, Vec3d c, int colorA, int colorB, int colorC) {
      public Tri(Vec3d a, Vec3d b, Vec3d c, int color) {
         this(a, b, c, color, color, color);
      }
   }

   @Environment(EnvType.CLIENT)
   public static record WorldMesh(
      List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.Ring> rings, List<WorldMeshRenderer.PlaneRect> planeRects, List<WorldMeshRenderer.Tri> tris
   ) {
      public WorldMesh(List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.Ring> rings, List<WorldMeshRenderer.PlaneRect> planeRects) {
         this(lines, rings, planeRects, List.of());
      }

      public boolean isEmpty() {
         return this.lines.isEmpty() && this.rings.isEmpty() && this.planeRects.isEmpty() && this.tris.isEmpty();
      }
   }
}
