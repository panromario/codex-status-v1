package local.codexlimits;

import java.time.LocalDate;

public final class AccountAgeTest {
    public static void main(String[] args) {
        LocalDate date = AccountAge.parse("31.01.2026");
        assert AccountAge.format(date).equals("31.01.2026");
        assert AccountAge.expiry("2026-01-31").contains("28.02.2026");
        assert AccountAge.information("2026-01-01", LocalDate.of(2026, 1, 28)).contains("Осталось дней: 4");
        boolean rejected = false;
        try { AccountAge.parse("31.02.2026"); } catch (IllegalArgumentException e) { rejected = true; }
        assert rejected;
        System.out.println("Account age tests passed");
    }
}
