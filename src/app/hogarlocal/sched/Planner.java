package app.hogarlocal.sched;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Cálculo de horarios, sin Android: siguiente ejecución y cuáles tocan en un momento dado. */
public final class Planner {
    private Planner() { }

    /** Primera ejecución estrictamente posterior a «from»; null si la regla no tiene días. */
    public static ZonedDateTime next(Schedule s, ZonedDateTime from) {
        if ((s.days & Schedule.EVERY_DAY) == 0) return null;
        LocalDate day = from.toLocalDate();
        for (int i = 0; i <= 8; i++, day = day.plusDays(1)) {
            int idx = day.getDayOfWeek().getValue() - 1;
            if (!s.hasDay(idx)) continue;
            // Si la hora no existe ese día (cambio de horario), ZonedDateTime la recorre hacia adelante.
            ZonedDateTime t = ZonedDateTime.of(day, LocalTime.of(s.hour, s.minute), from.getZone());
            if (t.isAfter(from)) return t;
        }
        return null;
    }

    /** Próxima alarma entre las reglas activas; null si no hay ninguna. */
    public static ZonedDateTime nextAlarm(List<Schedule> list, ZonedDateTime from) {
        ZonedDateTime best = null;
        for (Schedule s : list) {
            if (!s.enabled) continue;
            ZonedDateTime t = next(s, from);
            if (t != null && (best == null || t.isBefore(best))) best = t;
        }
        return best;
    }

    /** Reglas activas que tocan exactamente en «at» (al minuto). */
    public static List<Schedule> due(List<Schedule> list, ZonedDateTime at) {
        List<Schedule> out = new ArrayList<>();
        ZonedDateTime from = at.minusSeconds(1);
        for (Schedule s : list) {
            if (!s.enabled) continue;
            ZonedDateTime t = next(s, from);
            if (t != null && t.toEpochSecond() / 60 == at.toEpochSecond() / 60) out.add(s);
        }
        return out;
    }

    /** Próxima ejecución de las reglas activas de un dispositivo; null si no hay. */
    public static ZonedDateTime nextFor(List<Schedule> list, String deviceId, ZonedDateTime from) {
        List<Schedule> mine = new ArrayList<>();
        for (Schedule s : list) if (s.deviceId.equals(deviceId)) mine.add(s);
        return nextAlarm(mine, from);
    }
}
