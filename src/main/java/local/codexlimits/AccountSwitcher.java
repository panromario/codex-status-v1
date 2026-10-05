package local.codexlimits;

import java.io.*;
import java.nio.file.*;

final class AccountSwitcher {
    static void switchTo(AccountProfiles.Profile next) throws IOException {
        Path main = AccountProfiles.systemHome();
        Path mainAuth = main.resolve("auth.json");
        AccountProfiles.Profile previous = AccountProfiles.active();
        if (previous != null && !previous.id().equals(next.id())) {
            Path previousHome = Path.of(previous.home());
            if (Files.isRegularFile(mainAuth)) CodexAuth.copyAtomically(mainAuth, previousHome.resolve("auth.json"));
            ChatHistoryRecovery.copyLatestRollout(main, previousHome);
        } else if (previous == null && Files.isRegularFile(mainAuth)) {
            Path backup = main.resolve("auth.before-account-switch.json");
            if (!Files.exists(backup)) CodexAuth.copyAtomically(mainAuth, backup);
        }
        Path nextHome = Path.of(next.home());
        Path nextAuth = nextHome.resolve("auth.json");
        if (!Files.isRegularFile(nextAuth)) throw new FileNotFoundException("В профиле отсутствует auth.json");
        CodexAuth.copyAtomically(nextAuth, mainAuth);
        ChatHistoryRecovery.copyLatestRollout(nextHome, main);
        AccountProfiles.select(next);
    }
}
