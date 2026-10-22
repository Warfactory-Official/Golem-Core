package dev.engine_room.flywheel.backend.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.blaze3d.opengl.GlProgram;

import dev.engine_room.flywheel.backend.gl.ProgramCacheOwner;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder", remap = false)
abstract class GlCommandEncoderMixin implements ProgramCacheOwner {
	@Shadow
	private @Nullable GlProgram lastProgram;

	@Override
	public void flywheel$invalidateProgram() {
		this.lastProgram = null;
	}
}
