package dev.golem.core.player;

import dev.golem.core.platform.Lifecycle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.IntSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Per-slot work on what a player is carrying, resolved when a slot changes rather than re-derived
 * every time it is needed.
 *
 * <p>Looping over the inventory on a timer recomputes every slot's answer on every pass although the
 * slot has not changed, and the recomputation (a tag walk, a registry lookup, a list fold) is the whole
 * cost: forty-odd slots times every player times every few ticks, on the server thread.
 *
 * <p>Here a {@link Resolver} turns a stack into whatever the consumer needs to act on it and runs
 * <b>once per change</b>, off the server thread; the result is memoised per slot. The
 * {@link Application} runs on the server thread at the consumer's cadence and only walks the memo.
 *
 * <p><b>Where the changes come from.</b> {@code AbstractContainerMenu} compares each slot against its
 * last-sent copy every tick and notifies its {@link ContainerListener}s of the ones that differ. The
 * comparison is by value, so a component or NBT edit counts, not only a swap. {@link MenuWatch} is that
 * listener; the {@code ServerPlayer} mixin installs one on every menu the player opens. Only the
 * player's own inventory slots are taken, and only the open menu broadcasts, so while a foreign menu is
 * open the player's own menu is polled here, which is the only way armour and offhand edits are seen.
 *
 * <p><b>Threading.</b> The stack handed to a listener is the menu's private copy, replaced and never
 * mutated on the next change, so it is safe to read after the tick that produced it. Resolution runs
 * on the common pool with one batch in flight at a time, and the memo is published by an array swap.
 * A resolver must therefore be pure with respect to game state: it may read the stack it is given and
 * immutable registries, and nothing else.
 *
 * <p>The memo holds the item the answer was resolved for. If the live slot does not hold that item the
 * entry is skipped, so a lagging memo can miss a tick but never applies the wrong answer to a stack.
 */
public final class InventoryScan<T> {

    /**
     * Turns a stack into the value the application acts on, or null when the stack is of no
     * interest. Runs off the server thread.
     */
    @FunctionalInterface
    public interface Resolver<T> {
        @Nullable T resolve(ItemStack stack);
    }

    /** Acts on one carried stack and its resolved value. Runs on the server thread. */
    @FunctionalInterface
    public interface Application<T> {
        void apply(Player player, ItemStack stack, T resolved);
    }

    /**
     * The menu listener every scan is fed from. Equality is the player, because
     * {@code addSlotListener} de-duplicates and a menu is re-initialised more than once per session.
     */
    public record MenuWatch(Player player) implements ContainerListener {

        @Override
        public void slotChanged(AbstractContainerMenu menu, int index, ItemStack stack) {
            offer(player, menu.getSlot(index), stack);
        }

        @Override
        public void dataChanged(AbstractContainerMenu menu, int id, int value) {}
    }

    private record Resolved<T>(Item item, T value) {}

    private record Delta<T>(UUID player, int slot, ItemStack stack, Memo<T> memo) {}

    /**
     * Writes are locked and reads are not: a write is one slot edit away from another and costs an
     * array copy either way, while the read is the hazard tick's own loop. What the lock buys is that
     * two of the rare writers (the resolve batch and a caller asking for an immediate pass) cannot
     * lose each other's slot.
     */
    private static final class Memo<T> {

        private static final Resolved<?>[] NONE = new Resolved<?>[0];

        @SuppressWarnings("unchecked")
        private volatile Resolved<T>[] slots = (Resolved<T>[]) NONE;

        synchronized void set(int slot, Resolved<T> resolved) {
            Resolved<T>[] current = slots;
            Resolved<T>[] next = slot < current.length ? current.clone() : Arrays.copyOf(current, slot + 1);
            next[slot] = resolved;
            slots = next;
        }

        synchronized void clear(int slot) {
            Resolved<T>[] current = slots;
            if (slot >= current.length || current[slot] == null) return;
            Resolved<T>[] next = current.clone();
            next[slot] = null;
            slots = next;
        }
    }

    private static final List<InventoryScan<?>> SCANS = new CopyOnWriteArrayList<>();
    private static final Executor RESOLVER = ForkJoinPool.commonPool();
    private static final int OFFLINE_SWEEP_PERIOD = 100;

    private static boolean driving;
    private static @Nullable MinecraftServer running;

    private final Resolver<T> resolver;
    private final Application<T> application;
    private final ConcurrentHashMap<UUID, Memo<T>> memos = new ConcurrentHashMap<>();
    private final Queue<Delta<T>> pending = new ConcurrentLinkedQueue<>();

    private volatile CompletableFuture<Void> resolving = CompletableFuture.completedFuture(null);
    private volatile boolean stale;
    private boolean installed;
    private IntSupplier period = () -> 1;

    private InventoryScan(Resolver<T> resolver, Application<T> application) {
        this.resolver = resolver;
        this.application = application;
    }

    /** A scan over every player's carried items. Nothing reaches it until {@link #install()}. */
    public static <T> InventoryScan<T> of(Resolver<T> resolver, Application<T> application) {
        return new InventoryScan<>(resolver, application);
    }

    /**
     * How often the application runs, in ticks, read fresh each tick so a configured rate can
     * change at runtime. Players are spread across the period by entity id rather than all landing
     * on the same tick of it.
     */
    public InventoryScan<T> every(IntSupplier ticks) {
        this.period = ticks;
        return this;
    }

    /** Starts the scan: slot changes reach it, and the server tick resolves and applies them. */
    public void install() {
        if (installed) return;
        installed = true;
        SCANS.add(this);
        if (driving) return;
        driving = true;
        Lifecycle.onServerTick(InventoryScan::tick);
        Lifecycle.onServerStopping(server -> {
            running = null;
            for (InventoryScan<?> scan : SCANS) scan.forgetAll();
        });
    }

    /** Stops it again and drops everything it holds. A consumer that installs once never needs this. */
    public void forget() {
        installed = false;
        SCANS.remove(this);
        forgetAll();
    }

    /**
     * Declares every memoised answer out of date: what a consumer calls when the data its resolver
     * reads has changed. The re-resolve is queued rather than done here: a batch may be in flight,
     * and replacing the memo underneath it would leave whatever that batch writes unreachable.
     */
    public void invalidate() {
        if (running == null && resolving.isDone()) {
            forgetAll();
            return;
        }
        stale = true;
    }

    /**
     * Resolves this player's whole inventory on the calling thread and applies it once, ignoring
     * both the cadence and whatever is queued. For a caller that has just changed an inventory
     * wholesale and cannot wait for the next tick to see it.
     */
    public void scanNow(Player player) {
        Memo<T> memo = memos.computeIfAbsent(player.getUUID(), id -> new Memo<>());
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            store(memo, slot, inventory.getItem(slot));
        }
        applyNow(player);
    }

    /** Resolves everything queued, on the calling thread; answers how many slots were re-resolved. */
    public int drainNow() {
        List<Delta<T>> batch = drain();
        resolve(batch);
        return batch.size();
    }

    /** Runs the application over this player's memo once, outside the cadence. */
    public void applyNow(Player player) {
        Memo<T> memo = memos.get(player.getUUID());
        if (memo == null) return;
        Resolved<T>[] slots = memo.slots;
        if (slots.length == 0) return;
        Inventory inventory = player.getInventory();
        int size = Math.min(slots.length, inventory.getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            Resolved<T> resolved = slots[slot];
            if (resolved == null) continue;
            ItemStack live = inventory.getItem(slot);
            if (live.isEmpty() || live.getItem() != resolved.item()) continue;
            application.apply(player, live, resolved.value());
        }
    }

    private static void offer(Player player, Slot slot, ItemStack stack) {
        if (slot.container != player.getInventory()) return;
        int index = slot.getContainerSlot();
        if (index < 0) return;
        for (InventoryScan<?> scan : SCANS) scan.enqueue(player, index, stack);
    }

    private void enqueue(Player player, int slot, ItemStack stack) {
        UUID id = player.getUUID();
        Memo<T> memo = memos.get(id);
        if (memo == null) memo = memos.computeIfAbsent(id, ignored -> new Memo<>());
        pending.add(new Delta<>(id, slot, stack, memo));
    }

    private static void tick(MinecraftServer server) {
        running = server;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean sweep = server.getTickCount() % OFFLINE_SWEEP_PERIOD == 0;
        for (InventoryScan<?> scan : SCANS) {
            if (sweep || scan.memos.size() > players.size()) scan.forgetOffline(server);
        }
        for (ServerPlayer player : players) {
            if (player.isRemoved()) continue;
            boolean polled = false;
            for (InventoryScan<?> scan : SCANS) {
                if (!scan.due(player)) continue;
                if (!polled) {
                    polled = true;
                    pollOwnMenu(player);
                }
                scan.applyNow(player);
            }
        }
        for (InventoryScan<?> scan : SCANS) scan.dispatch(server);
    }

    /**
     * Only the open menu broadcasts, and no menu but the player's own carries armour and offhand.
     * A player standing at a chest would never report a boot swap. Suppressed remote updates keep
     * this from sending the client a second copy of a menu it is not looking at.
     */
    private static void pollOwnMenu(ServerPlayer player) {
        if (player.containerMenu == player.inventoryMenu) return;
        player.inventoryMenu.suppressRemoteUpdates();
        try {
            player.inventoryMenu.broadcastChanges();
        } finally {
            player.inventoryMenu.resumeRemoteUpdates();
        }
    }

    private boolean due(Player player) {
        int every = period.getAsInt();
        return every <= 1 || Math.floorMod(player.level().getGameTime() + player.getId(), (long) every) == 0L;
    }

    private void dispatch(MinecraftServer server) {
        if (!resolving.isDone()) return;
        if (stale) {
            stale = false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) requeue(player);
        }
        if (pending.isEmpty()) return;
        List<Delta<T>> batch = drain();
        resolving = CompletableFuture.runAsync(() -> resolve(batch), RESOLVER);
    }

    /** Empty slots included, so their memo entries drop; the stacks are live and so are copied. */
    private void requeue(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            enqueue(player, slot, inventory.getItem(slot).copy());
        }
    }

    private List<Delta<T>> drain() {
        List<Delta<T>> batch = new ArrayList<>();
        Delta<T> delta;
        while ((delta = pending.poll()) != null) batch.add(delta);
        return batch;
    }

    private void resolve(List<Delta<T>> batch) {
        for (Delta<T> delta : batch) {
            Memo<T> memo = memos.get(delta.player());
            if (memo == null || memo != delta.memo()) continue;
            store(memo, delta.slot(), delta.stack());
        }
    }

    private void store(Memo<T> memo, int slot, ItemStack stack) {
        T value = stack.isEmpty() ? null : resolver.resolve(stack);
        if (value == null) {
            memo.clear(slot);
        } else {
            memo.set(slot, new Resolved<>(stack.getItem(), value));
        }
    }

    /**
     * A leave offset by a join in the same tick leaves the counts equal, so the sweep cannot rest on
     * them alone; a retained entry would pin the departed player's memo for the life of the server.
     */
    private void forgetOffline(MinecraftServer server) {
        memos.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private void forgetAll() {
        memos.clear();
        pending.clear();
        stale = false;
    }
}
