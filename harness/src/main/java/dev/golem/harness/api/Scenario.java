package dev.golem.harness.api;

/**
 * A scenario is one {@code .java} file with a public class implementing this interface. The runner
 * compiles it in memory and calls {@link #run} once per live client, in parallel.
 *
 * <pre>{@code
 * public class BarrelVsChest implements Scenario {
 *     public void run(Client c) throws Exception {
 *         Fixtures.platform(c, 0, 100, 0, 6);
 *         ...
 *         Expect.screenOpen(c, "Barrel");
 *         c.screenshot("barrel-open");
 *     }
 * }
 * }</pre>
 */
public interface Scenario {

    void run(Client client) throws Exception;
}
