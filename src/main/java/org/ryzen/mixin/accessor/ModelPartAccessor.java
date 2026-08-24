package org.ryzen.mixin.accessor;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPart.Cuboid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({ModelPart.class})
public interface ModelPartAccessor {
   @Accessor("cuboids")
   List<Cuboid> blade$getCubes();
}
