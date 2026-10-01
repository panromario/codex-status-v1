package local.codexlimits;

import com.google.gson.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

record Quota(List<Window> windows) {
    private static final int FIVE_HOURS_MINUTES = 300;

    record Window(double used, int minutes, Long reset) {
        double remaining() { return Math.max(0, 100 - used); }
        String period() { return minutes <= 0 ? "период" : minutes % 1440 == 0 ? minutes / 1440 + "д" : minutes % 60 == 0 ? minutes / 60 + "ч" : minutes + "м"; }
    }
    enum Level { GREEN, YELLOW, RED }
    private Window statusWindow() {
        return windows.stream().filter(w -> w.minutes() == FIVE_HOURS_MINUTES).findFirst().orElse(windows.getFirst());
    }
    double remaining() { return statusWindow().remaining(); }
    String accountPercent() {
        return windows.stream().filter(w -> w.minutes() == FIVE_HOURS_MINUTES).findFirst()
            .map(w -> (int)Math.floor(w.remaining()) + "%").orElse("—");
    }
    Level level() { return remaining() < 10 ? Level.RED : remaining() >= 40 ? Level.GREEN : Level.YELLOW; }
    String text() { return "Codex " + (int)Math.floor(remaining()) + "%"; }
    String resetsHtml(Instant now) {
        StringBuilder html = new StringBuilder();
        for (Window w : windows) {
            if (!html.isEmpty()) html.append("<br>");
            html.append(w.period()).append(": ");
            if (w.reset() == null) { html.append("время сброса неизвестно"); continue; }
            long seconds = w.reset() - now.getEpochSecond();
            String color = seconds <= 1800 ? "#238636" : seconds <= 3600 ? "#947000" : "#C62828";
            html.append("<font color='").append(color).append("'>")
                .append(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .format(Instant.ofEpochSecond(w.reset()).atZone(ZoneId.systemDefault())))
                .append("</font>");
        }
        return html.toString();
    }
    String compactHtml(Instant now) {
        StringBuilder html = new StringBuilder();
        for (Window w : windows) {
            if (!html.isEmpty()) html.append(" &nbsp;·&nbsp; ");
            html.append(w.period()).append(' ').append((int)Math.floor(w.remaining())).append('%');
            if (w.reset() != null) {
                long seconds = w.reset() - now.getEpochSecond();
                String color = seconds <= 1800 ? "#238636" : seconds <= 3600 ? "#947000" : "#C62828";
                html.append(" <font color='").append(color).append("'>↻ ")
                    .append(DateTimeFormatter.ofPattern("dd.MM HH:mm")
                        .format(Instant.ofEpochSecond(w.reset()).atZone(ZoneId.systemDefault())))
                    .append("</font>");
            }
        }
        return html.toString();
    }
    String tooltip() {
        StringBuilder s = new StringBuilder("<html>Остаток Codex по периодам:");
        for (Window w : windows) {
            s.append("<br>").append(w.period()).append(": ").append(String.format(Locale.ROOT, "%.1f%%", w.remaining()));
            if (w.reset != null) s.append(" · сброс ").append(DateTimeFormatter.ofPattern("dd.MM HH:mm").format(Instant.ofEpochSecond(w.reset).atZone(ZoneId.systemDefault())));
        }
        return s.append("<br>Любой клик: все лимиты и настройки</html>").toString();
    }
    static Quota parse(JsonObject result) {
        JsonObject limits = object(result, "rateLimits");
        JsonObject buckets = object(result, "rateLimitsByLimitId");
        if (buckets != null && object(buckets, "codex") != null) limits = object(buckets, "codex");
        List<Window> windows = new ArrayList<>();
        if (limits != null) for (String key : List.of("primary", "secondary")) {
            JsonObject w = object(limits, key);
            if (w == null || !w.has("usedPercent") || w.get("usedPercent").isJsonNull()) continue;
            double used = w.get("usedPercent").getAsDouble();
            if (!Double.isFinite(used) || used < 0) throw new IllegalArgumentException("Invalid quota");
            windows.add(new Window(used, w.has("windowDurationMins") && !w.get("windowDurationMins").isJsonNull() ? w.get("windowDurationMins").getAsInt() : 0,
                w.has("resetsAt") && !w.get("resetsAt").isJsonNull() ? w.get("resetsAt").getAsLong() : null));
        }
        if (windows.isEmpty()) throw new IllegalArgumentException("No quota");
        return new Quota(List.copyOf(windows));
    }
    private static JsonObject object(JsonObject o, String key) { return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null; }
}
