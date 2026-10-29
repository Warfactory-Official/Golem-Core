package dev.golem.core.dejson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * The build's entry point into a consumer's declarations.
 *
 * <p>Takes the classes that hold the declarations, calls {@code declare()} on each, and then either
 * writes the files out or checks them against the authored ones. It runs in a plain JVM: nothing in
 * the declaration vocabulary touches Minecraft, which is what makes this a build step rather than a
 * game one.
 *
 * <pre>{@code
 * java dev.golem.core.dejson.DejsonMain write  <out> <authored> com.hbm.blocks.ModBlockModels …
 * java dev.golem.core.dejson.DejsonMain verify <out> <authored> com.hbm.blocks.ModBlockModels …
 * }</pre>
 */
public final class DejsonMain {

    private DejsonMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "usage: DejsonMain <write|verify> <out> <authored> <declaring class>...");
        }
        String mode = args[0];
        Path out = Path.of(args[1]);
        Path authored = Path.of(args[2]);
        Data.authoredRoot(authored);

        for (int index = 3; index < args.length; index++) {
            declare(args[index]);
        }

        int declarations = Dejson.declarations().size() + Dejson.itemDeclarations().size() + Data.size();
        if (declarations == 0) {
            throw new IllegalStateException("no declarations were registered; "
                    + "the classes named on the command line declared nothing");
        }

        List<DejsonWriter.Mismatch> mismatches = DejsonWriter.verify(authored);
        if (!mismatches.isEmpty()) {
            StringBuilder report = new StringBuilder(mismatches.size()
                    + " declaration(s) disagree with the file they stand in for:");
            for (DejsonWriter.Mismatch mismatch : mismatches.subList(0, Math.min(20, mismatches.size()))) {
                report.append("\n  ").append(mismatch.path()).append("\n    ").append(mismatch.detail());
            }
            if (mismatches.size() > 20) {
                report.append("\n  ... and ").append(mismatches.size() - 20).append(" more");
            }
            throw new IllegalStateException(report.toString());
        }

        List<String> missingSprites = DejsonWriter.missingSprites(authored);
        if (!missingSprites.isEmpty()) {
            StringBuilder report = new StringBuilder(missingSprites.size()
                    + " declared sprite(s) have no texture file:");
            for (String sprite : missingSprites.subList(0, Math.min(20, missingSprites.size()))) {
                report.append("\n  ").append(sprite);
            }
            if (missingSprites.size() > 20) {
                report.append("\n  ... and ").append(missingSprites.size() - 20).append(" more");
            }
            throw new IllegalStateException(report.toString());
        }

        int covered = DejsonWriter.covered(authored);
        if ("verify".equals(mode)) {
            System.out.println("dejson: " + declarations + " declarations, "
                    + Dejson.sprites().size() + " sprites all present, "
                    + covered + " authored file(s) still covered and matching");
            return;
        }
        if (!"write".equals(mode)) {
            throw new IllegalArgumentException("unknown mode '" + mode + "'; expected write or verify");
        }

        if (Files.exists(out)) {
            try (var paths = Files.walk(out)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                });
            }
        }
        DejsonWriter.Report report = DejsonWriter.write(out, authored);
        System.out.println("dejson: wrote " + report.written() + " file(s); "
                + report.deferred() + " left to the authored copy");
    }

    /**
     * Runs one declaring class: initialises it, then calls its static {@code declare()} if it has one.
     *
     * <p>Initialising is itself a way to declare: a registration class whose fields chain declarations
     * onto each registration ({@code block(...).model(...).loot(...)}) records them while its statics
     * run and has no {@code declare()}. Registrations are recorded, never flushed (there is no game), so
     * its static initialiser must not reach a Minecraft class that needs a bootstrapped game (a
     * {@code SoundEvents} constant, a {@code BuiltInRegistries} lookup). Keep those inside the factory
     * and properties lambdas, which defer them.
     */
    private static void declare(String className) throws Exception {
        Class<?> type;
        try {
            type = Class.forName(className, true, DejsonMain.class.getClassLoader());
        } catch (ExceptionInInitializerError | NoClassDefFoundError failure) {
            Throwable root = failure;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw new IllegalStateException(className + " cannot be initialised outside the game ("
                    + root + "). A class listed as declaring runs in a plain JVM: move whatever its static "
                    + "initialiser touches that needs a running game into a lambda or a holder class.", failure);
        }
        Method declare;
        try {
            declare = type.getDeclaredMethod("declare");
        } catch (NoSuchMethodException initialisedOnly) {
            return;
        }
        declare.setAccessible(true);
        declare.invoke(null);
    }
}
