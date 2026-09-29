package dev.golem.harness.cli;

import dev.golem.harness.api.Client;
import dev.golem.harness.api.HarnessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/** Save snapshots and the structure-template datapack. */
public final class World {

    private World() {}

    public static Path save(Nodes.Node node, String world) {
        return Paths.runDir(node).resolve("saves").resolve(world);
    }

    public static Path snapshotDir(Nodes.Node node, String world) {
        return Paths.runDir(node).resolve("saves").resolve(".snapshots").resolve(world);
    }

    public static boolean exists(Nodes.Node node, String world) {
        return Files.isDirectory(save(node, world).resolve("level.dat").getParent())
                && Files.exists(save(node, world).resolve("level.dat"));
    }

    /** Copies the save to the snapshot folder (the client must be out of the world or stopped). */
    public static void snapshot(Nodes.Node node, String world) throws IOException {
        Path from = save(node, world);
        if (!Files.exists(from.resolve("level.dat"))) throw new HarnessException("no save '" + world + "' for " + node.name());
        Path to = snapshotDir(node, world);
        deleteTree(to);
        copyTree(from, to);
        System.out.println("[harness] " + node.name() + ": snapshot of '" + world + "' stored");
    }

    /** Restores the snapshot over the save (the client must be out of the world or stopped). */
    public static void restore(Nodes.Node node, String world) throws IOException {
        Path from = snapshotDir(node, world);
        if (!Files.exists(from.resolve("level.dat"))) throw new HarnessException("no snapshot of '" + world + "' for " + node.name() + "; run `harness world snapshot` first");
        Path to = save(node, world);
        deleteTree(to);
        copyTree(from, to);
        syncFixtures(node, world);
    }

    /** Live reset: leave the world, restore the snapshot, rejoin. No JVM restart. */
    public static void reset(Nodes.Node node, Client client, String world) throws IOException {
        client.leaveWorld(60_000L);
        restore(node, world);
        client.joinWorld(world, 180_000L);
        System.out.println("[harness] " + node.name() + ": world '" + world + "' restored and rejoined");
    }

    /** Publishes {@code harness/fixtures/*.nbt} as {@code harness:<name>} templates inside the save. */
    public static void syncFixtures(Nodes.Node node, String world) throws IOException {
        Path saveDir = save(node, world);
        if (!Files.isDirectory(saveDir) || !Files.isDirectory(Paths.fixtures())) return;
        Path pack = saveDir.resolve("datapacks").resolve("harness");
        Path structures = pack.resolve("data").resolve("harness").resolve(node.structureFolder());
        Files.createDirectories(structures);
        // Since format 82, pack_format alone is rejected; older readers ignore these bounds.
        String mcmeta = "{\n  \"pack\": {\n    \"pack_format\": " + node.packFormat()
                + ",\n    \"min_format\": " + node.packFormat()
                + ",\n    \"max_format\": " + node.packFormat()
                + ",\n    \"description\": \"Harness structure templates\"\n  }\n}\n";
        Files.writeString(pack.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8);
        try (Stream<Path> files = Files.list(Paths.fixtures())) {
            for (Path nbt : files.toList()) {
                if (!nbt.getFileName().toString().endsWith(".nbt")) continue;
                Files.copy(nbt, structures.resolve(nbt.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : walk.toList()) {
                String name = source.getFileName().toString();
                if (name.equals("session.lock")) continue;
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) Files.createDirectories(target);
                else Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
