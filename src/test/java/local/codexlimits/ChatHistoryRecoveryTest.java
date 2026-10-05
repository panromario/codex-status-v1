package local.codexlimits;

import java.nio.file.*;

public final class ChatHistoryRecoveryTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("codex-history-");
        try {
            Path from = root.resolve("from"), to = root.resolve("to");
            Path old = from.resolve("sessions/old.jsonl"), latest = from.resolve("sessions/2026/latest.jsonl");
            Files.createDirectories(latest.getParent());
            Files.writeString(old, "old"); Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(1));
            Files.writeString(latest, "latest"); Files.setLastModifiedTime(latest, java.nio.file.attribute.FileTime.fromMillis(2));
            assert ChatHistoryRecovery.copyLatestRollout(from, to);
            assert Files.readString(to.resolve("sessions/2026/latest.jsonl")).equals("latest");
            assert !ChatHistoryRecovery.copyLatestRollout(root.resolve("missing"), to);
        } finally { try (var paths = Files.walk(root)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
        System.out.println("Chat history recovery tests passed");
    }
}
