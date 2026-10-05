package local.codexlimits;

import java.time.*;
import java.time.format.*;

final class AccountAge {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd.MM.uuuu")
        .withResolverStyle(ResolverStyle.STRICT);

    static LocalDate parse(String value) {
        try { return LocalDate.parse(value, DISPLAY); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Введите дату в формате ДД.ММ.ГГГГ."); }
    }

    static String format(LocalDate date) { return DISPLAY.format(date); }

    static String expiry(String registeredOn) {
        return "расчётная дата: " + format(LocalDate.parse(registeredOn).plusMonths(1));
    }

    static String information(String registeredOn, LocalDate today) {
        if (registeredOn == null || registeredOn.isBlank()) return "";
        LocalDate end = LocalDate.parse(registeredOn).plusMonths(1);
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, end);
        String text = "Расчётная дата окончания месяца: " + format(end) + ".";
        return days >= 0 && days <= 5 ? text + "<br><font color='#947000'>Осталось дней: " + days + ".</font><br>" : text + "<br>";
    }
}
