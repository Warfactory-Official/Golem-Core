package dev.golem.core.ae2;

//? if ae2 {
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import dev.golem.core.storage.ItemStore;
import java.util.function.ObjLongConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

// Both directions between ItemStore and AE2's MEStorage; the second only serves ItemStores.find.
final class StoreAdapter implements MEStorage {

    private final ItemStore store;

    private StoreAdapter(ItemStore store) {
        this.store = store;
    }

    static @Nullable MEStorage of(@Nullable ItemStore store) {
        return store == null ? null : new StoreAdapter(store);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return what instanceof AEItemKey item && store.prefers(item.toStack());
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return what instanceof AEItemKey item ? store.insert(item.toStack(), amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return what instanceof AEItemKey item ? store.extract(item.toStack(), amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        store.list((type, count) -> {
            AEItemKey key = AEItemKey.of(type);
            if (key != null && count > 0) out.add(key, count);
        });
    }

    @Override
    public Component getDescription() {
        return store.name();
    }

    static @Nullable ItemStore view(@Nullable MEStorage storage) {
        return storage == null ? null : new ItemStore() {
            @Override
            public long insert(ItemStack type, long amount, boolean simulate) {
                return storage.insert(AEItemKey.of(type), amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                        IActionSource.empty());
            }

            @Override
            public long extract(ItemStack type, long amount, boolean simulate) {
                return storage.extract(AEItemKey.of(type), amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                        IActionSource.empty());
            }

            @Override
            public void list(ObjLongConsumer<ItemStack> out) {
                for (var entry : storage.getAvailableStacks()) {
                    if (entry.getKey() instanceof AEItemKey item) out.accept(item.toStack(), entry.getLongValue());
                }
            }

            @Override
            public Component name() {
                return storage.getDescription();
            }

            @Override
            public boolean prefers(ItemStack type) {
                return storage.isPreferredStorageFor(AEItemKey.of(type), IActionSource.empty());
            }
        };
    }
}
//?}
