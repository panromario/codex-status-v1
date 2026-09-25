package local.codexlimits;

import com.google.gson.JsonParser;
import java.time.Instant;

public final class QuotaTest {
    private static Quota parse(String json) { return Quota.parse(JsonParser.parseString(json).getAsJsonObject()); }
    private static Quota used(double n) { return parse("{\"rateLimits\":{\"primary\":{\"usedPercent\":" + n + "}}}"); }
    public static void main(String[] args) {
        assert used(0).text().equals("Codex 100%");
        assert used(60).level() == Quota.Level.GREEN;
        assert used(60.01).level() == Quota.Level.YELLOW;
        assert used(90).level() == Quota.Level.YELLOW;
        assert used(90.01).level() == Quota.Level.RED;
        assert used(120).remaining() == 0;
        Quota both = parse("{\"rateLimits\":{\"primary\":{\"usedPercent\":5,\"windowDurationMins\":300},\"secondary\":{\"usedPercent\":95,\"windowDurationMins\":10080,\"resetsAt\":1800000000}}}");
        assert both.remaining() == 95;
        assert both.level() == Quota.Level.GREEN;
        assert both.text().equals("Codex 95%");
        assert both.tooltip().contains("5ч") && both.tooltip().contains("7д") && both.tooltip().contains("сброс");
        Quota resets = parse("{\"rateLimits\":{\"primary\":{\"usedPercent\":20,\"windowDurationMins\":300,\"resetsAt\":1001800},\"secondary\":{\"usedPercent\":30,\"windowDurationMins\":10080,\"resetsAt\":1003600}}}");
        String resetHtml = resets.resetsHtml(Instant.ofEpochSecond(1000000));
        assert resetHtml.contains("5ч: <font color='#238636'>") : resetHtml;
        assert resetHtml.contains("7д: <font color='#947000'>") : resetHtml;
        assert resets.resetsHtml(Instant.ofEpochSecond(999999)).contains("#C62828");
        Quota reversed = parse("{\"rateLimits\":{\"primary\":{\"usedPercent\":95,\"windowDurationMins\":10080},\"secondary\":{\"usedPercent\":20,\"windowDurationMins\":300}}}");
        assert reversed.remaining() == 80;
        assert parse("{\"rateLimits\":{\"primary\":{\"usedPercent\":99}},\"rateLimitsByLimitId\":{\"codex\":{\"primary\":{\"usedPercent\":10}}}}").remaining() == 90;
        for (String invalid : new String[]{"{}", "{\"rateLimits\":null}", "{\"rateLimits\":{\"primary\":null}}", "{\"rateLimits\":{\"primary\":{\"usedPercent\":-1}}}"}) {
            boolean rejected = false;
            try { parse(invalid); } catch (IllegalArgumentException e) { rejected = true; }
            assert rejected : invalid;
        }
        System.out.println("Quota tests passed");
    }
}
