package local.codexlimits;

import com.google.gson.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.*;
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class LimitWidget implements CustomStatusBarWidget {
    private static final java.util.Set<LimitWidget> ACTIVE = ConcurrentHashMap.newKeySet();
    private final Project project;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicLong refreshGeneration = new AtomicLong();
    private final java.util.List<ScheduledFuture<?>> afterRequest = new java.util.ArrayList<>();
    private final JLabel label = new JLabel("Codex …");
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean disposed;
    private volatile Process process;
    private ScheduledFuture<?> polling;
    private static final String KEY = AccountProfiles.KEY;
    static void chatActivity(Project project) {
        for (LimitWidget widget : ACTIVE) if (widget.project == project) widget.onRequest();
    }
    private synchronized void onRequest() {
        if (disposed) return;
        refresh();
        afterRequest.forEach(task -> task.cancel(false));
        afterRequest.clear();
        for (long delay : new long[]{5, 15, 30})
            afterRequest.add(AppExecutorUtil.getAppScheduledExecutorService().schedule(this::refresh, delay, TimeUnit.SECONDS));
    }
    LimitWidget(Project project) {
        this.project = project;
        label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        label.setToolTipText(PluginInfo.TITLE + " · загрузка лимитов · любой клик: все лимиты");
        label.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                showLimits(e);
            }
        });
    }
    public @NotNull String ID() { return "CodexLimitStatus"; }
    public @NotNull JComponent getComponent() { return label; }
    public void install(@NotNull StatusBar bar) {
        ACTIVE.add(this);
        polling = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(this::refresh, 0, 30, TimeUnit.SECONDS);
    }
    private static String executable() {
        String configured = PropertiesComponent.getInstance().getValue(KEY + "executable");
        if (configured != null && !configured.isBlank()) return configured;
        for (String path : new String[]{"/opt/homebrew/bin/codex", "/usr/local/bin/codex", System.getProperty("user.home") + "/.local/bin/codex"})
            if (Files.isExecutable(Path.of(path))) return path;
        return "codex";
    }
    private void showLimits(MouseEvent event) {
        JPopupMenu popup = new JPopupMenu();
        JLabel details = new JLabel(label.getToolTipText());
        details.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        popup.add(details);
        popup.addSeparator();
        AccountProfiles.Profile active = AccountProfiles.active();
        if (!AccountProfiles.all().isEmpty()) {
            JMenu accounts = new JMenu("Аккаунты");
            ButtonGroup group = new ButtonGroup();
            for (AccountProfiles.Profile profile : AccountProfiles.all()) {
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(accountText(profile, "Загрузка времени сброса…"), profile.equals(active));
                item.addActionListener(e -> selectAccount(profile));
                group.add(item); accounts.add(item);
                AppExecutorUtil.getAppExecutorService().execute(() -> {
                    String detail;
                    try { detail = readQuota(profile.home(), false).resetsHtml(Instant.now()); }
                    catch (Exception ignored) { detail = "Время сброса недоступно"; }
                    String result = detail;
                    SwingUtilities.invokeLater(() -> { if (!disposed) item.setText(accountText(profile, result)); });
                });
            }
            accounts.addSeparator();
            JMenuItem rename = new JMenuItem("Переименовать выбранный…");
            rename.setEnabled(active != null);
            rename.addActionListener(e -> renameAccount(active));
            accounts.add(rename);
            JMenuItem delete = new JMenuItem("Удалить выбранный…");
            delete.setEnabled(active != null);
            delete.addActionListener(e -> deleteAccount(active));
            accounts.add(delete);
            popup.add(accounts);
        }
        JMenuItem addAccount = new JMenuItem("Добавить аккаунт…");
        addAccount.addActionListener(e -> addAccount());
        popup.add(addAccount);
        popup.addSeparator();
        JMenuItem statistics = new JMenuItem("Статистика токенов…");
        statistics.addActionListener(e -> showTokenStatistics());
        popup.add(statistics);
        JMenuItem settings = new JMenuItem("Настройки…");
        settings.addActionListener(e -> configure());
        popup.add(settings);
        popup.show(label, event.getX(), event.getY());
    }
    private static String accountText(AccountProfiles.Profile profile, String detail) {
        return "<html>" + escapeHtml(profile.displayName()) + "<br><small>" + detail + "</small></html>";
    }
    private static void selectAccount(AccountProfiles.Profile profile) {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                AccountSwitcher.switchTo(profile);
                for (LimitWidget widget : ACTIVE) widget.accountChanged();
                SwingUtilities.invokeLater(() -> Messages.showInfoMessage(
                    "Аккаунт «" + profile.displayName() + "» активирован. Можно продолжить текущий чат Codex.", PluginInfo.TITLE));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> Messages.showErrorDialog(
                    "Не удалось переключить авторизацию: " + e.getMessage(), PluginInfo.TITLE));
            }
        });
    }
    private void accountChanged() {
        refreshGeneration.incrementAndGet();
        Process running;
        synchronized (this) { running = process; }
        if (running != null) stop(running);
        SwingUtilities.invokeLater(() -> {
            if (!disposed) {
                label.setText("Codex …");
                label.setToolTipText(PluginInfo.TITLE + " · переключение аккаунта…");
                label.setForeground(JBColor.GRAY);
            }
        });
        refresh();
    }
    private void showTokenStatistics() {
        AccountProfiles.Profile profile = AccountProfiles.active();
        String configured = profile == null ? PropertiesComponent.getInstance().getValue(KEY + "home", "") : profile.home();
        if (configured.isBlank()) configured = EnvironmentUtil.getEnvironmentMap().getOrDefault("CODEX_HOME", "");
        Path home = Path.of(configured.isBlank() ? System.getProperty("user.home") + "/.codex" : CliEnvironment.expandHome(configured));
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                String html = TokenStatistics.read(home).html();
                SwingUtilities.invokeLater(() -> Messages.showInfoMessage(project, html, "Статистика токенов · " + PluginInfo.TITLE));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> Messages.showErrorDialog(project,
                    "Не удалось прочитать локальную статистику токенов.", PluginInfo.TITLE));
            }
        });
    }
    private void addAccount() {
        String name = Messages.showInputDialog(project, "Название аккаунта (например, Работа):", PluginInfo.TITLE, null);
        if (name == null || name.isBlank()) return;
        AccountLogin.start(project, executable(), name.trim(), this::refresh);
    }
    private void renameAccount(AccountProfiles.Profile profile) {
        if (profile == null) return;
        String name = Messages.showInputDialog(project, "Новое название аккаунта:", PluginInfo.TITLE, null, profile.name(), null);
        if (name == null || name.isBlank()) return;
        AccountProfiles.rename(profile, name.trim());
        refresh();
    }
    private void deleteAccount(AccountProfiles.Profile profile) {
        if (profile == null) return;
        int answer = Messages.showYesNoDialog(project,
            "Удалить аккаунт «" + profile.displayName() + "» и его локальные данные авторизации?",
            PluginInfo.TITLE, "Удалить", "Отмена", Messages.getWarningIcon());
        if (answer != Messages.YES) return;
        try {
            AccountProfiles.delete(profile);
            AccountProfiles.Profile next = AccountProfiles.active();
            if (next != null) {
                AccountSwitcher.switchTo(next);
            }
            refresh();
        } catch (IOException e) {
            Messages.showErrorDialog(project, "Не удалось удалить аккаунт: " + e.getMessage(), PluginInfo.TITLE);
        }
    }
    private void configure() {
        String exe = Messages.showInputDialog("Путь к исполняемому файлу Codex CLI:", PluginInfo.TITLE, null, executable(), null);
        if (exe == null || exe.isBlank()) return;
        String home = Messages.showInputDialog("CODEX_HOME (пусто — окружение или ~/.codex):", PluginInfo.TITLE, null,
            PropertiesComponent.getInstance().getValue(KEY + "home", ""), null);
        if (home == null) return;
        PropertiesComponent.getInstance().setValue(KEY + "executable", exe.trim());
        PropertiesComponent.getInstance().setValue(KEY + "home", home.trim());
        AccountProfiles.clearSelection();
        refresh();
    }
    private void refresh() {
        if (disposed) return;
        if (!busy.compareAndSet(false, true)) { pending.set(true); return; }
        long generation = refreshGeneration.get();
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                Quota quota = readQuota();
                update(generation, quota.text(), quota.tooltip(), switch (quota.level()) {
                    case GREEN -> new JBColor(new Color(0x238636), new Color(0x65C879));
                    case YELLOW -> new JBColor(new Color(0x947000), new Color(0xE5C453));
                    case RED -> new JBColor(new Color(0xC62828), new Color(0xFF6B68));
                });
            } catch (Exception e) {
                // Never display or log server responses: they may contain account details.
                String reason = e instanceof EOFException ? "CLI завершился до ответа: проверьте Node.js и конфигурацию Codex."
                    : e instanceof TimeoutException ? "CLI не ответил за 25 секунд."
                    : e instanceof RpcException ? "CLI отклонил запрос лимитов: проверьте сеть и вход через codex login."
                    : e instanceof IOException ? "Не удалось запустить CLI или прочитать ответ: проверьте путь и права доступа."
                    : "Ответ CLI не содержит доступных лимитов.";
                update(generation, "Codex —", "<html>" + reason + "<br>Проверьте CODEX_HOME в настройках.<br>Любой клик: все лимиты и настройки</html>", JBColor.GRAY);
            } finally { busy.set(false); if (pending.getAndSet(false)) refresh(); }
        });
    }
    private Quota readQuota() throws Exception {
        String home = PropertiesComponent.getInstance().getValue(KEY + "home", "");
        return readQuota(home, true);
    }
    private Quota readQuota(String home, boolean trackProcess) throws Exception {
        ProcessBuilder builder = CliEnvironment.builder(executable(), EnvironmentUtil.getEnvironmentMap(), home);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p;
        synchronized (this) {
            if (disposed) throw new IOException("Disposed");
            p = builder.start();
            if (trackProcess) process = p;
        }
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> { timedOut.set(true); stop(p); }, 25, TimeUnit.SECONDS);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
            send(writer, "{\"id\":1,\"method\":\"initialize\",\"params\":{\"clientInfo\":{\"name\":\"codex_limit_status\",\"title\":\"" + PluginInfo.NAME + "\",\"version\":\"" + PluginInfo.VERSION + "\"}}}");
            response(reader, 1);
            send(writer, "{\"method\":\"initialized\"}");
            send(writer, "{\"id\":2,\"method\":\"account/rateLimits/read\"}");
            return Quota.parse(response(reader, 2));
        } catch (IOException e) {
            if (timedOut.get()) throw new TimeoutException();
            throw e;
        } finally {
            timeout.cancel(false);
            stop(p);
            if (trackProcess) synchronized (this) { if (process == p) process = null; }
        }
    }
    private static void send(BufferedWriter w, String line) throws IOException { w.write(line); w.newLine(); w.flush(); }
    private static final class RpcException extends IOException { }
    private static JsonObject response(BufferedReader reader, int id) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            JsonObject message = JsonParser.parseString(line).getAsJsonObject();
            if (!message.has("id") || message.get("id").isJsonNull() || !message.get("id").getAsString().equals(Integer.toString(id))) continue;
            if (message.has("error")) throw new RpcException();
            return message.getAsJsonObject("result");
        }
        throw new EOFException();
    }
    private void update(long generation, String text, String tooltip, Color color) {
        SwingUtilities.invokeLater(() -> {
            if (disposed || generation != refreshGeneration.get()) return;
            AccountProfiles.Profile profile = AccountProfiles.active();
            String account = profile == null ? "Системный CODEX_HOME" : escapeHtml(profile.displayName());
            label.setText(text);
            String accountInfo = profile == null ? "" : "Лимит относится к выбранному профилю.<br>" +
                AccountAge.information(profile.addedAt(), java.time.LocalDate.now());
            label.setToolTipText(tooltip.replace("<html>", "<html><b>" + PluginInfo.TITLE + "</b><br>Аккаунт: " + account + "<br>" + accountInfo));
            label.setForeground(color);
        });
    }
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static void stop(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }
    public synchronized void dispose() {
        disposed = true;
        ACTIVE.remove(this);
        afterRequest.forEach(task -> task.cancel(false));
        afterRequest.clear();
        if (polling != null) polling.cancel(false);
        if (process != null) stop(process);
    }
}
