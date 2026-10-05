package local.codexlimits;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import javax.swing.*;
import java.nio.file.*;
import java.util.UUID;

final class AccountLogin {
    static void start(Project project, String executable, String name, String registeredOn, Runnable done) {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            Path home = AccountProfiles.profilesRoot().resolve("profile-" + UUID.randomUUID());
            try {
                Files.createDirectories(home);
                ProcessBuilder builder = CliEnvironment.builder(executable, EnvironmentUtil.getEnvironmentMap(), home.toString(), "login");
                builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process process = builder.start();
                int code = process.waitFor();
                Path auth = home.resolve("auth.json");
                if (code != 0 || !Files.isRegularFile(auth)) throw new IllegalStateException("Codex CLI не завершил вход.");
                CodexAuth.Identity identity = CodexAuth.read(auth);
                AccountProfiles.Profile profile = AccountProfiles.add(name, identity.email(), home, registeredOn);
                AccountSwitcher.switchTo(profile);
                SwingUtilities.invokeLater(() -> { done.run(); Messages.showInfoMessage(project, "Аккаунт добавлен.", PluginInfo.TITLE); });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> Messages.showErrorDialog(project, "Не удалось добавить аккаунт: " + e.getMessage(), PluginInfo.TITLE));
            }
        });
    }
}
