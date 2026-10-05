package local.codexlimits;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;

public final class CodexAuthTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("codex-auth-");
        try {
            String claims = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"email\":\"me@example.com\",\"chatgpt_account_id\":\"acct-1\"}".getBytes(StandardCharsets.UTF_8));
            Path source = dir.resolve("source.json");
            Files.writeString(source, "{\"tokens\":{\"id_token\":\"x." + claims + ".x\"}}");
            CodexAuth.Identity identity = CodexAuth.read(source);
            assert identity.email().equals("me@example.com");
            assert identity.accountId().equals("acct-1");
            Path target = dir.resolve("nested/auth.json");
            CodexAuth.copyAtomically(source, target);
            assert Files.readString(target).equals(Files.readString(source));
        } finally { try (var paths = Files.walk(dir)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
        System.out.println("Codex auth tests passed");
    }
}
