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

    private static Stream<String> scan(Path source) {
        try {
            List<String> lines = Files.readAllLines(source);
            Stream.Builder<String> violations = Stream.builder();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (Pattern pattern : FORBIDDEN_PATTERNS) {
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
}
