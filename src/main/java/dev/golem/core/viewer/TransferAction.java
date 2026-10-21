package dev.golem.core.viewer;

import dev.golem.core.client.ClientTicks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A viewer's transfer button that acts on the open screen instead of moving items (selecting a recipe in a
 * crafting browser, say). The viewer closes its recipe screen first, then {@link #run} gets the container screen.
 */
public interface TransferAction<C extends AbstractContainerMenu, R> {

    /** Why the button is refused for this menu and recipe, shown on the button; {@code null} when it is available. */
    default Component unavailable(C menu, R recipe) {
        return null;
    }

    void run(AbstractContainerScreen<?> screen, C menu, R recipe);

    /** Runs the action on the container screen once the viewer has handed it back, if it shows {@code menu} within a second. */
    static <C extends AbstractContainerMenu, R> void runWhenBack(C menu, R recipe, TransferAction<C, R> action) {
        Pending.add(() -> {
            if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)
                    || screen.getMenu() != menu) {
                return false;
            }
            action.run(screen, menu, recipe);
            return true;
        });
    }

    /** Actions waiting for the viewer's screen to close, tried at the end of each client tick. */
    final class Pending {
        private static final int GIVE_UP_TICKS = 20;
        private static final List<Entry> QUEUE = new ArrayList<>();
        private static boolean registered;

        private record Entry(BooleanSupplier attempt, int[] ticks) {}

        private Pending() {}

        static synchronized void add(BooleanSupplier attempt) {
            if (!registered) {
                registered = true;
                ClientTicks.onEndTick(Pending::tick);
            }
            QUEUE.add(new Entry(attempt, new int[1]));
        }

        private static synchronized void tick() {
            QUEUE.removeIf(entry -> entry.attempt().getAsBoolean() || ++entry.ticks()[0] >= GIVE_UP_TICKS);
        }
    }
}
