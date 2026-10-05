package local.codexlimits;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

final class TokenStatistics {
    record Usage(long total, long input, long cached, long output, long reasoning) {
        Usage add(Usage other) { return new Usage(total + other.total, input + other.input, cached + other.cached, output + other.output, reasoning + other.reasoning); }
    }
    private final SortedMap<LocalDate, Usage> days;
    private TokenStatistics(SortedMap<LocalDate, Usage> days) { this.days = Collections.unmodifiableSortedMap(days); }

    static TokenStatistics read(Path codexHome) throws IOException {
        SortedMap<LocalDate, Usage> days = new TreeMap<>(Comparator.reverseOrder());
        Path sessions = codexHome.resolve("sessions");
        if (!Files.isDirectory(sessions)) return new TokenStatistics(days);
        try (var files = Files.walk(sessions)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".jsonl")).toList())
                readFile(file, days);
        }
        return new TokenStatistics(days);
    }

    private static void readFile(Path file, SortedMap<LocalDate, Usage> days) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
            try {
                JsonObject root = JsonParser.parseString(line).getAsJsonObject();
                JsonObject payload = object(root, "payload");
                if (payload == null || !"token_count".equals(text(payload, "type"))) continue;
                JsonObject info = object(payload, "info");
                JsonObject usage = object(info, "last_token_usage");
                if (usage == null) continue;
                LocalDate date = date(text(root, "timestamp"), file);
                Usage parsed = usage(usage);
                days.merge(date, parsed, Usage::add);
            } catch (JsonParseException | IllegalStateException ignored) { }
            }
        }
    }

    private static LocalDate date(String timestamp, Path file) throws IOException {
        if (!timestamp.isBlank()) try { return Instant.parse(timestamp).atZone(ZoneId.systemDefault()).toLocalDate(); }
        catch (DateTimeParseException ignored) { }
        return Files.getLastModifiedTime(file).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    private static Usage usage(JsonObject o) {
        long input = number(o, "input_tokens"), cached = number(o, "cached_input_tokens");
        long output = number(o, "output_tokens"), reasoning = number(o, "reasoning_output_tokens");
        long total = number(o, "total_tokens");
        if (total == 0) total = input + output;
        return new Usage(total, input, cached, output, reasoning);
    }

    String html() {
        StringBuilder html = new StringBuilder("<html><b>Локальная статистика токенов</b>");
        if (days.isEmpty()) return html.append("<br>Данные usage не найдены.</html>").toString();
        html.append("<table><tr><th>Дата</th><th>Всего</th><th>Входные</th><th>Кэш</th><th>Выходные</th><th>Reasoning</th></tr>");
        for (var entry : days.entrySet()) {
            Usage u = entry.getValue();
            html.append("<tr><td>").append(entry.getKey()).append("</td><td>").append(u.total())
                .append("</td><td>").append(u.input()).append("</td><td>").append(u.cached())
                .append("</td><td>").append(u.output()).append("</td><td>").append(u.reasoning()).append("</td></tr>");
        }
        return html.append("</table></html>").toString();
    }
    SortedMap<LocalDate, Usage> days() { return days; }
    private static JsonObject object(JsonObject o, String key) { return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null; }
    private static String text(JsonObject o, String key) { return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : ""; }
    private static long number(JsonObject o, String key) { return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0; }
}
