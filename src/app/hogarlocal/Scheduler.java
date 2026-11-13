package app.hogarlocal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import java.time.ZonedDateTime;

import app.hogarlocal.sched.Planner;

/**
 * Deja puesta una sola alarma: la de la próxima regla. Al sonar, el receptor
 * ejecuta lo que toque y vuelve a llamar aquí para poner la siguiente.
 */
public final class Scheduler {
    static final String ACTION = "app.hogarlocal.PROGRAMACION";
    static final String EXTRA_AT = "at";

    private Scheduler() { }

    private static PendingIntent intent(Context c, long at) {
        Intent i = new Intent(c, ScheduleReceiver.class).setAction(ACTION).putExtra(EXTRA_AT, at);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Recalcula y pone (o quita) la alarma. Llamar desde el hilo principal tras cualquier cambio. */
    public static void update(Context ctx) {
        Context c = ctx.getApplicationContext();
        Home home = Home.get(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        ZonedDateTime next = Planner.nextAlarm(home.store.schedules, ZonedDateTime.now());
        if (next == null) {
            am.cancel(intent(c, 0));
            return;
        }
        long at = next.toInstant().toEpochMilli();
        PendingIntent pi = intent(c, at);
        try {
            if (exactAllowed(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static boolean exactAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    /** Si Android puede dormir la app y retrasar las alarmas (ahorro de batería). */
    public static boolean batteryRestricted(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && !pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }
}
