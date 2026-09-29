package dev.golem.harness.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The Stonecutter nodes declared in {@code harness.json}, each with its fixed listener port. */
public final class Nodes {

    public record Node(String name, String mcVersion, String loader, int port, int packFormat, int javaVersion) {
        public boolean fabric() {
            return loader.equals("fabric");
        }

        public boolean forgeLike() {
            return !fabric();
        }

        /** Datapack structure folder name: 1.21+ singular, 1.20.x plural. */
        public String structureFolder() {
            return mcVersion.startsWith("1.20.") || mcVersion.startsWith("1.19") ? "structures" : "structure";
        }
    }

    private Nodes() {}

    public static List<Node> all() {
        return Config.get().nodes;
    }

    public static List<String> defaultUp() {
        return Config.get().defaultNodes;
    }

    public static Node get(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (Node node : all()) if (node.name().equalsIgnoreCase(n)) return node;
        throw new IllegalArgumentException("unknown node '" + name + "'; known: " + names());
    }

    public static List<Node> parse(String list) {
        List<Node> nodes = new ArrayList<>();
        for (String part : list.split("[,\\s]+")) {
            if (part.isBlank()) continue;
            if (part.equalsIgnoreCase("all")) return new ArrayList<>(all());
            nodes.add(get(part));
        }
        return nodes;
    }

    public static String names() {
        return String.join(", ", all().stream().map(Node::name).toList());
    }

    public static List<String> namesOf(List<Node> nodes) {
        return nodes.stream().map(Node::name).toList();
    }
}
