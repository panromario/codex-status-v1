package local.codexlimits;

import java.nio.file.*;

public final class AccountProfilesTest {
    public static void main(String[] args) throws Exception {
        String original = System.getProperty("user.home");
        Path root = Files.createTempDirectory("codex-profiles-");
        try {
            System.setProperty("user.home", root.toString());
            Path home = AccountProfiles.profilesRoot().resolve("profile-test"); Files.createDirectories(home);
            AccountProfiles.Profile profile = AccountProfiles.add("Работа", "me@example.com", home, "2026-10-05");
            assert AccountProfiles.all().size() == 1;
            assert AccountProfiles.active() == null;
            AccountProfiles.select(profile);
            assert AccountProfiles.active().displayName().contains("me@example.com");
            AccountProfiles.rename(profile, "Личный");
            AccountProfiles.Profile renamed = AccountProfiles.active();
            assert renamed.name().equals("Личный");
            AccountProfiles.setRegistrationDate(renamed, "2026-10-06");
            assert AccountProfiles.active().registeredOn().equals("2026-10-06");
            AccountProfiles.delete(AccountProfiles.active());
            assert AccountProfiles.all().isEmpty() && !Files.exists(home);
        } finally {
            System.setProperty("user.home", original);
            try (var paths = Files.walk(root)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
        System.out.println("Account profiles tests passed");
    }
}
