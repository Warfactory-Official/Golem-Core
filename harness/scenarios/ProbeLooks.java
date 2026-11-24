import dev.golem.harness.api.Client;
import dev.golem.harness.api.Scenario;

/**
 * The probe's looks (Golem Probe, src/probe), each shot with the probe on (a debug stick held) and off: the replaced sun
 * and the after-sky quad overhead, Core's weather north of the camera in rain (also with Improved Transparency), Jade's
 * probe gauge on a furnace, and the recipe viewers beside the inventory. Compare the pairs by eye; every shot must also show no missing or black pass.
 */
public final class ProbeLooks implements Scenario {

    @Override
    public void run(Client c) throws Exception {
        c.useFlatWorld();
        c.waitInWorld(180_000L);
        if (c.screenClass() != null) { c.key("escape"); c.waitScreen("none", 5000); }
        c.cmd("/time set noon");
        c.cmd("/weather clear");

        hold(c, "minecraft:debug_stick");
        c.look(0, -90);
        c.waitTicks(20);
        c.screenshot("sky-probe");
        hold(c, "minecraft:air");
        c.waitTicks(20);
        c.screenshot("sky-vanilla");

        c.cmd("/weather rain");
        c.look(180, 0);
        hold(c, "minecraft:debug_stick");
        c.waitTicks(60);
        c.screenshot("weather-probe");
        hold(c, "minecraft:air");
        c.waitTicks(20);
        c.screenshot("weather-vanilla");
        // Improved Transparency: order-independent transparency on 26.3, Fabulous before it.
        String transparency = c.setOption("fabulous", "true");
        hold(c, "minecraft:debug_stick");
        c.waitTicks(40);
        c.screenshot("weather-probe-transparency");
        System.out.println("[ProbeLooks] improved transparency during the shot: " + c.setOption("fabulous", transparency));
        c.cmd("/weather clear");

        c.cmd("/setblock ~ ~ ~-3 minecraft:furnace");
        c.look(180, 25);
        c.waitTicks(40);
        c.screenshot("jade-furnace");

        c.key("e");
        c.waitTicks(40);
        c.screenshot("viewer-inventory");
        c.key("escape");
        System.out.println("[ProbeLooks] PASS");
    }

    private static void hold(Client c, String item) {
        c.cmd("/item replace entity @s weapon.mainhand with " + item);
    }
}
