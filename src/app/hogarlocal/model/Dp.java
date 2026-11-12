package app.hogarlocal.model;

/** Un «data point» del dispositivo: un interruptor, un temporizador, una medición… */
public final class Dp {
    public String id = "";
    /** Código de Tuya (switch_1, countdown_1, cur_power…); vacío si no se conoce. */
    public String code = "";
    /** Nombre que le puso el usuario; vacío para usar el predeterminado. */
    public String name = "";
    /** bool, int o str; vacío si aún no se ha visto un valor. */
    public String type = "";

    public int num() {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) { return -1; }
    }
}
