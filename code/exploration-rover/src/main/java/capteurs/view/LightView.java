package capteurs.view;

import capteurs.model.LightState;

/**
 * Vue console pour limiter le spam et afficher la luminosité.
 */
public class LightView {

    private long lastPrintAt = 0;
    private final int periodMs;

    public LightView() {
        this(500);
    }

    public LightView(int periodMs) {
        this.periodMs = Math.max(100, periodMs);
    }

    public void renderConsole(LightState state) {
        if (state == null) return;

        long now = System.currentTimeMillis();
        if (now - lastPrintAt < periodMs) return;
        lastPrintAt = now;

        String luxStr = Double.isNaN(state.illuminanceLux()) ? "?" : String.format("%.1f", state.illuminanceLux());
        String err = (state.lastError() == null) ? "-" : state.lastError();

        System.out.printf("[LUX] attached=%s | lux=%s | err=%s%n",
                state.attached(), luxStr, err);
    }
}
