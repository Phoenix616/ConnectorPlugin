package de.themoep.connectorplugin.bukkit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliaSchedulerGuardTest {

    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("Bukkit\\.getScheduler\\s*\\("),
            Pattern.compile("getServer\\s*\\(\\s*\\)\\.getScheduler\\s*\\("),
            Pattern.compile("\\brunTask\\s*\\("),
            Pattern.compile("\\brunTaskLater\\s*\\("),
            Pattern.compile("\\brunTaskTimer\\s*\\("),
            Pattern.compile("\\bscheduleSync\\w*\\s*\\("),
            Pattern.compile("\\bscheduleAsync\\w*\\s*\\(")
    );
    private static final List<Pattern> FORBIDDEN_RUNTIME_DEPENDENCIES = List.of(
            Pattern.compile("\\bVault\\b"),
            Pattern.compile("\\bEconomy\\b"),
            Pattern.compile("\\bServicesManager\\b"),
            Pattern.compile("\\bPaperLib\\b"),
            Pattern.compile("paperlib", Pattern.CASE_INSENSITIVE)
    );

    @Test
    void runtimeSourcesDoNotUseLegacyBukkitSchedulerApis() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");

        try (Stream<Path> sources = Files.walk(sourceRoot)) {
            List<String> violations = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(FoliaSchedulerGuardTest::scan)
                    .toList();

            assertTrue(violations.isEmpty(), () -> "Legacy Bukkit scheduler usage found:\n" + String.join("\n", violations));
        }
    }

    @Test
    void runtimeDoesNotDependOnVaultOrPaperLib() throws IOException {
        List<Path> runtimePaths = List.of(
                Path.of("pom.xml"),
                Path.of("..", "pom.xml"),
                Path.of("..", "plugin", "pom.xml"),
                Path.of("src", "main", "java"),
                Path.of("src", "main", "resources")
        );

        List<String> violations = runtimePaths.stream()
                .flatMap(FoliaSchedulerGuardTest::walkExisting)
                .filter(path -> path.toString().endsWith(".java")
                        || path.toString().endsWith(".yml")
                        || path.getFileName().toString().equals("pom.xml"))
                .flatMap(path -> scan(path, FORBIDDEN_RUNTIME_DEPENDENCIES))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Forbidden runtime dependency found:\n" + String.join("\n", violations));
    }

    @Test
    void pluginMetadataDeclaresFoliaSupport() throws IOException {
        String pluginYml = Files.readString(Path.of("src", "main", "resources", "plugin.yml"));

        assertTrue(pluginYml.contains("folia-supported: true"), "plugin.yml must declare folia-supported: true");
    }

    private static Stream<String> scan(Path source) {
        return scan(source, FORBIDDEN_PATTERNS);
    }

    private static Stream<String> scan(Path source, List<Pattern> patterns) {
        try {
            List<String> lines = Files.readAllLines(source);
            Stream.Builder<String> violations = Stream.builder();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(line).find()) {
                        violations.add(source + ":" + (i + 1) + ": " + line.trim());
                    }
                }
            }
            return violations.build();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan " + source, e);
        }
    }

    private static Stream<Path> walkExisting(Path path) {
        if (!Files.exists(path)) {
            return Stream.empty();
        }
        try {
            return Files.walk(path);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan " + path, e);
        }
    }
}
