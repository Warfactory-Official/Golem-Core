package dev.engine_room.flywheel.backend.gl;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.engine_room.flywheel.backend.mixin.GpuDeviceAccessor;

/**
 * 26.1's {@code GlCommandEncoder} caches the last {@code GlProgram} it bound and never invalidates
 * that cache, so a draw whose pipeline matches the cache skips its {@code glUseProgram} and writes
 * its sampler uniforms against whatever program is really current. Anything that changes the program
 * outside the encoder -- Flywheel binding and unbinding its own -- silently breaks vanilla's next
 * draw. Every program change routed through {@code GlStateManager} clears the cache here.
 */
public final class VanillaProgramCache {
	private static @Nullable GpuDevice cachedDevice;
	private static @Nullable ProgramCacheOwner cachedOwner;

	private VanillaProgramCache() {
	}

	public static void invalidate() {
		ProgramCacheOwner owner = owner();
		if (owner != null) {
			owner.flywheel$invalidateProgram();
		}
	}

	private static @Nullable ProgramCacheOwner owner() {
		GpuDevice device;
		try {
			device = RenderSystem.getDevice();
		} catch (IllegalStateException e) {
			return null;
		}

		if (device == cachedDevice) {
			return cachedOwner;
		}

		cachedDevice = device;
		cachedOwner = null;

		GpuDeviceBackend backend = ((GpuDeviceAccessor) (Object) device).flywheel$backend();
		CommandEncoderBackend encoder = backend.createCommandEncoder();
		if (encoder instanceof ProgramCacheOwner owner) {
			cachedOwner = owner;
		}
		return cachedOwner;
	}
}
