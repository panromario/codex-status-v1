package local.codexlimits;

import java.nio.file.*;

public final class AccountSwitcherTest {
    public static void main(String[] args) throws Exception {
        String originalHome = System.getProperty("user.home");
        Path root = Files.createTempDirectory("codex-switch-");
        try {
            System.setProperty("user.home", root.toString());
            Path main = AccountProfiles.systemHome(); Files.createDirectories(main);
            Files.writeString(main.resolve("auth.json"), "system-auth");
            Path firstHome = AccountProfiles.profilesRoot().resolve("profile-first");
            Path secondHome = AccountProfiles.profilesRoot().resolve("profile-second");
            Files.createDirectories(firstHome); Files.createDirectories(secondHome);
            Files.writeString(firstHome.resolve("auth.json"), "first-auth");
            Files.writeString(secondHome.resolve("auth.json"), "second-auth");
            AccountProfiles.Profile first = AccountProfiles.add("First", "", firstHome, "2026-10-01");
            AccountProfiles.Profile second = AccountProfiles.add("Second", "", secondHome, "2026-10-01");
            AccountSwitcher.switchTo(first);
            assert Files.readString(main.resolve("auth.before-account-switch.json")).equals("system-auth");
            assert Files.readString(main.resolve("auth.json")).equals("first-auth");
            Files.writeString(main.resolve("auth.json"), "first-refreshed");
            Path rollout = main.resolve("sessions/2026/rollout.jsonl"); Files.createDirectories(rollout.getParent()); Files.writeString(rollout, "rollout");
            AccountSwitcher.switchTo(second);
            assert Files.readString(firstHome.resolve("auth.json")).equals("first-refreshed");
            assert Files.readString(main.resolve("auth.json")).equals("second-auth");
            assert Files.readString(firstHome.resolve("sessions/2026/rollout.jsonl")).equals("rollout");
            assert AccountProfiles.active().id().equals(second.id());
        } finally {
            System.setProperty("user.home", originalHome);
            try (var paths = Files.walk(root)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
        System.out.println("Account switcher tests passed");
    }
}
