package dev.engine_room.flywheel.backend.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;

@Mixin(value = GpuDevice.class, remap = false)
public interface GpuDeviceAccessor {
	@Accessor("backend")
	GpuDeviceBackend flywheel$backend();
}
