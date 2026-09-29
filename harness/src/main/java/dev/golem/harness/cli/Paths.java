package dev.golem.harness.cli;

import java.nio.file.Path;

/** Repository layout, resolved from {@code harness.json}. */
public final class Paths {

    private Paths() {}

    public static Path mod() {
        return Config.get().mod;
    }

    public static Path golemCore() {
        return Config.get().golemCore;
    }

    public static Path out() {
        return Config.get().out;
    }

    /** Live-client state next to the config: {@code <config dir>/.harness/state.json}. */
    public static Path state() {
        return Config.get().dir.resolve(".harness");
    }

    public static Path nodeDir(Nodes.Node node) {
        return mod().resolve("versions").resolve(node.name());
    }

    public static Path runDir(Nodes.Node node) {
        return nodeDir(node).resolve("runs").resolve("harness");
    }

    public static Path clientRunDir(Nodes.Node node) {
        return nodeDir(node).resolve("runs").resolve("client");
    }

    public static Path specFile(Nodes.Node node) {
        return nodeDir(node).resolve("build").resolve("harness").resolve("spec.json");
    }

    public static Path fixtures() {
        return Config.get().fixtures;
    }

    public static Path scenarios() {
        return Config.get().scenarios;
    }
}
