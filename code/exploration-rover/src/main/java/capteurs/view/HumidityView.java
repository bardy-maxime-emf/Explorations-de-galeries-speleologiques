package capteurs.view;

import capteurs.model.HumidityState;

/**
 * Vue console pour limiter le spam et afficher humidité/température.
 */
public class HumidityView {

    private long lastPrintAt = 0;
    private final int periodMs;

    public HumidityView() {
        this(500);
    }

    public HumidityView(int periodMs) {
        this.periodMs = Math.max(100, periodMs);
    }

    public void renderConsole(HumidityState state) {
        if (state == null) return;

        long now = System.currentTimeMillis();
        if (now - lastPrintAt < periodMs) return;
        lastPrintAt = now;

        String hStr = Double.isNaN(state.humidityPercent()) ? "?" : String.format("%.1f", state.humidityPercent());
        String tStr = Double.isNaN(state.temperatureCelsius()) ? "?" : String.format("%.1f", state.temperatureCelsius());
        String err = (state.lastError() == null) ? "-" : state.lastError();

        System.out.printf("[HUM] attached=%s | humidity=%s%% | temp=%sC | err=%s%n",
                state.attached(), hStr, tStr, err);
    }
}
