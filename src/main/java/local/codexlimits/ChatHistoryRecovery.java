package local.codexlimits;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

final class ChatHistoryRecovery {
    static boolean copyLatestRollout(Path sourceHome, Path targetHome) throws IOException {
        Path source = sourceHome.resolve("sessions");
        if (!Files.isDirectory(source)) return false;
        Path latest;
        try (var files = Files.walk(source)) {
            latest = files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                .max(Comparator.comparing(ChatHistoryRecovery::modified)).orElse(null);
        }
        if (latest == null) return false;
        Path target = targetHome.resolve("sessions").resolve(source.relativize(latest));
        Files.createDirectories(target.getParent());
        if (!Files.exists(target) || Files.getLastModifiedTime(latest).compareTo(Files.getLastModifiedTime(target)) > 0)
            CodexAuth.copyAtomically(latest, target);
        return true;
    }

    private static FileTime modified(Path path) {
        try { return Files.getLastModifiedTime(path); }
        catch (IOException e) { return FileTime.fromMillis(0); }
    }
}
