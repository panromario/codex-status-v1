package local.codexlimits;

import java.nio.file.*;
import java.time.LocalDate;

public final class TokenStatisticsTest {
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("codex-stats-");
        try {
            Path file = home.resolve("sessions/2026/10/05/rollout.jsonl");
            Files.createDirectories(file.getParent());
            String one = "{\"timestamp\":\"2026-10-05T10:00:00Z\",\"payload\":{\"type\":\"token_count\",\"info\":{\"last_token_usage\":{\"input_tokens\":10,\"cached_input_tokens\":4,\"output_tokens\":3,\"reasoning_output_tokens\":2,\"total_tokens\":13}}}}";
            String two = "{\"timestamp\":\"2026-10-05T11:00:00Z\",\"payload\":{\"type\":\"token_count\",\"info\":{\"last_token_usage\":{\"input_tokens\":5,\"output_tokens\":7,\"total_tokens\":12}}}}";
            Files.writeString(file, one + System.lineSeparator() + "not-json" + System.lineSeparator() + two);
            TokenStatistics statistics = TokenStatistics.read(home);
            TokenStatistics.Usage usage = statistics.days().get(LocalDate.of(2026, 10, 5));
            assert usage != null && usage.total() == 25 && usage.input() == 15 && usage.cached() == 4 && usage.output() == 10 && usage.reasoning() == 2;
            assert statistics.html().contains("2026-10-05");
        } finally { try (var paths = Files.walk(home)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
        System.out.println("Token statistics tests passed");
    }
}
