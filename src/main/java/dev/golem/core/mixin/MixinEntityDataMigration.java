package dev.golem.core.mixin;

import dev.golem.core.data.EntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
import dev.golem.core.data.ValueDataIn;
import net.minecraft.world.level.storage.ValueInput;
//?} else {
/*import dev.golem.core.data.TagDataIn;
import net.minecraft.nbt.CompoundTag;
*///?}

/**
 * Hands an entity's finished save data to {@link EntityData#migrateLegacy}, so a mod's pre-attachment
 * per-entity format can be read once ({@link EntityData#migrateFrom}).
 *
 * <p>Why a mixin (AGENTS.md §7 ladder): (1) vanilla has no load hook that exposes an entity's save data;
 * (2) the loader APIs hand an attachment or capability only its own sub-compound — NeoForge's
 * {@code IAttachmentSerializer}, Fabric's persistence codec, Forge's {@code INBTSerializable} — and no loader
 * posts an event carrying the entity's save data at load; (3) visibility is not the obstacle, so an access
 * widener cannot help. One {@code @Inject} at the tail of {@code Entity.load}, which runs after every loader
 * has read its own attachments/capabilities, so "no stored value yet" can be decided there. It does no work
 * unless some declaration named a legacy key.
 */
@Mixin(Entity.class)
public abstract class MixinEntityDataMigration {

    @Inject(method = "load", at = @At("TAIL"))
    //? if >=26.1 {
    private void golem$migrateLegacyData(ValueInput input, CallbackInfo ci) {
        if (EntityData.migrating()) EntityData.migrateLegacy((Entity) (Object) this, new ValueDataIn(input));
    }
    //?} else {
    /*private void golem$migrateLegacyData(CompoundTag tag, CallbackInfo ci) {
        if (!EntityData.migrating()) return;
        Entity self = (Entity) (Object) this;
        EntityData.migrateLegacy(self, new TagDataIn(tag, self.level().registryAccess()));
    }
    *///?}
}
